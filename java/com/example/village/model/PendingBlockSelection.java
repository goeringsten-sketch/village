package com.example.village.model;

/**
 * Represents a pending block selection for interactive building operations.
 * Used when a player needs to select a block location (e.g., for moving building signs).
 */
public final class PendingBlockSelection {

    private final java.util.UUID playerId;
    private final SelectionType type;
    private final java.util.UUID buildingId;

    public enum SelectionType {
        MOVE_BUILDING_SIGN
    }

    public PendingBlockSelection(java.util.UUID playerId, SelectionType type, java.util.UUID buildingId) {
        this.playerId = playerId;
        this.type = type;
        this.buildingId = buildingId;
    }

    public java.util.UUID getPlayerId() {
        return playerId;
    }

    public SelectionType getType() {
        return type;
    }

    public java.util.UUID getBuildingId() {
        return buildingId;
    }
}