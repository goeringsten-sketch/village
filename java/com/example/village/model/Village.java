package com.example.village.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Village {

    private final UUID id;
    private String name;
    private UUID founderId;
    private String worldName;
    private Location bellLocation;
    private final List<VillageBorder> borders;
    private int level;
    private int points;
    private final Map<UUID, VillageMember> members;
    private final Map<UUID, VillageJoinRequest> joinRequests;
    private final Map<UUID, VillageRelation> relations;
    private final List<VillageBuilding> buildings;
    private final List<CustomVillager> villagers;
    private final List<VillagerContract> contracts;
    private final Map<String, Integer> upgrades;
    private final java.util.Set<UUID> knownVillageIds;
    private long foundedAt;
    private int maxBuildRadius;
    private CustomVillager lastDeadVillager;
    private int reviveUses;
    private long lastReviveAt;

    public Village(UUID id, String name, UUID founderId, Location bellLocation) {
        this.id = id;
        this.name = name;
        this.founderId = founderId;
        this.bellLocation = bellLocation;
        this.worldName = bellLocation.getWorld() != null ? bellLocation.getWorld().getName() : "world";
        this.borders = new ArrayList<>();
        VillageBorder initial = new VillageBorder();
        initial.setId(0);
        this.borders.add(initial);
        this.level = 1;
        this.points = 0;
        this.members = new HashMap<>();
        this.joinRequests = new HashMap<>();
        this.relations = new HashMap<>();
        this.buildings = new ArrayList<>();
        this.villagers = new ArrayList<>();
        this.contracts = new ArrayList<>();
        this.upgrades = new HashMap<>();
        this.knownVillageIds = new java.util.HashSet<>();
        this.maxBuildRadius = 50; // Default wird aus Config überschrieben
        this.foundedAt = System.currentTimeMillis();
        this.reviveUses = 0;
        this.lastReviveAt = 0L;

        members.put(founderId, new VillageMember(founderId, VillageRole.FOUNDER));
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public UUID getFounderId() { return founderId; }
    public void setFounderId(UUID founderId) { this.founderId = founderId; }
    public String getWorldName() { return worldName; }
    public void setWorldName(String worldName) { this.worldName = worldName; }

    public World getWorld() {
        return Bukkit.getWorld(worldName);
    }

    public Location getBellLocation() { return bellLocation; }
    public Location getLocation() { return bellLocation; }  // Alias für Kompatibilität
    public void setBellLocation(Location bellLocation) {
        this.bellLocation = bellLocation;
        if (bellLocation.getWorld() != null) {
            this.worldName = bellLocation.getWorld().getName();
        }
    }

    /**
     * Returns the initial (first) border of the village.
     * For territory checks, use {@link #containsLocation(int, int)} which checks ALL borders.
     */
    public VillageBorder getBorder() { return borders.get(0); }

    /**
     * Replaces ALL borders with a single border (used for initial setup or full reset).
     */
    public void setBorder(VillageBorder border) {
        this.borders.clear();
        if (border.getId() < 0) {
            border.setId(0);
        }
        this.borders.add(border);
    }

    /**
     * Returns all borders (initial + expansions).
     */
    public List<VillageBorder> getBorders() { return borders; }

    /**
     * Adds an expansion border to the village territory.
     */
    public void addBorder(VillageBorder border) { this.borders.add(border); }

    public int nextBorderId() {
        int id = 1;
        while (true) {
            boolean used = false;
            for (VillageBorder border : borders) {
                if (border.getId() == id) {
                    used = true;
                    break;
                }
            }
            if (!used) {
                return id;
            }
            id++;
        }
    }

    public void ensureBorderIds() {
        if (borders.isEmpty()) {
            return;
        }

        borders.get(0).setId(0);
        java.util.Set<Integer> used = new java.util.HashSet<>();
        used.add(0);
        int nextCandidate = 1;

        for (int i = 1; i < borders.size(); i++) {
            VillageBorder border = borders.get(i);
            int currentId = border.getId();

            if (currentId <= 0 || used.contains(currentId)) {
                while (used.contains(nextCandidate)) {
                    nextCandidate++;
                }
                border.setId(nextCandidate);
                used.add(nextCandidate);
                nextCandidate++;
            } else {
                used.add(currentId);
            }
        }
    }

    public VillageBorder getBorderById(int id) {
        for (VillageBorder b : borders) {
            if (b.getId() == id) return b;
        }
        return null;
    }

    public VillageBorder getBorderAt(int x, int z) {
        for (VillageBorder b : borders) {
            if (b.contains(x, z) || b.isOnBorder(x, z)) return b;
        }
        return null;
    }

    /**
     * Checks whether a point (x, z) is inside ANY of the village borders.
     */
    public boolean containsLocation(int x, int z) {
        for (VillageBorder b : borders) {
            if (b.contains(x, z) || b.isOnBorder(x, z)) return true;
        }
        return false;
    }

    public double getDistanceToBorder(int x, int z) {
        double best = Double.POSITIVE_INFINITY;
        for (VillageBorder b : borders) {
            if (b.contains(x, z) || b.isOnBorder(x, z)) {
                return 0.0;
            }
            best = Math.min(best, b.distanceToBoundary(x, z));
        }
        return best == Double.POSITIVE_INFINITY ? Double.MAX_VALUE : best;
    }

    public double getBellRadius() {
        if (bellLocation == null || borders.isEmpty()) {
            return 0.0;
        }
        double centerX = bellLocation.getX();
        double centerZ = bellLocation.getZ();
        double maxDistance = 0.0;
        for (VillageBorder border : borders) {
            for (int[] point : border.getBorderPoints()) {
                double dx = point[0] - centerX;
                double dz = point[1] - centerZ;
                maxDistance = Math.max(maxDistance, Math.sqrt(dx * dx + dz * dz));
            }
        }
        return maxDistance;
    }

    /**
     * Checks whether a Location is inside ANY of the village borders (including Y check).
     */
    public boolean containsLocation(org.bukkit.Location location) {
        for (VillageBorder b : borders) {
            if (b.contains(location)) return true;
        }
        return false;
    }

    /**
     * Checks whether a point (x, z) is on the edge of ANY border.
     */
    public boolean isOnAnyBorder(int x, int z) {
        for (VillageBorder b : borders) {
            if (b.isOnBorder(x, z)) return true;
        }
        return false;
    }

    /**
     * Returns the total area of all borders combined.
     */
    public int getTotalArea() {
        int total = 0;
        for (VillageBorder b : borders) {
            total += b.calculateArea();
        }
        return total;
    }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public int getPoints() { return points; }
    public void setPoints(int points) { this.points = points; }
    public void addPoints(int amount) { this.points += amount; }

    public Map<UUID, VillageMember> getMembers() { return members; }
    public Map<UUID, VillageJoinRequest> getJoinRequests() { return joinRequests; }
    public Map<UUID, VillageRelation> getRelations() { return relations; }

    public void addMember(UUID playerId, VillageRole role) {
        members.put(playerId, new VillageMember(playerId, role));
        joinRequests.remove(playerId);
    }

    public void removeMember(UUID playerId) {
        members.remove(playerId);
    }

    public boolean isMember(UUID playerId) {
        return members.containsKey(playerId);
    }

    public VillageMember getMember(UUID playerId) {
        return members.get(playerId);
    }

    public boolean isFounder(UUID playerId) {
        return founderId.equals(playerId);
    }

    public boolean hasUpgrade(String key) {
        return getUpgradeLevel(key) > 0;
    }

    public boolean hasJoinRequest(UUID playerId) {
        return joinRequests.containsKey(playerId);
    }

    public void addJoinRequest(UUID playerId) {
        joinRequests.put(playerId, new VillageJoinRequest(playerId));
    }

    public void removeJoinRequest(UUID playerId) {
        joinRequests.remove(playerId);
    }

    public VillageRelation getRelation(UUID villageId) {
        return relations.get(villageId);
    }

    public void addRelation(VillageRelation relation) {
        if (relation != null) {
            relations.put(relation.getOtherVillageId(), relation);
        }
    }

    public void removeRelation(UUID villageId) {
        relations.remove(villageId);
    }

    public List<VillageBuilding> getBuildings() { return buildings; }

    public void addBuilding(VillageBuilding building) {
        buildings.add(building);
    }

    public void removeBuilding(UUID buildingId) {
        buildings.removeIf(b -> b.getId().equals(buildingId));
    }

    public List<CustomVillager> getVillagers() { return villagers; }

    public void addVillager(CustomVillager villager) {
        villagers.add(villager);
    }

    public void removeVillager(UUID villagerId) {
        villagers.removeIf(v -> v.getId().equals(villagerId));
    }

    public List<VillagerContract> getContracts() {
        return contracts;
    }

    public void addContract(VillagerContract contract) {
        if (contract != null) {
            contracts.add(contract);
        }
    }

    public void removeContract(UUID contractId) {
        contracts.removeIf(c -> c.getId().equals(contractId));
    }

    public VillagerContract getContract(UUID contractId) {
        for (VillagerContract contract : contracts) {
            if (contract.getId().equals(contractId)) {
                return contract;
            }
        }
        return null;
    }

    public Map<String, Integer> getUpgrades() { return upgrades; }

    public java.util.Set<UUID> getKnownVillageIds() {
        return java.util.Collections.unmodifiableSet(knownVillageIds);
    }

    public void addKnownVillageId(UUID villageId) {
        if (villageId != null && !villageId.equals(id)) {
            knownVillageIds.add(villageId);
        }
    }

    public void removeKnownVillageId(UUID villageId) {
        if (villageId != null) {
            knownVillageIds.remove(villageId);
        }
    }

    public void setKnownVillageIds(java.util.Collection<UUID> villageIds) {
        knownVillageIds.clear();
        if (villageIds != null) {
            for (UUID villageId : villageIds) {
                if (villageId != null && !villageId.equals(id)) {
                    knownVillageIds.add(villageId);
                }
            }
        }
    }

    public void clearKnownVillageIds() {
        knownVillageIds.clear();
    }

    public int getUpgradeLevel(String upgradeKey) {
        return upgrades.getOrDefault(upgradeKey, 0);
    }

    public void setUpgradeLevel(String upgradeKey, int level) {
        upgrades.put(upgradeKey, level);
    }

    public long getFoundedAt() { return foundedAt; }
    public void setFoundedAt(long foundedAt) { this.foundedAt = foundedAt; }

    public int getMaxMembers(int baseMax, int membersPerLevel) {
        int upgradeLevel = getUpgradeLevel("max-members");
        return baseMax + (upgradeLevel * membersPerLevel);
    }

    public int getMaxVillagers(int baseMax, int villagersPerLevel) {
        int upgradeLevel = getUpgradeLevel("max-villagers");
        return baseMax + (upgradeLevel * villagersPerLevel);
    }

    public int getMaxArea(int baseMax, int areaPerLevel) {
        int upgradeLevel = getUpgradeLevel("border-expansion");
        return baseMax + (upgradeLevel * areaPerLevel);
    }

    public int getMaxBuildRadius(int baseRadius, int radiusPerLevel) {
        int upgradeLevel = getUpgradeLevel("build-radius-expansion");
        return baseRadius + (upgradeLevel * radiusPerLevel);
    }

    public int getMaxBuildRadius() {
        return maxBuildRadius;
    }

    public void setMaxBuildRadius(int radius) {
        this.maxBuildRadius = radius;
    }

    public CustomVillager getLastDeadVillager() {
        return lastDeadVillager;
    }

    public void setLastDeadVillager(CustomVillager villager) {
        this.lastDeadVillager = villager;
    }

    public int getReviveUses() {
        return reviveUses;
    }

    public void setReviveUses(int reviveUses) {
        this.reviveUses = Math.max(0, reviveUses);
    }

    public void incrementReviveUses() {
        this.reviveUses = Math.max(0, this.reviveUses + 1);
    }

    public long getLastReviveAt() {
        return lastReviveAt;
    }

    public void setLastReviveAt(long lastReviveAt) {
        this.lastReviveAt = Math.max(0L, lastReviveAt);
    }
}
