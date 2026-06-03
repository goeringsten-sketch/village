package com.example.village.gui;

import com.example.village.VillagePlugin;
import com.example.village.config.VillageConfigManager;
import com.example.village.hook.VaultHook;
import com.example.village.model.Village;
import com.example.village.service.LevelService;
import com.example.village.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * GUI für Dorf-Levelup System.
 * Zeigt aktuelle Level, Voraussetzungen und ermöglicht Levelup.
 */
public final class LevelupGui {

    private static final Map<UUID, Integer> ACTIVE_EFFECT_TASKS = new HashMap<>();

    private final Player player;
    private final Village village;
    private final LevelService levelService;
    private final VillageConfigManager configManager;
    private final VaultHook vaultHook;
    private final VillagePlugin plugin;

    public LevelupGui(VillagePlugin plugin, Player player, Village village, LevelService levelService,
                      VillageConfigManager configManager, VaultHook vaultHook) {
        this.plugin = plugin;
        this.player = player;
        this.village = village;
        this.levelService = levelService;
        this.configManager = configManager;
        this.vaultHook = vaultHook;
    }

    public void open() {
        VillageMenuHolder holder = new VillageMenuHolder(VillageMenuHolder.MenuType.LEVELUP);
        Inventory inv = Bukkit.createInventory(holder, 54, "§6§lDorf Levelup");

        // Row 1: Info
        inv.setItem(4, createBellItem());

        // Row 2: Current Level Progress
        fillProgressRow(inv);

        // Row 3: Prerequisites
        fillPrerequisitesRow(inv);

        // Row 4: Costs
        fillCostsRow(inv);

        // Row 5: Action Buttons
        boolean levelupReady = levelService.getLevelupFailureReason(village, player) == null
                && levelService.isLevelUpAvailable(village);
        if (levelupReady) {
            inv.setItem(49, new ItemBuilder(Material.LIME_CONCRETE)
                    .name("§a§lLevelup durchführen")
                    .lore("§7Klick um das Dorf hochzuleveln")
                    .build());
        } else {
            inv.setItem(49, new ItemBuilder(Material.RED_CONCRETE)
                    .name("§c§lLevelup nicht verfügbar")
                    .lore("§7Nicht alle Voraussetzungen erfüllt")
                    .build());
        }

        // Back Button
        inv.setItem(45, new ItemBuilder(Material.ARROW)
                .name("§e§lZurück")
                .lore("§7Zurück zum Dorf-Menü")
                .build());

        // Close Button
        inv.setItem(53, new ItemBuilder(Material.BARRIER)
                .name("§c§lSchließen")
                .build());

        player.openInventory(inv);
        stopPersistentEffects();
        showLevelupBellVisuals();
        startPersistentEffects();
    }

    private org.bukkit.inventory.ItemStack createBellItem() {
        int nextLevel = village.getLevel() + 1;
        int maxLevel = configManager.getMaxLevel();

        if (nextLevel > maxLevel) {
            return new ItemBuilder(Material.BELL)
                    .name("§6§lDorf Level §e" + village.getLevel() + " §6(MAX)")
                    .lore("§7Maximales Level erreicht!")
                    .build();
        }

        boolean hasLevelup = levelService.getLevelupFailureReason(village, player) == null
                && levelService.isLevelUpAvailable(village);
        Material material = Material.BELL;

        int current = village.getPoints();
        int required = configManager.getPointsForLevel(village.getLevel());
        double progress = Math.min(1.0, (double) current / required);

        ItemBuilder builder = new ItemBuilder(material)
                .name("§6§lDorf Level §e" + village.getLevel() + " §6→ §e" + nextLevel);
        if (hasLevelup) builder.glow(true);

        List<String> lore = new ArrayList<>();
        lore.add("§7" + current + " / " + required + " Punkte");

        // Progress bar
        int barLength = 20;
        int filledLength = (int) (progress * barLength);
        StringBuilder bar = new StringBuilder("§a");
        for (int i = 0; i < filledLength; i++) bar.append("█");
        bar.append("§7");
        for (int i = filledLength; i < barLength; i++) bar.append("█");
        lore.add(bar.toString());

        if (hasLevelup) {
            lore.add("");
            lore.add("§a§lLevelup verfügbar!");
        }

        builder.lore(lore.toArray(new String[0]));
        return builder.build();
    }

    private void showLevelupBellVisuals() {
        if (levelService == null || village.getBellLocation() == null || !configManager.isParticleEffectEnabled()) {
            return;
        }

        if (!shouldShowBellEffects()) {
            return;
        }

        Location bellLocation = village.getBellLocation().clone().add(0.5, 0.8, 0.5);
        if (bellLocation.getWorld() == null) {
            return;
        }

        double radius = Math.max(0.5, configManager.getParticleRadiusAroundBell());
        double height = Math.max(0.3, configManager.getParticleHeightAboveBell());
        int scale = Math.max(1, Math.min(4, 1));
        List<Particle> particles = getConfiguredParticles();
        if (particles.isEmpty()) {
            particles = List.of(Particle.ENCHANT, Particle.HAPPY_VILLAGER);
        }

        for (Particle particle : particles) {
            int count = Math.max(6, (int) (radius * 8 * scale));
            bellLocation.getWorld().spawnParticle(particle, bellLocation, count, radius, height, radius, 0.02);
        }
        bellLocation.getWorld().playSound(bellLocation, Sound.BLOCK_BELL_RESONATE, 0.5f, 1.2f);
    }

    private void startPersistentEffects() {
        stopPersistentEffects();
        if (!configManager.isParticleEffectEnabled() || !shouldShowBellEffects()) {
            return;
        }

        int intervalTicks = Math.max(1, configManager.getParticleCheckIntervalTicks());
        int taskId = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || !shouldShowBellEffects()) {
                stopPersistentEffects();
                return;
            }
            showLevelupBellVisuals();
        }, 0L, intervalTicks).getTaskId();

        ACTIVE_EFFECT_TASKS.put(player.getUniqueId(), taskId);
    }

    private void stopPersistentEffects() {
        Integer taskId = ACTIVE_EFFECT_TASKS.remove(player.getUniqueId());
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }

    private boolean shouldShowBellEffects() {
        if (levelService == null || village.getBellLocation() == null) {
            return false;
        }
        String failureReason = levelService.getLevelupFailureReason(village, player);
        return failureReason == null && levelService.isLevelUpAvailable(village);
    }

    private List<Particle> getConfiguredParticles() {
        List<Particle> result = new ArrayList<>();
        List<String> configured = configManager.getPrerequisiteParticles(village.getLevel() + 1);
        if (configured == null || configured.isEmpty()) {
            return result;
        }

        for (String entry : configured) {
            Particle particle = resolveParticle(entry);
            if (particle != null && !result.contains(particle)) {
                result.add(particle);
            }
        }
        return result;
    }

    private Particle resolveParticle(String raw) {
        if (raw == null) {
            return null;
        }

        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "flame" -> Particle.FLAME;
            case "flame_small", "small_flame" -> Particle.SMALL_FLAME;
            case "enchant" -> Particle.ENCHANT;
            case "happy_villager", "happyvillager" -> Particle.HAPPY_VILLAGER;
            case "soul" -> Particle.SOUL;
            case "soul_fire_flame", "soulfireflame" -> Particle.SOUL_FIRE_FLAME;
            default -> {
                try {
                    yield Particle.valueOf(normalized.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    yield null;
                }
            }
        };
    }

    private void fillProgressRow(Inventory inv) {
        // Row 2 (Slots 9-17)
        int current = village.getPoints();
        int required = configManager.getPointsForLevel(village.getLevel());

        inv.setItem(10, new ItemBuilder(Material.EXPERIENCE_BOTTLE)
                .name("§b§lAktuelle Punkte")
                .lore("§e" + current + " §7Dorfpunkte")
                .build());

        inv.setItem(13, new ItemBuilder(Material.TARGET)
                .name("§b§lErforderlich")
                .lore("§e" + required + " §7Punkte für Level §e" + (village.getLevel() + 1))
                .build());

        int pointsNeeded = Math.max(0, required - current);
        inv.setItem(16, new ItemBuilder(Material.LIME_STAINED_GLASS_PANE)
                .name("§b§lBis zum Levelup")
                .lore("§e" + pointsNeeded + " §7Punkte benötigt")
                .build());
    }

    private void fillPrerequisitesRow(Inventory inv) {
        // Row 3 (Slots 18-26)
        int nextLevel = village.getLevel() + 1;
        Map<String, Object> prerequisites = configManager.getLevelPrerequisites(nextLevel);

        if (prerequisites == null || prerequisites.isEmpty()) {
            inv.setItem(22, new ItemBuilder(Material.GREEN_CONCRETE)
                    .name("§a§lKeine Voraussetzungen")
                    .lore("§7Level §e" + nextLevel + " §7kann direkt freigeschaltet werden")
                    .build());
            return;
        }

        List<String> prerequisites_list = new ArrayList<>();

        // Check villages
        if (prerequisites.containsKey("buildings")) {
            @SuppressWarnings("unchecked")
            List<String> requiredBuildings = (List<String>) prerequisites.get("buildings");
            prerequisites_list.add("§6Gebäude:");
            for (String building : requiredBuildings) {
                boolean hasBuilding = village.getBuildings().stream()
                        .anyMatch(b -> b.getTypeKey().equalsIgnoreCase(building));
                String status = hasBuilding ? "§a✓" : "§c✗";
                prerequisites_list.add(status + " " + building);
            }
        }

        // Check villagers
        if (prerequisites.containsKey("min-villagers")) {
            int required = (int) prerequisites.get("min-villagers");
            int current = village.getVillagers().size();
            boolean met = current >= required;
            String status = met ? "§a✓" : "§c✗";
            prerequisites_list.add("§6Dorfbewohner: " + status + current + " / " + required);
        }

        // Check permission
        if (prerequisites.containsKey("permission")) {
            String perm = (String) prerequisites.get("permission");
            boolean hasPermission = player.hasPermission(perm);
            String status = hasPermission ? "§a✓" : "§c✗";
            prerequisites_list.add("§6Permission: " + status + perm);
        }

        // Display prerequisites
        int slot = 18;
        for (String line : prerequisites_list) {
            if (slot >= 27) break;
            inv.setItem(slot, new ItemBuilder(Material.WRITABLE_BOOK)
                    .name(line)
                    .build());
            slot++;
        }
    }

    private void fillCostsRow(Inventory inv) {
        // Row 4 (Slots 27-35)
        int nextLevel = village.getLevel() + 1;
        int required = configManager.getPointsForLevel(village.getLevel());

        inv.setItem(28, new ItemBuilder(Material.EXPERIENCE_BOTTLE)
                .name("§c§lKosten - Dorfpunkte")
                .lore("§e-" + required + " §7Punkte")
                .build());

        // Global currency cost (if configured)
        double globalCost = configManager.getLevelupCost(nextLevel, "global");
        if (globalCost > 0) {
            inv.setItem(31, new ItemBuilder(Material.GOLD_INGOT)
                    .name("§c§lKosten - Geld")
                    .lore("§e-" + vaultHook.format(globalCost))
                    .build());
        }

        // Local currency cost (if configured)
        double localCost = configManager.getLevelupCost(nextLevel, "local");
        if (localCost > 0) {
            inv.setItem(34, new ItemBuilder(Material.EMERALD)
                    .name("§c§lKosten - Lokale Währung")
                    .lore("§e-" + localCost)
                    .build());
        }
    }
}
