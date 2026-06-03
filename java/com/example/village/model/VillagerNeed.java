package com.example.village.model;

public enum VillagerNeed {
    HUNGER("Hunger", 2.0, 20.0),
    HAPPINESS("Zufriedenheit", 1.0, 30.0);

    private final String displayName;
    private final double defaultDecayPerMinute;
    private final double defaultCriticalThreshold;

    VillagerNeed(String displayName, double defaultDecayPerMinute, double defaultCriticalThreshold) {
        this.displayName = displayName;
        this.defaultDecayPerMinute = defaultDecayPerMinute;
        this.defaultCriticalThreshold = defaultCriticalThreshold;
    }

    public String getDisplayName() { return displayName; }
    public double getDefaultDecayPerMinute() { return defaultDecayPerMinute; }
    public double getDefaultCriticalThreshold() { return defaultCriticalThreshold; }
}
