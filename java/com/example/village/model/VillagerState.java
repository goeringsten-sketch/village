package com.example.village.model;

/**
 * Zustände des Villager State Machine.
 */
public enum VillagerState {
    IDLE("Untätig"),
    MOVING("Läuft"),
    WORKING("Arbeitet"),
    SLEEPING("Schläft"),
    EATING("Isst"),
    TRADING("Handelt"),
    GOSSIPING("Tratscht"),
    FLEEING("Flieht"),
    HEALING("Heilt"),
    INTERACTING("Interagiert");

    private final String displayName;

    VillagerState(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
