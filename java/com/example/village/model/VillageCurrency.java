package com.example.village.model;

import java.util.*;

/**
 * Datenmodell für Dorf-spezifische Währung
 */
public class VillageCurrency {
    
    private final String villageUUID;
    private final String currencyName;
    private final String currencySymbol;
    private final double startingAmount;
    
    // Konten
    private final Map<UUID, Double> playerAccounts;
    private final Map<String, Double> villagerAccounts;
    
    // Metadaten
    private boolean tradingEnabled;
    private List<String> allowedTraders;
    private long createdAt;
    
    public VillageCurrency(String villageUUID, String currencyName, String currencySymbol, 
                          double startingAmount) {
        this.villageUUID = villageUUID;
        this.currencyName = currencyName;
        this.currencySymbol = currencySymbol;
        this.startingAmount = startingAmount;
        this.playerAccounts = Collections.synchronizedMap(new HashMap<>());
        this.villagerAccounts = Collections.synchronizedMap(new HashMap<>());
        this.tradingEnabled = false;
        this.allowedTraders = Collections.synchronizedList(new ArrayList<>());
        this.createdAt = System.currentTimeMillis();
    }
    
    // ============ GETTER ============
    
    public String getVillageUUID() {
        return villageUUID;
    }
    
    public String getCurrencyName() {
        return currencyName;
    }
    
    public String getCurrencySymbol() {
        return currencySymbol;
    }
    
    public double getStartingAmount() {
        return startingAmount;
    }
    
    public boolean isTradingEnabled() {
        return tradingEnabled;
    }
    
    public void setTradingEnabled(boolean enabled) {
        this.tradingEnabled = enabled;
    }
    
    public List<String> getAllowedTraders() {
        return new ArrayList<>(allowedTraders);
    }
    
    public void setAllowedTraders(List<String> traders) {
        this.allowedTraders = Collections.synchronizedList(new ArrayList<>(traders));
    }
    
    public long getCreatedAt() {
        return createdAt;
    }
    
    // ============ STATISTIKEN ============
    
    /**
     * Gesamt-Guthaben im Umlauf
     */
    public double getTotalInCirculation() {
        double total = 0;
        for (Double balance : playerAccounts.values()) {
            total += balance;
        }
        for (Double balance : villagerAccounts.values()) {
            total += balance;
        }
        return total;
    }
    
    /**
     * Anzahl Spieler mit Guthaben
     */
    public int getPlayerCount() {
        return playerAccounts.size();
    }
    
    /**
     * Anzahl Villager mit Guthaben
     */
    public int getVillagerCount() {
        return villagerAccounts.size();
    }
    
    @Override
    public String toString() {
        return "VillageCurrency{" +
            "village=" + villageUUID.substring(0, 8) +
            ", name='" + currencyName + '\'' +
            ", total=" + getTotalInCirculation() +
            ", players=" + getPlayerCount() +
            ", villagers=" + getVillagerCount() +
            '}';
    }
}
