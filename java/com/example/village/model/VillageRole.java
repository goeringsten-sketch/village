package com.example.village.model;

public enum VillageRole {
    FOUNDER("Gruender"),
    HR("HR"),
    BAUMEISTER("Baumeister"),
    BUILDER("Builder"),
    HAENDLER("Haendler"),
    TRAINER("Trainer"),
    MEMBER("Mitglied");

    private final String displayName;

    VillageRole(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean canManageVillage() {
        return this == FOUNDER || this == HR;
    }

    /** Spielerverwaltung (Beitrittsanfragen etc.). */
    public boolean canManageMembers() {
        return this == FOUNDER || this == HR;
    }

    public boolean canAssignRoles() {
        return this == FOUNDER || this == HR;
    }

    public boolean canManageBuildings() {
        return this == FOUNDER || this == BAUMEISTER;
    }

    public boolean canBuildOnSites() {
        return this == FOUNDER || this == BAUMEISTER || this == BUILDER;
    }

    public boolean canTradeWithVillagers() {
        return this == FOUNDER || this == HAENDLER;
    }

    public boolean canUpgradeVillagers() {
        return this == FOUNDER || this == TRAINER;
    }

    public boolean canDeleteVillage() {
        return this == FOUNDER;
    }

    public String upgradeKey() {
        return switch (this) {
            case HR -> "role_hr";
            case BAUMEISTER -> "role_baumeister";
            case BUILDER -> "role_builder";
            case HAENDLER -> "role_haendler";
            case TRAINER -> "role_trainer";
            default -> null;
        };
    }
}
