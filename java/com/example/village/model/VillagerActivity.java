package com.example.village.model;

import org.bukkit.Location;

/**
 * Repräsentiert eine Aktivität in der Bewegungsequenz eines Villagers.
 */
public final class VillagerActivity {

    public enum ActivityType {
        SLEEP,           // Zum Bett gehen
        PREPARE,         // Vorbereitung an bestimmten Ort
        WORK,            // Arbeiten am Arbeitsplatz
        GATHER,          // Materialien sammeln
        TRADE,           // Handeln mit anderen
        IDLE,            // Freizeit
        TRAVEL_TO,       // Zu bestimmten Ort reisen
        GET_FOOD,        // Nahrung beschaffen
        EAT_FOOD         // Nahrung verzehren
    }

    private final ActivityType type;
    private final String locationKey; // Schlüssel für Gebäudelocation (z.B. "workstation", "storage")
    private final int durationTicks;  // Dauer in Ticks
    private final boolean interruptable; // Kann unterbrochen werden
    private final String description; // Beschreibung für Debugging
    private final int delayTicks;
    private final java.util.List<String> actions;
    private final int requiredVillageLevel;
    private final String requiredUpgrade;

    public VillagerActivity(ActivityType type, String locationKey, int durationTicks,
                           boolean interruptable, String description) {
        this(type, locationKey, durationTicks, interruptable, description, 0,
                java.util.List.of(), 0, null);
    }

    public VillagerActivity(ActivityType type, String locationKey, int durationTicks,
                           boolean interruptable, String description, int delayTicks,
                           java.util.List<String> actions, int requiredVillageLevel,
                           String requiredUpgrade) {
        this.type = type;
        this.locationKey = locationKey;
        this.durationTicks = durationTicks;
        this.interruptable = interruptable;
        this.description = description;
        this.delayTicks = delayTicks;
        this.actions = actions == null ? java.util.List.of() : java.util.List.copyOf(actions);
        this.requiredVillageLevel = requiredVillageLevel;
        this.requiredUpgrade = requiredUpgrade;
    }

    public ActivityType getType() { return type; }
    public String getLocationKey() { return locationKey; }
    public int getDurationTicks() { return durationTicks; }
    public boolean isInterruptable() { return interruptable; }
    public String getDescription() { return description; }
    public int getDelayTicks() { return delayTicks; }
    public java.util.List<String> getActions() { return actions; }
    public int getRequiredVillageLevel() { return requiredVillageLevel; }
    public String getRequiredUpgrade() { return requiredUpgrade; }
}