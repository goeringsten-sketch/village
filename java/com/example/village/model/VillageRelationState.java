package com.example.village.model;

public enum VillageRelationState {
    NONE("Keine Beziehung"),
    REQUESTED("Anfrage gestellt"),
    ACTIVE("Aktiv"),
    PENDING_PEACE("Friedensanfrage");

    private final String displayName;

    VillageRelationState(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
