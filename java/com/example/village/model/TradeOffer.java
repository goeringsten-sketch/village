package com.example.village.model;

import com.example.village.currency.CurrencyType;

/**
 * Ein Handels-Angebot von einem Villager
 */
public class TradeOffer {
    
    private String itemId;              // z.B. "WHEAT"
    private String displayName;         // z.B. "Weizen"
    private int quantityPerTrade;       // Wie viel pro Handel
    private double price;               // Preis pro Trade
    private CurrencyType currency;      // LOCAL oder GLOBAL
    private int stock;                  // Wie viel noch verfügbar?
    private long createdAt;
    
    public TradeOffer(String itemId, String displayName, int quantityPerTrade, 
                     double price, CurrencyType currency, int stock) {
        this.itemId = itemId;
        this.displayName = displayName;
        this.quantityPerTrade = quantityPerTrade;
        this.price = price;
        this.currency = currency;
        this.stock = stock;
        this.createdAt = System.currentTimeMillis();
    }
    
    // Getter/Setter
    public String getItemId() {
        return itemId;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public int getQuantityPerTrade() {
        return quantityPerTrade;
    }
    
    public double getPrice() {
        return price;
    }
    
    public CurrencyType getCurrency() {
        return currency;
    }
    
    public int getStock() {
        return stock;
    }
    
    public void setStock(int stock) {
        this.stock = stock;
    }
    
    public void reduceStock(int quantity) {
        this.stock = Math.max(0, this.stock - quantity);
    }
    
    public void addStock(int quantity) {
        this.stock += quantity;
    }
    
    public boolean hasStock(int quantity) {
        return stock >= quantity;
    }
    
    public long getCreatedAt() {
        return createdAt;
    }
    
    // Für GUI-Anzeige
    public String getPriceDisplay(String symbol) {
        return String.format("%s %.0f", symbol, price);
    }
}
