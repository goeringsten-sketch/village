package com.example.village.service;

import com.example.village.VillagePlugin;
import org.bukkit.Location;
import com.example.village.config.VillageConfigManager;
import com.example.village.hook.VaultHook;
import com.example.village.model.Village;
import com.example.village.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public final class LevelService {

    private final VillagePlugin plugin;
    private final VillageConfigManager configManager;
    private final VillageManager villageManager;
    private final VaultHook vaultHook;
    private final CurrencyService currencyService;

    public LevelService(VillagePlugin plugin, VillageConfigManager configManager,
                        VillageManager villageManager, VaultHook vaultHook,
                        CurrencyService currencyService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.villageManager = villageManager;
        this.vaultHook = vaultHook;
        this.currencyService = currencyService;
    }

    // Track villages we've already notified about an available levelup to avoid spamming
    private final java.util.Set<java.util.UUID> levelupNotified = new java.util.HashSet<>();

    public void addPoints(Village village, int points, String source) {
        village.addPoints(points);
        checkLevelUp(village);
        villageManager.saveVillage(village);
    }

    public void addPointsFromSource(Village village, String sourceKey) {
        int points = configManager.getPointsForSource(sourceKey);
        if (points > 0) {
            addPoints(village, points, sourceKey);
        }
    }

    private void checkLevelUp(Village village) {
        // Levelup ist jetzt manuell via Glockenklick
        // Diese Methode notifiziert nur, wenn Levelup verfügbar ist
        int maxLevel = configManager.getMaxLevel();
        if (village.getLevel() >= maxLevel) return;

        int required = configManager.getPointsForLevel(village.getLevel());
        if (village.getPoints() >= required) {
            // Levelup ist verfügbar - wird später manuell ausgelöst
            // Sende einmalige Effekte und Benachrichtigung an Dorfbewohner, vermeide Spam
            java.util.UUID vid = village.getId();
            if (!levelupNotified.contains(vid)) {
                levelupNotified.add(vid);
                notifyVillageMembers(village, configManager.message("levelup-available")
                        .replace("%name%", village.getName())
                        .replace("%level%", String.valueOf(village.getLevel() + 1)));
                // Spawn particles/sound at bell location for nearby players
                if (village.getBellLocation() != null && village.getBellLocation().getWorld() != null) {
                    Location bell = village.getBellLocation().clone().add(0.5, 0.8, 0.5);
                    try {
                        double radius = Math.max(0.5, configManager.getParticleRadiusAroundBell());
                        int scale = Math.max(1, Math.min(4, 1));
                        java.util.List<org.bukkit.Particle> particles = new java.util.ArrayList<>();
                        java.util.List<String> configured = configManager.getPrerequisiteParticles(village.getLevel() + 1);
                        if (configured != null && !configured.isEmpty()) {
                            for (String entry : configured) {
                                org.bukkit.Particle p;
                                try {
                                    p = org.bukkit.Particle.valueOf(entry.trim().toUpperCase(java.util.Locale.ROOT));
                                } catch (IllegalArgumentException ex) {
                                    p = null;
                                }
                                if (p != null && !particles.contains(p)) particles.add(p);
                            }
                        }
                        if (particles.isEmpty()) particles = java.util.List.of(org.bukkit.Particle.ENCHANT, org.bukkit.Particle.HAPPY_VILLAGER);

                        for (org.bukkit.Particle particle : particles) {
                            int count = Math.max(6, (int) (radius * 8 * scale));
                            bell.getWorld().spawnParticle(particle, bell, count, radius, 0.5, radius, 0.02);
                        }
                        bell.getWorld().playSound(bell, org.bukkit.Sound.BLOCK_BELL_RESONATE, 0.6f, 1.0f);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    private void notifyVillageMembers(Village village, String message) {
        String prefix = configManager.getPrefix();
        for (UUID memberId : village.getMembers().keySet()) {
            Player player = Bukkit.getPlayer(memberId);
            if (player != null && player.isOnline()) {
                MessageUtil.send(player, prefix, message);
            }
        }
    }

    public int getPointsToNextLevel(Village village) {
        int required = configManager.getPointsForLevel(village.getLevel());
        return Math.max(0, required - village.getPoints());
    }

    public double getLevelProgress(Village village) {
        int required = configManager.getPointsForLevel(village.getLevel());
        if (required <= 0) return 1.0;
        return Math.min(1.0, (double) village.getPoints() / required);
    }

    public boolean isLevelUpAvailable(Village village) {
        if (village.getLevel() >= configManager.getMaxLevel()) return false;

        int required = configManager.getPointsForLevel(village.getLevel());
        if (village.getPoints() < required) return false;

        return hasRequiredLevelupPrerequisites(village);
    }

    public String getLevelupFailureReason(Village village, Player player) {
        if (village.getLevel() >= configManager.getMaxLevel()) {
            return "Maximales Level erreicht!";
        }

        int required = configManager.getPointsForLevel(village.getLevel());
        if (village.getPoints() < required) {
            return "Es fehlen noch §e" + (required - village.getPoints()) + " §cDorfpunkte.";
        }

        if (!hasRequiredLevelupPrerequisites(village)) {
            return "Nicht alle Voraussetzungen erfüllt.";
        }

        int nextLevel = village.getLevel() + 1;
        String permission = configManager.getPrerequisitePermission(nextLevel);
        if (player != null && permission != null && !player.hasPermission(permission)) {
            return "Fehlende Berechtigung: §e" + permission;
        }

        double globalCost = configManager.getLevelupCost(nextLevel, "global");
        if (globalCost > 0 && player != null && !vaultHook.has(player, globalCost)) {
            return "Nicht genug Geld vorhanden.";
        }

        double localCost = configManager.getLevelupCost(nextLevel, "local");
        if (localCost > 0 && player != null) {
            String localCurrencyId = currencyService.getVillageCurrencyId(village);
            if (currencyService.getBalance(player.getUniqueId(), localCurrencyId) < localCost) {
                return "Nicht genug lokale Währung vorhanden.";
            }
        }

        return null;
    }

    public boolean performLevelUp(Village village, Player executor) {
        if (!isLevelUpAvailable(village)) {
            return false;
        }

        String failureReason = getLevelupFailureReason(village, executor);
        if (failureReason != null) {
            return false;
        }

        int required = configManager.getPointsForLevel(village.getLevel());
        int nextLevel = village.getLevel() + 1;

        double globalCost = configManager.getLevelupCost(nextLevel, "global");
        double localCost = configManager.getLevelupCost(nextLevel, "local");

        if (globalCost > 0 && !vaultHook.withdraw(executor, globalCost)) {
            return false;
        }

        if (localCost > 0) {
            String localCurrencyId = currencyService.getVillageCurrencyId(village);
            if (!currencyService.removeBalance(executor.getUniqueId(), localCurrencyId, localCost)) {
                if (globalCost > 0) {
                    vaultHook.deposit(executor, globalCost);
                }
                return false;
            }
        }

        village.setPoints(village.getPoints() - required);
        village.setLevel(nextLevel);

        // Highlight newly unlocked buildings/upgrades for all online members
        com.example.village.gui.GuiManager gm = plugin.getGuiManager();
        if (gm != null) gm.markNewlyUnlockedForVillage(village, nextLevel);

        notifyVillageMembers(village,
                configManager.message("level-up")
                        .replace("%name%", village.getName())
                        .replace("%level%", String.valueOf(village.getLevel())));

        // Clear the "available" notification flag so future levelups (if any) re-notify
        levelupNotified.remove(village.getId());

        villageManager.saveVillage(village);
        return true;
    }

    private boolean hasRequiredLevelupPrerequisites(Village village) {
        int nextLevel = village.getLevel() + 1;

        List<String> requiredBuildings = configManager.getPrerequisiteBuildings(nextLevel);
        if (!requiredBuildings.isEmpty()) {
            for (String requiredBuilding : requiredBuildings) {
                boolean hasBuilding = village.getBuildings().stream()
                        .anyMatch(building -> building.getTypeKey().equalsIgnoreCase(requiredBuilding));
                if (!hasBuilding) {
                    return false;
                }
            }
        }

        int requiredVillagers = configManager.getPrerequisiteMinVillagers(nextLevel);
        return village.getVillagers().size() >= requiredVillagers;
    }
}
