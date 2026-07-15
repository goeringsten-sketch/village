package com.example.village.listener;

import com.example.village.config.VillageConfigManager;
import com.example.village.model.Village;
import com.example.village.model.VillageBorder;
import com.example.village.model.VillageBuilding;
import com.example.village.service.BuildingService;
import com.example.village.service.VillageManager;
import com.example.village.service.LevelService;
import com.example.village.VillagePlugin;
import com.example.village.util.MessageUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class VillageDiscoveryListener implements Listener {

    private final VillageManager villageManager;
    private final VillageConfigManager configManager;
    private final LevelService levelService;
    private final VillagePlugin plugin;
    private final ConcurrentHashMap<UUID, UUID> lastVillageByPlayer = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Integer> lastBorderByPlayer = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, UUID> lastBuildingByPlayer = new ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentHashMap<UUID, Integer> activeEffectTasks = new java.util.concurrent.ConcurrentHashMap<>();
    private BuildingService buildingService;

    public VillageDiscoveryListener(VillageManager villageManager, VillageConfigManager configManager, VillagePlugin plugin, LevelService levelService) {
        this.villageManager = villageManager;
        this.configManager = configManager;
        this.plugin = plugin;
        this.levelService = levelService;
    }

    /** Call this after BuildingService is fully initialized. */
    public void setBuildingService(BuildingService buildingService) {
        this.buildingService = buildingService;
    }

    private String t(String path, String fallback) {
        return configManager.text(path, fallback);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // ── Building-zone actionbar (independent of borderNameDebug) ──────────────
        if (buildingService != null) {
            Village toVillageForBuilding = villageManager.getVillageAtLocation(event.getTo()).orElse(null);
            if (toVillageForBuilding != null) {
                int bx = event.getTo().getBlockX();
                int bz = event.getTo().getBlockZ();
                VillageBuilding currentBuilding = buildingService.getBuildingAtLocation(toVillageForBuilding, bx, bz);
                UUID currentBuildingId = currentBuilding != null ? currentBuilding.getId() : null;
                UUID lastBuildingId = lastBuildingByPlayer.get(playerId);
                if (!Objects.equals(currentBuildingId, lastBuildingId)) {
                    if (currentBuildingId != null) {
                        lastBuildingByPlayer.put(playerId, currentBuildingId);
                    } else {
                        lastBuildingByPlayer.remove(playerId);
                    }
                    showBuildingActionBar(player, toVillageForBuilding, currentBuilding);
                }
            } else {
                lastBuildingByPlayer.remove(playerId);
            }
        }

        Village fromVillage = villageManager.getVillageAtLocation(event.getFrom()).orElse(null);
        Village toVillage = villageManager.getVillageAtLocation(event.getTo()).orElse(null);
        UUID currentVillageId = toVillage != null ? toVillage.getId() : null;
        UUID lastVillageId = lastVillageByPlayer.get(playerId);
        boolean borderNameDebugEnabled = configManager.isBorderEntryDebug();

        if (!borderNameDebugEnabled) {
            lastVillageByPlayer.remove(playerId);
            lastBorderByPlayer.remove(playerId);
        }

        if (borderNameDebugEnabled && Objects.equals(lastVillageId, currentVillageId)) {
            // If village stays the same, still allow border-region changes
            if (toVillage != null && Objects.equals(lastVillageId, currentVillageId)) {
                int toBorderId = getBorderId(toVillage, event.getTo().getBlockX(), event.getTo().getBlockZ());
                Integer lastBorderId = lastBorderByPlayer.get(playerId);
                if (!Objects.equals(lastBorderId, toBorderId)) {
                    lastBorderByPlayer.put(playerId, toBorderId);
                    if (configManager.isBorderEntryDebug()) {
                        showBorderActionBar(player, toVillage, toBorderId);
                    }
                }
            }
            // If still in same village, ensure effect task exists if levelup available
            if (toVillage != null && levelService != null && levelService.isLevelUpAvailable(toVillage)) {
                ensureEffectTask(player, toVillage);
            }
            return;
        }

        if (currentVillageId == null) {
            if (borderNameDebugEnabled) {
                lastVillageByPlayer.remove(playerId);
                lastBorderByPlayer.remove(playerId);
            }
            cancelEffectTask(playerId);
            return;
        }

        int toBorderId = getBorderId(toVillage, event.getTo().getBlockX(), event.getTo().getBlockZ());
        if (borderNameDebugEnabled) {
            lastVillageByPlayer.put(playerId, currentVillageId);
            lastBorderByPlayer.put(playerId, toBorderId);
        }

        if (borderNameDebugEnabled && fromVillage != null && fromVillage.getId().equals(toVillage.getId())) {
            if (configManager.isBorderEntryDebug()) {
                showBorderActionBar(player, toVillage, toBorderId);
            }
            return;
        }

        if (!villageManager.getPlayerDiscoveredVillageIds(playerId).contains(toVillage.getId())) {
            villageManager.registerVillageDiscovery(playerId, toVillage);
            MessageUtil.send(player, null, t("messages.village-discovered", "&eDu hast das Dorf &6%name% &eentdeckt!").replace("%name%", toVillage.getName()));
        }
        if (borderNameDebugEnabled) {
            showBorderActionBar(player, toVillage, toBorderId);
        }

        // Start persistent levelup effects for this player if levelup is available
        if (levelService != null && levelService.isLevelUpAvailable(toVillage)) {
            ensureEffectTask(player, toVillage);
        } else {
            cancelEffectTask(playerId);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        lastVillageByPlayer.remove(event.getPlayer().getUniqueId());
        lastBorderByPlayer.remove(event.getPlayer().getUniqueId());
        lastBuildingByPlayer.remove(event.getPlayer().getUniqueId());
        cancelEffectTask(event.getPlayer().getUniqueId());
    }

    private void ensureEffectTask(Player player, Village village) {
        if (player == null || village == null) return;
        UUID pid = player.getUniqueId();
        if (activeEffectTasks.containsKey(pid)) return;
        if (village.getBellLocation() == null) return;

        int taskId = org.bukkit.Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                cancelEffectTask(pid);
                return;
            }
            // Stop if player left the village or levelup no longer available
            java.util.Optional<Village> current = villageManager.getVillageAtLocation(player.getLocation());
            if (current.isEmpty() || !current.get().getId().equals(village.getId()) || (levelService != null && !levelService.isLevelUpAvailable(village))) {
                cancelEffectTask(pid);
                return;
            }
            try {
                org.bukkit.Location bell = village.getBellLocation().clone().add(0.5, 0.8, 0.5);
                double radius = Math.max(0.5, configManager.getParticleRadiusAroundBell());
                int scale = Math.max(1, Math.min(4, 1));
                java.util.List<org.bukkit.Particle> particles = new java.util.ArrayList<>();
                java.util.List<String> configured = configManager.getPrerequisiteParticles(village.getLevel() + 1);
                if (configured != null && !configured.isEmpty()) {
                    for (String entry : configured) {
                        try {
                            org.bukkit.Particle p = org.bukkit.Particle.valueOf(entry.trim().toUpperCase(java.util.Locale.ROOT));
                            if (p != null && !particles.contains(p)) particles.add(p);
                        } catch (Exception ignored) {}
                    }
                }
                if (particles.isEmpty()) particles = java.util.List.of(org.bukkit.Particle.ENCHANT, org.bukkit.Particle.HAPPY_VILLAGER);
                for (org.bukkit.Particle particle : particles) {
                    int count = Math.max(6, (int) (radius * 8 * scale));
                    // Show to the player only
                    player.spawnParticle(particle, bell, count, radius, Math.max(0.3, configManager.getParticleHeightAboveBell()), radius, 0.02);
                }
                bell.getWorld().playSound(bell, org.bukkit.Sound.BLOCK_BELL_RESONATE, 0.5f, 1.2f);
            } catch (Exception ignored) {}
        }, 0L, Math.max(1, configManager.getParticleCheckIntervalTicks())).getTaskId();

        activeEffectTasks.put(pid, taskId);
    }

    private void cancelEffectTask(UUID playerId) {
        Integer t = activeEffectTasks.remove(playerId);
        if (t != null) org.bukkit.Bukkit.getScheduler().cancelTask(t);
    }

    private int getBorderId(Village village, int x, int z) {
        if (village == null) return -1;
        VillageBorder border = village.getBorderAt(x, z);
        return border != null ? border.getId() : -1;
    }

    private void showBorderActionBar(Player player, Village village, int borderId) {
        if (player == null || village == null) return;
        if (!player.hasPermission("village.border.shownames")) return;
        String name = village.getName();
        String message = borderId < 0
                ? t("messages.border-enter-no-id", "&7Grenzfläche betreten: &6%name% &7(keine ID)").replace("%name%", name)
                : t("messages.border-enter-id", "&7Grenzfläche betreten: &6%name% &7(ID=%id%)")
                    .replace("%name%", name)
                    .replace("%id%", String.valueOf(borderId));
        Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(message);
        player.sendActionBar(component);
    }

    private void showBuildingActionBar(Player player, Village village, VillageBuilding building) {
        if (player == null || village == null) return;
        if (building == null) return;

        String typeKey = building.getTypeKey();
        String typeName = buildingService != null ? buildingService.getDisplayNameForType(typeKey) : typeKey;
        String bName = building.hasCustomName() ? building.getCustomName() : typeName;
        String level = String.valueOf(building.getLevel());

        String message;
        if (building.getOwnerId() != null) {
            org.bukkit.OfflinePlayer off = org.bukkit.Bukkit.getOfflinePlayer(building.getOwnerId());
            String ownerName = off.getName() != null ? off.getName() : "Unbekannt";
            message = t("messages.building-enter-with-owner", "&7Gebäude: &6%building_name% &8(%building_type%&8) &7· Besitzer: &e%owner% &7· Lv.%level%")
                    .replace("%building_name%", bName)
                    .replace("%building_type%", typeName)
                    .replace("%owner%", ownerName)
                    .replace("%level%", level);
        } else {
            message = t("messages.building-enter", "&7Gebäude: &6%building_name% &8(&7%building_type%&8) &7Lv.%level%")
                    .replace("%building_name%", bName)
                    .replace("%building_type%", typeName)
                    .replace("%level%", level);
        }

        Component component = LegacyComponentSerializer.legacyAmpersand().deserialize(message);
        player.sendActionBar(component);
    }

    public void clearBorderTracking() {
        lastVillageByPlayer.clear();
        lastBorderByPlayer.clear();
        lastBuildingByPlayer.clear();
    }
}
