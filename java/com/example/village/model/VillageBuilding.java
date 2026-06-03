package com.example.village.model;

import org.bukkit.Location;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class VillageBuilding {

    private final UUID id;
    private final String typeKey;
    private Location location;
    private boolean completed;
    private long placedAt;
    private int bedCount;
    private String direction;
    private String schematicName;
    private int level;
    private Location signLocation;
    private UUID ownerId;
    private boolean signHidden;
    private boolean accessAllMembers;
    private final Set<UUID> accessList = new HashSet<>();
    private String signTemplate;
    private String customName;
    /** Stable index per building type within the village (used for WorldGuard region id). */
    private int typeOrdinal;

    public VillageBuilding(UUID id, String typeKey, Location location) {
        this.id = id;
        this.typeKey = typeKey;
        this.location = location;
        this.completed = false;
        this.placedAt = System.currentTimeMillis();
        this.bedCount = 0;
        this.direction = "N";
        this.schematicName = null;
        this.level = 1;
        this.signLocation = null;
        this.ownerId = null;
        this.signHidden = false;
        this.accessAllMembers = true;
        this.signTemplate = null;
        this.customName = null;
        this.typeOrdinal = 0;
    }

    public UUID getId() { return id; }
    public String getTypeKey() { return typeKey; }
    public Location getLocation() { return location; }
    public void setLocation(Location location) { this.location = location; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
    public long getPlacedAt() { return placedAt; }
    public void setPlacedAt(long placedAt) { this.placedAt = placedAt; }
    public int getBedCount() { return bedCount; }
    public void setBedCount(int bedCount) { this.bedCount = bedCount; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public String getSchematicName() { return schematicName; }
    public void setSchematicName(String schematicName) { this.schematicName = schematicName != null && !schematicName.isBlank() ? schematicName.trim() : null; }
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = Math.max(1, level); }
    public Location getSignLocation() { return signLocation; }
    public void setSignLocation(Location signLocation) { this.signLocation = signLocation; }

    public UUID getOwnerId() { return ownerId; }
    public void setOwnerId(UUID ownerId) { this.ownerId = ownerId; }

    public boolean isSignHidden() { return signHidden; }
    public void setSignHidden(boolean signHidden) { this.signHidden = signHidden; }

    public boolean isAccessAllMembers() { return accessAllMembers; }
    public void setAccessAllMembers(boolean accessAllMembers) { this.accessAllMembers = accessAllMembers; }

    public Set<UUID> getAccessList() { return accessList; }
    public void setAccessList(Set<UUID> list) {
        accessList.clear();
        if (list != null) accessList.addAll(list);
    }

    public String getSignTemplate() { return signTemplate; }
    public void setSignTemplate(String signTemplate) { this.signTemplate = signTemplate; }

    public String getCustomName() { return customName; }
    public void setCustomName(String customName) {
        this.customName = customName != null && !customName.isBlank() ? customName.trim() : null;
    }

    public boolean hasCustomName() {
        return customName != null && !customName.isBlank();
    }

    public int getTypeOrdinal() { return typeOrdinal; }
    public void setTypeOrdinal(int typeOrdinal) { this.typeOrdinal = Math.max(0, typeOrdinal); }
}
