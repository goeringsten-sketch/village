package com.example.village.service;

import com.example.village.VillagePlugin;
import com.example.village.config.VillageConfigManager;
import com.example.village.model.UpgradeType;
import com.example.village.model.Village;
import com.example.village.service.EconomyService;
import com.example.village.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.configuration.ConfigurationSection;

public final class UpgradeService {

    private final VillagePlugin plugin;
    private final VillageConfigManager configManager;
    private final VillageManager villageManager;
    private final EconomyService economyService;
    private final CurrencyService currencyService;
    private java.util.List<RoleUnlock> roleUnlocks;

    public UpgradeService(VillagePlugin plugin, VillageConfigManager configManager,
                          VillageManager villageManager, EconomyService economyService, CurrencyService currencyService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.villageManager = villageManager;
        this.economyService = economyService;
        this.currencyService = currencyService;
        this.roleUnlocks = loadRoleUnlocks();
    }

    public enum UpgradeResult {
        SUCCESS,
        MAX_LEVEL_REACHED,
        NOT_ENOUGH_MONEY,
        NOT_ENOUGH_POINTS,
        UNKNOWN_UPGRADE,
        NOT_IN_VILLAGE,
        NO_PERMISSION
    }

    public record RoleUnlock(String key, String displayName, org.bukkit.Material icon, double globalMoneyCost, double localMoneyCost, int pointsCost) {}
    
    /**
     * Lädt die Rollen-Upgrades aus der Konfigurationsdatei.
     */
    private java.util.List<RoleUnlock> loadRoleUnlocks() {
        java.util.List<RoleUnlock> unlocks = new java.util.ArrayList<>();
        ConfigurationSection section = configManager.getRoleUpgradesSection();
        
        // Fallback zu hardcoded defaults
        java.util.List<RoleUnlock> defaults = java.util.List.of(
                new RoleUnlock("role_hr", "HR", org.bukkit.Material.WRITABLE_BOOK, 2000.0, 0.0, 500),
                new RoleUnlock("role_baumeister", "Baumeister", org.bukkit.Material.BRICKS, 2000.0, 0.0, 500),
                new RoleUnlock("role_builder", "Builder", org.bukkit.Material.IRON_PICKAXE, 1500.0, 0.0, 350),
                new RoleUnlock("role_haendler", "Haendler", org.bukkit.Material.EMERALD, 1800.0, 0.0, 400),
                new RoleUnlock("role_trainer", "Trainer", org.bukkit.Material.EXPERIENCE_BOTTLE, 1800.0, 0.0, 400)
        );
        
        if (section == null || section.getKeys(false).isEmpty()) {
            return defaults;
        }
        
        for (String key : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(key);
            if (s == null) continue;
            String displayName = s.getString("display-name", key);
            String iconStr = s.getString("icon", "PAPER");
            org.bukkit.Material icon = org.bukkit.Material.matchMaterial(iconStr);
            if (icon == null) icon = org.bukkit.Material.PAPER;
            double globalMoneyCost = s.contains("money-cost.global") ? s.getDouble("money-cost.global", 1000.0) : s.getDouble("money-cost", 1000.0);
            double localMoneyCost = s.getDouble("money-cost.local", 0.0);
            int pointsCost = s.getInt("points-cost", 250);
            
            unlocks.add(new RoleUnlock(key, displayName, icon, globalMoneyCost, localMoneyCost, pointsCost));
        }
        
        // Wenn keine Rollen aus Config geladen wurden, nutze defaults
        if (unlocks.isEmpty()) {
            return defaults;
        }
        
        return unlocks;
    }

    public java.util.List<RoleUnlock> getRoleUnlocks() {
        return roleUnlocks;
    }

    public UpgradeResult purchaseRoleUnlock(Player player, Village village, String roleUpgradeKey) {
        RoleUnlock unlock = roleUnlocks.stream().filter(r -> r.key().equalsIgnoreCase(roleUpgradeKey)).findFirst().orElse(null);
        if (unlock == null) return UpgradeResult.UNKNOWN_UPGRADE;
        if (village.getUpgradeLevel(unlock.key()) > 0) return UpgradeResult.MAX_LEVEL_REACHED;
        if (village.getPoints() < unlock.pointsCost()) return UpgradeResult.NOT_ENOUGH_POINTS;
        if (unlock.globalMoneyCost() > 0 && economyService.isAvailable()) {
            if (!economyService.canAfford(player, unlock.globalMoneyCost())) return UpgradeResult.NOT_ENOUGH_MONEY;
            if (!economyService.withdraw(player, unlock.globalMoneyCost())) return UpgradeResult.NOT_ENOUGH_MONEY;
        }
        if (unlock.localMoneyCost() > 0) {
            String localCurrencyId = currencyService.getVillageCurrencyId(village);
            if (currencyService.getBalance(player.getUniqueId(), localCurrencyId) < unlock.localMoneyCost()) {
                return UpgradeResult.NOT_ENOUGH_MONEY;
            }
            if (!currencyService.removeBalance(player.getUniqueId(), localCurrencyId, unlock.localMoneyCost())) {
                return UpgradeResult.NOT_ENOUGH_MONEY;
            }
        }
        village.setPoints(village.getPoints() - unlock.pointsCost());
        village.setUpgradeLevel(unlock.key(), 1);
        villageManager.saveVillage(village);
        return UpgradeResult.SUCCESS;
    }

    public UpgradeResult purchaseUpgrade(Player player, Village village, String upgradeKey) {
        UpgradeType upgradeType = configManager.getUpgradeType(upgradeKey);
        if (upgradeType == null) return UpgradeResult.UNKNOWN_UPGRADE;

        int currentLevel = village.getUpgradeLevel(upgradeKey);
        if (currentLevel >= upgradeType.getMaxLevel()) return UpgradeResult.MAX_LEVEL_REACHED;

        double globalCost = upgradeType.getGlobalCostPerLevel() * (currentLevel + 1);
        double localCost = upgradeType.getLocalCostPerLevel() * (currentLevel + 1);
        int pointsCost = upgradeType.getPointsPerLevel() * (currentLevel + 1);

        if (village.getPoints() < pointsCost) return UpgradeResult.NOT_ENOUGH_POINTS;

        if (globalCost > 0 && economyService.isAvailable()) {
            if (!economyService.canAfford(player, globalCost)) return UpgradeResult.NOT_ENOUGH_MONEY;
            if (!economyService.withdraw(player, globalCost)) return UpgradeResult.NOT_ENOUGH_MONEY;
        }
        if (localCost > 0) {
            String localCurrencyId = currencyService.getVillageCurrencyId(village);
            if (currencyService.getBalance(player.getUniqueId(), localCurrencyId) < localCost) {
                return UpgradeResult.NOT_ENOUGH_MONEY;
            }
            if (!currencyService.removeBalance(player.getUniqueId(), localCurrencyId, localCost)) {
                return UpgradeResult.NOT_ENOUGH_MONEY;
            }
        }

        village.setPoints(village.getPoints() - pointsCost);
        village.setUpgradeLevel(upgradeKey, currentLevel + 1);
        villageManager.saveVillage(village);

        return UpgradeResult.SUCCESS;
    }

    public double getUpgradeCost(String upgradeKey, int currentLevel) {
        UpgradeType upgradeType = configManager.getUpgradeType(upgradeKey);
        if (upgradeType == null) return 0;
        return upgradeType.getGlobalCostPerLevel() * (currentLevel + 1);
    }

    public int getUpgradePointsCost(String upgradeKey, int currentLevel) {
        UpgradeType upgradeType = configManager.getUpgradeType(upgradeKey);
        if (upgradeType == null) return 0;
        return upgradeType.getPointsPerLevel() * (currentLevel + 1);
    }
}
