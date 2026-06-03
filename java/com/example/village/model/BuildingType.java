package com.example.village.model;

import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.util.Vector;

import java.util.Collections;
import java.util.Map;

public final class BuildingType {

    private final String key;
    private final String displayName;
    private final String description;
    private final Material icon;
    private final int villagerCapacity;
    private final double globalCost;
    private final double localCost;
    private final int requiredLevel;
    private final String requiredUpgrade;
    private final String schematic;
    private final Map<String, Vector> locations; // Relative Koordinaten (workstation, storage, etc.)
    private final Material workstationBlock; // Arbeitsblock für Arbeitsgebäude
    private final Map<String, Material> workstationBlocks; // Mehrere Arbeitsblöcke pro Location-Key
    private final int maxLevel;
    private final double upgradeCostPerLevel;
    private final double upgradeLocalCostPerLevel;
    private final int upgradePointsPerLevel;
    private final int upgradeRequiredVillageLevel;
    private final String upgradeRequiredUnlockKey;

    public BuildingType(String key, String displayName, String description,
                        Material icon, int villagerCapacity, double globalCost, double localCost,
                        int requiredLevel, String requiredUpgrade, String schematic, Map<String, Vector> locations,
                        Material workstationBlock, int maxLevel,
                        Map<String, Material> workstationBlocks,
                        double upgradeCostPerLevel, double upgradeLocalCostPerLevel, int upgradePointsPerLevel,
                        int upgradeRequiredVillageLevel, String upgradeRequiredUnlockKey) {
        this.key = key;
        this.displayName = displayName;
        this.description = description;
        this.icon = icon;
        this.villagerCapacity = villagerCapacity;
        this.globalCost = globalCost;
        this.localCost = localCost;
        this.requiredLevel = requiredLevel;
        this.requiredUpgrade = requiredUpgrade;
        this.schematic = schematic;
        this.locations = locations;
        this.workstationBlock = workstationBlock;
        this.workstationBlocks = workstationBlocks != null ? workstationBlocks : Collections.emptyMap();
        this.maxLevel = Math.max(1, maxLevel);
        this.upgradeCostPerLevel = Math.max(0.0, upgradeCostPerLevel);
        this.upgradeLocalCostPerLevel = Math.max(0.0, upgradeLocalCostPerLevel);
        this.upgradePointsPerLevel = Math.max(0, upgradePointsPerLevel);
        this.upgradeRequiredVillageLevel = Math.max(1, upgradeRequiredVillageLevel);
        this.upgradeRequiredUnlockKey = upgradeRequiredUnlockKey;
    }

    public String getKey() { return key; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public Material getIcon() { return icon; }
    public int getVillagerCapacity() { return villagerCapacity; }
    public double getCost() { return globalCost; }
    public double getGlobalCost() { return globalCost; }
    public double getLocalCost() { return localCost; }
    public int getRequiredLevel() { return requiredLevel; }
    public String getRequiredUpgrade() { return requiredUpgrade; }
    public String getSchematic() { return schematic; }
    public Map<String, Vector> getLocations() { return locations; }
    public Material getWorkstationBlock() { return workstationBlock; }
    public Map<String, Material> getWorkstationBlocks() { return workstationBlocks; }
    public int getMaxLevel() { return maxLevel; }
    public double getUpgradeCostPerLevel() { return upgradeCostPerLevel; }
    public double getUpgradeLocalCostPerLevel() { return upgradeLocalCostPerLevel; }
    public int getUpgradePointsPerLevel() { return upgradePointsPerLevel; }
    public int getUpgradeRequiredVillageLevel() { return upgradeRequiredVillageLevel; }
    public String getUpgradeRequiredUnlockKey() { return upgradeRequiredUnlockKey; }

    public int getEffectiveVillagerCapacity(int level) {
        return villagerCapacity * Math.max(1, level);
    }

    /**
     * Gibt die absolute Position einer relativen Location zurück.
     */
    public org.bukkit.Location getAbsoluteLocation(String locationKey, org.bukkit.Location buildingOrigin) {
        Vector relative = locations.get(locationKey);
        if (relative == null) return null;
        return buildingOrigin.clone().add(relative);
    }

    public String resolveWorkstationKeyAt(Location clicked, Location buildingOrigin, Material material) {
        if (clicked == null || buildingOrigin == null || material == null) return null;
        for (Map.Entry<String, Material> e : workstationBlocks.entrySet()) {
            Location target = getAbsoluteLocation(e.getKey(), buildingOrigin);
            if (target == null) continue;
            if (target.getBlockX() == clicked.getBlockX()
                    && target.getBlockY() == clicked.getBlockY()
                    && target.getBlockZ() == clicked.getBlockZ()
                    && e.getValue() == material) {
                return e.getKey();
            }
        }
        return null;
    }
}
