package com.example.village.model;

public enum VillageRelationType {
    FRIENDSHIP("Freundschaft"),
    TRADE("Handel"),
    WAR("Krieg"),
    CURFEW("Durchgangssperre");

    private final String displayName;

    VillageRelationType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static VillageRelationType fromString(String value) {
        if (value == null) {
            return null;
        }
        try {
            return VillageRelationType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            for (VillageRelationType type : values()) {
                if (type.name().equalsIgnoreCase(value) || type.displayName.equalsIgnoreCase(value)) {
                    return type;
                }
            }
            return null;
        }
    }
}
