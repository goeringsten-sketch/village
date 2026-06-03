package com.example.village.service;

import com.example.village.VillagePlugin;
import com.example.village.model.*;
import org.bukkit.*;
import org.bukkit.block.Chest;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Verwaltet Truhen-Workstation-Blöcke:
 * - Slot-Kapazität (1×9 – 6×9) pro Gebäude
 * - Öffentlicher Zugriff für Dorfmitglieder
 * - Villager-Automatisierung: Items entnehmen / einlagern
 * - Persistenz in chest-data.yml
 */
public final class BuildingChestManager {

    private final VillagePlugin plugin;
    private final VillageManager villageManager;
    private final BuildingConfigLoader configLoader;
    private final File dataFile;

    private final Map<UUID, Integer>                  chestSlots     = new LinkedHashMap<>();
    private final Map<UUID, Location>                 chestLocations = new LinkedHashMap<>();
    private final Map<UUID, Map<Material, Integer>>   chestCache     = new LinkedHashMap<>();

    public BuildingChestManager(VillagePlugin plugin, VillageManager villageManager,
                                BuildingConfigLoader configLoader) {
        this.plugin        = plugin;
        this.villageManager = villageManager;
        this.configLoader  = configLoader;
        this.dataFile      = new File(plugin.getDataFolder(), "chest-data.yml");
        load();
    }

    // ── Registrierung ─────────────────────────────────────────────

    public void registerChest(VillageBuilding building, Location loc, BuildingDefinition def) {
        ChestConfig cfg = def.getChestConfig();
        chestSlots.put(building.getId(), cfg != null ? cfg.getInitialSlots() : 9);
        chestLocations.put(building.getId(), loc.clone());
        chestCache.putIfAbsent(building.getId(), new LinkedHashMap<>());
        save();
    }

    public void unregisterChest(UUID buildingId) {
        chestSlots.remove(buildingId); chestLocations.remove(buildingId); chestCache.remove(buildingId); save();
    }

    // ── Slots & Upgrades ──────────────────────────────────────────

    public int getSlots(UUID bid) { return chestSlots.getOrDefault(bid, 9); }

    /** Upgrade um eine Zeile (9 Slots). Gibt true zurück wenn erfolgreich. */
    public boolean upgradeSlots(UUID buildingId, BuildingDefinition def, Player player) {
        int cur  = getSlots(buildingId);
        int maxS = def.getChestConfig() != null ? def.getChestConfig().getMaxUpgradeableSlots() : 54;
        if (cur >= maxS) { player.sendMessage("§cTruhe bereits auf Maximalkapazität."); return false; }
        int nw = Math.min(cur + 9, maxS);
        chestSlots.put(buildingId, nw);
        save();
        player.sendMessage("§a✓ Truhe auf §e" + ChestConfig.slotsToRows(nw) + "×9 §aupgegraded (" + nw + " Slots).");
        return true;
    }

    // ── Spieler-Zugriff ───────────────────────────────────────────

    public boolean openForPlayer(Player player, VillageBuilding building, BuildingDefinition def, Village village) {
        ChestConfig cfg = def.getChestConfig();
        if (cfg == null) { player.sendMessage("§cKein Truhen-WB konfiguriert."); return false; }
        if (!canAccess(player, building, cfg, village)) { player.sendMessage("§cKein Zugriff."); return false; }

        // Echte Truhe falls vorhanden
        Location loc = chestLocations.get(building.getId());
        if (loc != null && loc.getBlock().getState() instanceof Chest c) {
            player.openInventory(c.getInventory()); return true;
        }
        // Virtuelles Inventar
        int rows = ChestConfig.slotsToRows(getSlots(building.getId()));
        Inventory inv = Bukkit.createInventory(null, rows * 9,
            net.kyori.adventure.text.Component.text("§6" + def.getName() + " – Lager"));
        fillInventory(inv, chestCache.getOrDefault(building.getId(), Collections.emptyMap()));
        player.openInventory(inv);
        return true;
    }

    private boolean canAccess(Player player, VillageBuilding b, ChestConfig cfg, Village v) {
        if (player.hasPermission("village.admin")) return true;
        return switch (cfg.getAccess()) {
            case PUBLIC       -> v.isMember(player.getUniqueId());
            case PRIVATE      -> b.getOwnerId() != null && b.getOwnerId().equals(player.getUniqueId());
            case VILLAGER_ONLY -> false;
        };
    }

    private void fillInventory(Inventory inv, Map<Material, Integer> items) {
        int slot = 0;
        for (Map.Entry<Material, Integer> e : items.entrySet()) {
            int rem = e.getValue();
            while (rem > 0 && slot < inv.getSize()) {
                int st = Math.min(rem, e.getKey().getMaxStackSize());
                inv.setItem(slot++, new ItemStack(e.getKey(), st));
                rem -= st;
            }
        }
    }

    /** Synct Cache nach Schließen eines Truhen-Inventars. */
    public void syncFromInventory(UUID buildingId, Inventory inv) {
        Map<Material, Integer> cache = chestCache.computeIfAbsent(buildingId, k -> new LinkedHashMap<>());
        cache.clear();
        for (ItemStack item : inv.getContents()) {
            if (item == null || item.getType() == Material.AIR) continue;
            cache.merge(item.getType(), item.getAmount(), Integer::sum);
        }
        save();
    }

    // ── Villager-Automatisierung ──────────────────────────────────

    /** Gibt kombinierten Item-Pool aller öffentlichen Truhen eines Dorfes zurück. */
    public Map<Material, Integer> getPublicPool(Village village) {
        Map<Material, Integer> pool = new LinkedHashMap<>();
        for (VillageBuilding b : village.getBuildings()) {
            BuildingDefinition def = configLoader.getDefinition(b.getTypeKey());
            if (def == null || def.getChestConfig() == null) continue;
            if (!def.getChestConfig().isPublic() || !def.getChestConfig().isVillagerAccessible()) continue;
            chestCache.getOrDefault(b.getId(), Collections.emptyMap())
                      .forEach((mat, amt) -> pool.merge(mat, amt, Integer::sum));
        }
        return pool;
    }

    /** Entnimmt Items anteilig aus öffentlichen Truhen. false wenn nicht genug vorhanden. */
    public boolean consumeFromPublic(Village village, Map<Material, Integer> required) {
        Map<Material, Integer> stillNeeded = new LinkedHashMap<>(required);
        Map<UUID, Map<Material, Integer>> toDeduct = new LinkedHashMap<>();

        for (VillageBuilding b : village.getBuildings()) {
            if (stillNeeded.isEmpty()) break;
            BuildingDefinition def = configLoader.getDefinition(b.getTypeKey());
            if (def == null || def.getChestConfig() == null
                || !def.getChestConfig().isPublic() || !def.getChestConfig().isVillagerAccessible()) continue;

            Map<Material, Integer> cache = chestCache.getOrDefault(b.getId(), Collections.emptyMap());
            Map<Material, Integer> contrib = new LinkedHashMap<>();
            for (Map.Entry<Material, Integer> e : new LinkedHashMap<>(stillNeeded).entrySet()) {
                int avail = cache.getOrDefault(e.getKey(), 0);
                if (avail <= 0) continue;
                int take = Math.min(avail, e.getValue());
                contrib.put(e.getKey(), take);
                int rem = e.getValue() - take;
                if (rem <= 0) stillNeeded.remove(e.getKey()); else stillNeeded.put(e.getKey(), rem);
            }
            if (!contrib.isEmpty()) toDeduct.put(b.getId(), contrib);
        }
        if (!stillNeeded.isEmpty()) return false;

        toDeduct.forEach((bid, contrib) -> {
            Map<Material, Integer> cache = chestCache.get(bid);
            if (cache == null) return;
            contrib.forEach((mat, amt) -> {
                int rem = cache.getOrDefault(mat, 0) - amt;
                if (rem <= 0) cache.remove(mat); else cache.put(mat, rem);
            });
        });
        save();
        return true;
    }

    /** Lagert Items in eine Gebäude-Truhe ein. false wenn kein Platz. */
    public boolean deposit(UUID buildingId, Map<Material, Integer> items) {
        Map<Material, Integer> cache = chestCache.computeIfAbsent(buildingId, k -> new LinkedHashMap<>());
        int usedSlots = cache.values().stream().mapToInt(v -> (int) Math.ceil(v / 64.0)).sum();
        int needed    = items.values().stream().mapToInt(v -> (int) Math.ceil(v / 64.0)).sum();
        if (usedSlots + needed > getSlots(buildingId)) return false;
        items.forEach((mat, amt) -> cache.merge(mat, amt, Integer::sum));
        save();
        return true;
    }

    public Map<Material, Integer> getCache(UUID buildingId) {
        return Collections.unmodifiableMap(chestCache.getOrDefault(buildingId, Collections.emptyMap()));
    }

    // ── Persistenz ────────────────────────────────────────────────

    private void load() {
        if (!dataFile.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(dataFile);
        for (String uid : cfg.getKeys(false)) {
            try {
                UUID id = UUID.fromString(uid);
                chestSlots.put(id, cfg.getInt(uid + ".slots", 9));
                String wn = cfg.getString(uid + ".world");
                if (wn != null && Bukkit.getWorld(wn) != null)
                    chestLocations.put(id, new Location(Bukkit.getWorld(wn),
                        cfg.getInt(uid + ".x"), cfg.getInt(uid + ".y"), cfg.getInt(uid + ".z")));
                var itemSec = cfg.getConfigurationSection(uid + ".items");
                Map<Material, Integer> items = new LinkedHashMap<>();
                if (itemSec != null) itemSec.getKeys(false).forEach(k -> {
                    Material m = Material.matchMaterial(k);
                    if (m != null) items.put(m, itemSec.getInt(k));
                });
                chestCache.put(id, items);
            } catch (Exception ignored) {}
        }
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        chestSlots.forEach((id, slots) -> {
            String base = id.toString();
            cfg.set(base + ".slots", slots);
            Location loc = chestLocations.get(id);
            if (loc != null && loc.getWorld() != null) {
                cfg.set(base + ".world", loc.getWorld().getName());
                cfg.set(base + ".x", loc.getBlockX());
                cfg.set(base + ".y", loc.getBlockY());
                cfg.set(base + ".z", loc.getBlockZ());
            }
            Map<Material, Integer> items = chestCache.get(id);
            if (items != null) items.forEach((m, a) -> cfg.set(base + ".items." + m.name(), a));
        });
        try { cfg.save(dataFile); }
        catch (IOException e) { plugin.getLogger().warning("[ChestManager] Speicherfehler: " + e.getMessage()); }
    }
}
