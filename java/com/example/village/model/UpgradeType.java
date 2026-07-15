package com.example.village.model;

import org.bukkit.Material;

public final class UpgradeType {

    private final String key;
    private final String displayName;
    private final String description;
    private final Material icon;
    private final int maxLevel;
    private final double globalCostPerLevel;
    private final double localCostPerLevel;
    private final int pointsPerLevel;

    private final int requiredVillageLevel;

    public UpgradeType(String key, String displayName, String description,
                       Material icon, int maxLevel, double globalCostPerLevel, double localCostPerLevel, int pointsPerLevel) {
        this(key, displayName, description, icon, maxLevel, globalCostPerLevel, localCostPerLevel, pointsPerLevel, 1);
    }

    public UpgradeType(String key, String displayName, String description,
                       Material icon, int maxLevel, double globalCostPerLevel, double localCostPerLevel, int pointsPerLevel,
                       int requiredVillageLevel) {
        this.key = key;
        this.displayName = displayName;
        this.description = description;
        this.icon = icon;
        this.maxLevel = maxLevel;
        this.globalCostPerLevel = globalCostPerLevel;
        this.localCostPerLevel = localCostPerLevel;
        this.pointsPerLevel = pointsPerLevel;
        this.requiredVillageLevel = Math.max(1, requiredVillageLevel);
    }

    public String getKey() { return key; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public Material getIcon() { return icon; }
    public int getMaxLevel() { return maxLevel; }
    public double getCostPerLevel() { return globalCostPerLevel; }
    public double getGlobalCostPerLevel() { return globalCostPerLevel; }
    public double getLocalCostPerLevel() { return localCostPerLevel; }
    public int getPointsPerLevel() { return pointsPerLevel; }
    public int getRequiredVillageLevel() { return requiredVillageLevel; }
}
