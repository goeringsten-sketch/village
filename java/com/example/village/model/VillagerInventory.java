package com.example.village.model;

import java.util.*;

/**
 * Villager-Inventar: Ressourcen, die ein Villager hat
 */
public class VillagerInventory {
    
    // Ressourcen im Bestand (itemId -> Menge)
    private final Map<String, Integer> resources;
    
    // Was der Villager verkaufen möchte
    private final List<TradeOffer> forSale;
    
    // Was der Villager kaufen möchte (optional)
    private final List<TradeOffer> wantToBuy;
    
    private final int maxSlots;         // Lagergröße
    
    public VillagerInventory(int maxSlots) {
        this.resources = Collections.synchronizedMap(new HashMap<>());
        this.forSale = Collections.synchronizedList(new ArrayList<>());
        this.wantToBuy = Collections.synchronizedList(new ArrayList<>());
        this.maxSlots = maxSlots;
    }
    
    // ============ RESSOURCEN ============
    
    /**
     * Addiere Ressource zum Bestand
     */
    public void addResource(String itemId, int amount) {
        resources.merge(itemId, amount, Integer::sum);
    }
    
    /**
     * Entferne Ressource vom Bestand
     */
    public boolean removeResource(String itemId, int amount) {
        int current = resources.getOrDefault(itemId, 0);
        if (current < amount) {
            return false;
        }
        resources.put(itemId, current - amount);
        if (resources.get(itemId) <= 0) {
            resources.remove(itemId);
        }
        return true;
    }
    
    /**
     * Hole Menge einer Ressource
     */
    public int getResource(String itemId) {
        return resources.getOrDefault(itemId, 0);
    }
    
    /**
     * Hole alle Ressourcen
     */
    public Map<String, Integer> getAllResources() {
        return new HashMap<>(resources);
    }
    
    /**
     * Lagerbestand (Anzahl verschiedener Item-Typen)
     */
    public int getStorageUsed() {
        return resources.size();
    }
    
    public boolean hasSpace() {
        return getStorageUsed() < maxSlots;
    }
    
    // ============ VERKAUFS-ANGEBOTE ============
    
    /**
     * Addiere Verkaufs-Angebot
     */
    public void addForSale(TradeOffer offer) {
        forSale.add(offer);
    }
    
    /**
     * Entferne Verkaufs-Angebot
     */
    public void removeForSale(TradeOffer offer) {
        forSale.remove(offer);
    }
    
    /**
     * Hole alle Verkaufs-Angebote
     */
    public List<TradeOffer> getForSale() {
        return new ArrayList<>(forSale);
    }
    
    /**
     * Finde Angebot nach Item-ID
     */
    public TradeOffer findSaleOffer(String itemId) {
        return forSale.stream()
            .filter(offer -> offer.getItemId().equals(itemId))
            .findFirst()
            .orElse(null);
    }
    
    // ============ KAUF-WÜNSCHE ============
    
    /**
     * Addiere Kauf-Wunsch
     */
    public void addWantToBuy(TradeOffer offer) {
        wantToBuy.add(offer);
    }
    
    /**
     * Entferne Kauf-Wunsch
     */
    public void removeWantToBuy(TradeOffer offer) {
        wantToBuy.remove(offer);
    }
    
    /**
     * Hole alle Kauf-Wünsche
     */
    public List<TradeOffer> getWantToBuy() {
        return new ArrayList<>(wantToBuy);
    }
    
    // ============ UTIL ============
    
    public void clear() {
        resources.clear();
        forSale.clear();
        wantToBuy.clear();
    }
    
    @Override
    public String toString() {
        return "VillagerInventory{" +
            "resources=" + resources.size() +
            ", forSale=" + forSale.size() +
            ", maxSlots=" + maxSlots +
            '}';
    }
}
