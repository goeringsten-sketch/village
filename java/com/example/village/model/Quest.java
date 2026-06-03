package com.example.village.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Quest-Template für Villager Quest-Vergabe.
 */
public class Quest {
    private String questId;
    private String title;
    private String description;
    private String objective;
    private int requiredVillageLevel;
    private int minReputation;
    private List<String> prerequisites;
    private int rewardVillagePoints;
    private double rewardGlobalMoney;
    private double rewardLocalMoney;
    private Map<String, Integer> rewardItems;
    private int rewardVillagerXp;
    private boolean isDailyQuest;
    private long startTime;
    private long deadline;
    private UUID giverVillagerId;
    private UUID assignedPlayerId;
    private boolean isCompleted;
    private int priority;  // 1-5

    public Quest(String questId, String title) {
        this.questId = questId;
        this.title = title;
        this.description = "";
        this.objective = "";
        this.requiredVillageLevel = 1;
        this.minReputation = -100;
        this.prerequisites = new ArrayList<>();
        this.rewardVillagePoints = 0;
        this.rewardGlobalMoney = 0;
        this.rewardLocalMoney = 0;
        this.rewardItems = new HashMap<>();
        this.rewardVillagerXp = 0;
        this.isDailyQuest = false;
        this.isCompleted = false;
        this.priority = 3;
        this.startTime = System.currentTimeMillis();
    }

    // Getters & Setters
    public String getQuestId() { return questId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getObjective() { return objective; }
    public void setObjective(String objective) { this.objective = objective; }

    public int getRequiredVillageLevel() { return requiredVillageLevel; }
    public void setRequiredVillageLevel(int level) { this.requiredVillageLevel = level; }

    public int getMinReputation() { return minReputation; }
    public void setMinReputation(int rep) { this.minReputation = rep; }

    public List<String> getPrerequisites() { return prerequisites; }
    public void addPrerequisite(String questId) { this.prerequisites.add(questId); }

    public int getRewardVillagePoints() { return rewardVillagePoints; }
    public void setRewardVillagePoints(int points) { this.rewardVillagePoints = points; }

    public double getRewardMoney() { return rewardGlobalMoney; }
    public void setRewardMoney(double money) { this.rewardGlobalMoney = money; }
    public double getRewardGlobalMoney() { return rewardGlobalMoney; }
    public void setRewardGlobalMoney(double money) { this.rewardGlobalMoney = money; }
    public double getRewardLocalMoney() { return rewardLocalMoney; }
    public void setRewardLocalMoney(double money) { this.rewardLocalMoney = money; }

    public Map<String, Integer> getRewardItems() { return rewardItems; }
    public void addRewardItem(String material, int amount) { 
        rewardItems.put(material, rewardItems.getOrDefault(material, 0) + amount); 
    }

    public int getRewardVillagerXp() { return rewardVillagerXp; }
    public void setRewardVillagerXp(int xp) { this.rewardVillagerXp = xp; }

    public boolean isDailyQuest() { return isDailyQuest; }
    public void setDailyQuest(boolean daily) { this.isDailyQuest = daily; }

    public UUID getGiverVillagerId() { return giverVillagerId; }
    public void setGiverVillagerId(UUID id) { this.giverVillagerId = id; }

    public UUID getAssignedPlayerId() { return assignedPlayerId; }
    public void setAssignedPlayerId(UUID id) { this.assignedPlayerId = id; }

    public boolean isCompleted() { return isCompleted; }
    public void complete() { this.isCompleted = true; }

    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = Math.max(1, Math.min(5, priority)); }

    public long getStartTime() { return startTime; }
    public long getDeadline() { return deadline; }
    public void setDeadline(long deadline) { this.deadline = deadline; }

    public boolean isExpired() {
        return deadline > 0 && System.currentTimeMillis() > deadline;
    }
}
