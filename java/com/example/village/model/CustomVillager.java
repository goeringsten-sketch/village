package com.example.village.model;

import org.bukkit.Location;
import org.bukkit.Material;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Erweiterte Villager-Klasse mit Berufen, Fähigkeiten, Bedürfnissen und Beziehungen.
 */
public final class CustomVillager {

    private final UUID id;
    private String name;
    private VillagerJob job;
    private int level;
    private double xp;
    private double wallet;
    private final Map<Material, Integer> inventory;
    private final Map<VillagerNeed, Double> needs;
    private final Map<String, Double> nutrientLevels;
    private final Map<String, Double> nutrientCapacities;
    private final Map<String, VillagerSkill> skills;
    private Location homeLocation;
    private Location workLocation;
    private long lastProductionTime;
    private UUID assignedBedBuildingId;
    private UUID assignedJobBuildingId;
    private UUID parentVillageId;
    
    // Neues System
    private VillagerState currentState;
    private long stateChangeTime;
    private int npcId = -1;  // Citizens NPC ID
    private UUID vanillaEntityId;  // UUID der Vanilla-Villager Entity
    private boolean isRecruited;
    private long recruitmentDate;
    private final Map<UUID, VillagerRelation> playerRelations;
    private double morale;  // 0-100
    /** Aktivität für nährstoffabhängigen Decay (WORK, IDLE, SLEEP, WAKE_UP) */
    private String activityDecayKey = "IDLE";
    /** Steuerung fuer das Produktions-Menue: Produktion pausiert? */
    private boolean productionPaused = false;
    /** Steuerung fuer das Produktions-Menue: gewuenschtes Zielprodukt (null = alle moeglichen Produkte). */
    private Material preferredProduct;

    public CustomVillager(UUID id, String name, VillagerJob job) {
        this.id = id;
        this.name = name;
        this.job = job;
        this.level = 1;
        this.xp = 0;
        this.wallet = 0;
        this.inventory = new HashMap<>();
        this.needs = new EnumMap<>(VillagerNeed.class);
        this.nutrientLevels = new HashMap<>();
        this.nutrientCapacities = new HashMap<>();
        this.skills = new HashMap<>();
        this.playerRelations = new HashMap<>();
        this.currentState = VillagerState.IDLE;
        this.stateChangeTime = System.currentTimeMillis();
        this.lastProductionTime = System.currentTimeMillis();
        this.isRecruited = false;
        this.morale = 50;
        initializeNeeds();
        initializeSkills();
    }

    private void initializeNeeds() {
        for (VillagerNeed need : VillagerNeed.values()) {
            needs.put(need, 100.0);
        }
    }

    private void initializeSkills() {
        for (String skillName : job.getSkillTrees()) {
            skills.put(skillName, new VillagerSkill(skillName));
        }
    }

    // Getter & Setter
    public UUID getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public VillagerJob getJob() { return job; }
    public void setJob(VillagerJob job) { 
        this.job = job; 
        skills.clear();
        initializeSkills();
    }

    // Backward compatibility methods
    public String getProfessionKey() {
        return job != null ? job.getProfessionKey() : null;
    }
    public void setProfessionKey(String professionKey) {
        setJob(VillagerJob.fromString(professionKey));
    }
    
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }
    public double getXp() { return xp; }
    public void setXp(double xp) { this.xp = xp; }
    public void addXp(double amount) { this.xp += amount; }
    
    public double getWallet() { return wallet; }
    public void setWallet(double wallet) { this.wallet = wallet; }
    public void addMoney(double amount) { this.wallet = Math.max(0, wallet + amount); }
    
    // Inventory
    public Map<Material, Integer> getInventory() { return inventory; }
    public void addItem(Material material, int amount) {
        inventory.merge(material, amount, Integer::sum);
    }
    public boolean removeItem(Material material, int amount) {
        int current = inventory.getOrDefault(material, 0);
        if (current < amount) return false;
        int remaining = current - amount;
        if (remaining <= 0) {
            inventory.remove(material);
        } else {
            inventory.put(material, remaining);
        }
        return true;
    }
    
    // Needs
    public Map<VillagerNeed, Double> getNeeds() { return needs; }
    public double getNeedValue(VillagerNeed need) {
        if (need == VillagerNeed.HUNGER && !nutrientLevels.isEmpty()) {
            return calculateAverageNutritionHunger();
        }
        return needs.getOrDefault(need, 100.0);
    }
    public void setNeedValue(VillagerNeed need, double value) {
        needs.put(need, Math.max(0, Math.min(100, value)));
    }
    public void decayNeeds(double minutes) {
        for (VillagerNeed need : VillagerNeed.values()) {
            if (need == VillagerNeed.HUNGER && !nutrientLevels.isEmpty()) {
                continue;
            }
            double current = getNeedValue(need);
            double decay = need.getDefaultDecayPerMinute() * minutes;
            setNeedValue(need, current - decay);
        }
    }
    public boolean isNeedCritical(VillagerNeed need) {
        return getNeedValue(need) <= need.getDefaultCriticalThreshold();
    }

    public Map<String, Double> getNutrientLevels() { return nutrientLevels; }
    public Map<String, Double> getNutrientCapacities() { return nutrientCapacities; }
    public String getActivityDecayKey() { return activityDecayKey; }
    public void setActivityDecayKey(String activityDecayKey) {
        this.activityDecayKey = activityDecayKey != null ? activityDecayKey : "IDLE";
    }
    public double getNutrientLevel(String nutrientKey) {
        return nutrientLevels.getOrDefault(nutrientKey.toLowerCase(Locale.ROOT), 0.0);
    }
    public void setNutrientLevel(String nutrientKey, double value) {
        String key = nutrientKey.toLowerCase(Locale.ROOT);
        double capacity = getNutrientCapacity(key);
        nutrientLevels.put(key, Math.max(0.0, Math.min(capacity, value)));
    }
    public void addNutrientLevel(String nutrientKey, double amount) {
        setNutrientLevel(nutrientKey, getNutrientLevel(nutrientKey) + amount);
    }
    public double getNutrientCapacity(String nutrientKey) {
        String key = nutrientKey.toLowerCase(Locale.ROOT);
        return nutrientCapacities.getOrDefault(key, 100.0);
    }
    public void setNutrientCapacity(String nutrientKey, double capacity) {
        nutrientCapacities.put(nutrientKey.toLowerCase(Locale.ROOT), Math.max(1.0, capacity));
    }
    public void decayNutrients(double minutes) {
        if (nutrientLevels.isEmpty()) {
            return;
        }

        double decayPerMinute = VillagerNeed.HUNGER.getDefaultDecayPerMinute();
        for (Map.Entry<String, Double> entry : new HashMap<>(nutrientLevels).entrySet()) {
            double capacity = getNutrientCapacity(entry.getKey());
            double decay = decayPerMinute * minutes * (capacity / 100.0);
            setNutrientLevel(entry.getKey(), entry.getValue() - decay);
        }
    }

    public double calculateAverageNutritionHunger() {
        if (nutrientLevels.isEmpty()) {
            return needs.getOrDefault(VillagerNeed.HUNGER, 100.0);
        }
        double sum = 0.0;
        int count = 0;
        for (Map.Entry<String, Double> entry : nutrientLevels.entrySet()) {
            double capacity = getNutrientCapacity(entry.getKey());
            double percent = capacity > 0 ? (entry.getValue() / capacity) * 100.0 : 0.0;
            sum += percent;
            count++;
        }
        return count == 0 ? needs.getOrDefault(VillagerNeed.HUNGER, 100.0) : Math.max(0.0, Math.min(100.0, sum / count));
    }
    
    // Skills
    public Map<String, VillagerSkill> getSkills() { return skills; }
    public VillagerSkill getSkill(String skillName) {
        return skills.getOrDefault(skillName, null);
    }
    public void addSkillXp(String skillName, double xp) {
        VillagerSkill skill = skills.get(skillName);
        if (skill != null) {
            skill.addXp(xp);
        }
    }
    
    // Locations
    public Location getHomeLocation() { return homeLocation; }
    public void setHomeLocation(Location homeLocation) { this.homeLocation = homeLocation; }
    public Location getWorkLocation() { return workLocation; }
    public void setWorkLocation(Location workLocation) { this.workLocation = workLocation; }
    
    // Production
    public long getLastProductionTime() { return lastProductionTime; }
    public void setLastProductionTime(long lastProductionTime) { this.lastProductionTime = lastProductionTime; }
    public long getLastProductionDelta() { return System.currentTimeMillis() - lastProductionTime; }
    public boolean isProductionPaused() { return productionPaused; }
    public void setProductionPaused(boolean productionPaused) { this.productionPaused = productionPaused; }
    public Material getPreferredProduct() { return preferredProduct; }
    public void setPreferredProduct(Material preferredProduct) { this.preferredProduct = preferredProduct; }
    
    // Buildings
    public UUID getAssignedBedBuildingId() { return assignedBedBuildingId; }
    public void setAssignedBedBuildingId(UUID assignedBedBuildingId) { this.assignedBedBuildingId = assignedBedBuildingId; }
    public UUID getAssignedJobBuildingId() { return assignedJobBuildingId; }
    public void setAssignedJobBuildingId(UUID assignedJobBuildingId) { this.assignedJobBuildingId = assignedJobBuildingId; }
    
    // State
    public VillagerState getCurrentState() { return currentState; }
    public void setState(VillagerState state) {
        this.currentState = state;
        this.stateChangeTime = System.currentTimeMillis();
    }
    public long getStateChangeDelta() { return System.currentTimeMillis() - stateChangeTime; }
    
    // Village
    public UUID getParentVillageId() { return parentVillageId; }
    public void setParentVillageId(UUID villageId) { this.parentVillageId = villageId; }
    
    // NPC
    public int getNpcId() { return npcId; }
    public void setNpcId(int npcId) { this.npcId = npcId; }
    
    // Recruitment
    public boolean isRecruited() { return isRecruited; }
    public void recruit() { 
        this.isRecruited = true;
        this.recruitmentDate = System.currentTimeMillis();
    }
    public long getRecruitmentDate() { return recruitmentDate; }
    
    // Morale
    public double getMorale() { return morale; }
    public void setMorale(double morale) { 
        this.morale = Math.max(0, Math.min(100, morale)); 
    }
    public void adjustMorale(double change) {
        setMorale(morale + change);
    }
    
    // Relations
    public Map<UUID, VillagerRelation> getPlayerRelations() { return playerRelations; }
    public VillagerRelation getRelation(UUID playerId) {
        return playerRelations.computeIfAbsent(playerId, VillagerRelation::new);
    }

    // Vanilla Entity
    public UUID getVanillaEntityId() { return vanillaEntityId; }
    public void setVanillaEntityId(UUID vanillaEntityId) { this.vanillaEntityId = vanillaEntityId; }
}
