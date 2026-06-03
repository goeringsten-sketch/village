package com.example.village.currency;

/**
 * Enum für die zwei Arten von Währungen im Village-Plugin
 */
public enum CurrencyType {
    /**
     * Lokale Dorf-Währung
     * - Pro Dorf isoliert
     * - Von Villager generiert
     * - Name konfigurierbar
     */
    LOCAL("Dorfspezifisch"),
    
    /**
     * Globale Server-Währung
     * - Server-weit verfügbar
     * - Für Dorf-übergreifend Handel
     * - Name: "Goldmünze"
     */
    GLOBAL("Universell");
    
    private final String description;
    
    CurrencyType(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}
