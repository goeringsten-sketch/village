package com.example.village.model;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

public final class VillagerProfession {

    private final String key;
    private final String displayName;
    private final Material icon;
    private final List<Material> produces;
    private final int productionIntervalSeconds;
    private final int requiredLevel;
    private final String skinName; // Citizens NPC Skin
    private final List<VillagerActivity> activitySequence; // Bewegungsequenzen

    public VillagerProfession(String key, String displayName, Material icon,
                              List<Material> produces, int productionIntervalSeconds,
                              int requiredLevel, String skinName, List<VillagerActivity> activitySequence) {
        this.key = key;
        this.displayName = displayName;
        this.icon = icon;
        this.produces = produces;
        this.productionIntervalSeconds = productionIntervalSeconds;
        this.requiredLevel = requiredLevel;
        this.skinName = skinName;
        this.activitySequence = activitySequence;
    }

    public String getKey() { return key; }
    public String getDisplayName() { return displayName; }
    public Material getIcon() { return icon; }
    public List<Material> getProduces() { return produces; }
    public int getProductionIntervalSeconds() { return productionIntervalSeconds; }
    public int getRequiredLevel() { return requiredLevel; }
    public String getSkinName() { return skinName; }
    public List<VillagerActivity> getActivitySequence() { return activitySequence; }
}
