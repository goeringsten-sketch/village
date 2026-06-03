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
        TRAVEL_TO       // Zu bestimmten Ort reisen
    }

    private final ActivityType type;
    private final String locationKey; // Schlüssel für Gebäudelocation (z.B. "workstation", "storage")
    private final int durationTicks;  // Dauer in Ticks
    private final boolean interruptable; // Kann unterbrochen werden
    private final String description; // Beschreibung für Debugging

    public VillagerActivity(ActivityType type, String locationKey, int durationTicks,
                           boolean interruptable, String description) {
        this.type = type;
        this.locationKey = locationKey;
        this.durationTicks = durationTicks;
        this.interruptable = interruptable;
        this.description = description;
    }

    public ActivityType getType() { return type; }
    public String getLocationKey() { return locationKey; }
    public int getDurationTicks() { return durationTicks; }
    public boolean isInterruptable() { return interruptable; }
    public String getDescription() { return description; }
}