package com.example.village.model;

import java.util.UUID;

/**
 * Beziehung zwischen einem Spieler und einem Villager.
 */
public class VillagerRelation {
    private final UUID playerId;
    private int reputation;  // -100 bis +100
    private int trustLevel;  // 0-10
    private long lastInteractionTime;
    private int questsCompleted;
    private boolean isFavorite;

    public VillagerRelation(UUID playerId) {
        this.playerId = playerId;
        this.reputation = 0;
        this.trustLevel = 0;
        this.lastInteractionTime = System.currentTimeMillis();
        this.questsCompleted = 0;
        this.isFavorite = false;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public int getReputation() {
        return reputation;
    }

    public void addReputation(int amount) {
        this.reputation = Math.max(-100, Math.min(100, reputation + amount));
    }

    public int getTrustLevel() {
        return trustLevel;
    }

    public void setTrustLevel(int trustLevel) {
        this.trustLevel = Math.max(0, Math.min(10, trustLevel));
    }

    public long getLastInteractionTime() {
        return lastInteractionTime;
    }

    public void updateLastInteraction() {
        this.lastInteractionTime = System.currentTimeMillis();
    }

    public int getQuestsCompleted() {
        return questsCompleted;
    }

    public void completeQuest() {
        questsCompleted++;
    }

    public boolean isFavorite() {
        return isFavorite;
    }

    public void setFavorite(boolean favorite) {
        isFavorite = favorite;
    }

    public String getRelationshipStatus() {
        if (reputation < -50) return "§cVerhasst";
        if (reputation < -20) return "§fUngünstig";
        if (reputation < 20) return "§7Neutral";
        if (reputation < 50) return "§aFreundlich";
        return "§aVerliebt";
    }
}
