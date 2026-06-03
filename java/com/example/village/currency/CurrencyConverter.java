package com.example.village.currency;

import java.util.logging.Logger;

/**
 * Konvertiert zwischen LocalCurrency und GlobalCurrency
 * Mit Konversions-Gebühr (Maklerprovision)
 */
public class CurrencyConverter {
    
    private final double conversionRate;  // z.B. 1.25 bedeutet: 100 Local = 80 Global
    private final Logger logger;
    
    /**
     * @param conversionRate Kosten-Faktor. 1.25 = 100->80 (20% Verlust)
     * @param logger Bukkit Logger
     */
    public CurrencyConverter(double conversionRate, Logger logger) {
        if (conversionRate < 1.0) {
            throw new IllegalArgumentException("Conversion rate must be >= 1.0");
        }
        this.conversionRate = conversionRate;
        this.logger = logger;
    }
    
    /**
     * Konvertiere LocalCurrency zu GlobalCurrency (mit Verlust)
     * @param localAmount Betrag in Local
     * @return Betrag in Global (nach Gebühr)
     */
    public double convertLocalToGlobal(double localAmount) {
        double globalAmount = localAmount / conversionRate;
        logger.fine("Converted " + localAmount + " LOCAL to " + globalAmount + " GLOBAL");
        return globalAmount;
    }
    
    /**
     * Konvertiere GlobalCurrency zu LocalCurrency (kein Verlust)
     * @param globalAmount Betrag in Global
     * @return Betrag in Local (exakt)
     */
    public double convertGlobalToLocal(double globalAmount) {
        double localAmount = globalAmount * conversionRate;
        logger.fine("Converted " + globalAmount + " GLOBAL to " + localAmount + " LOCAL");
        return localAmount;
    }
    
    /**
     * Berechne Gebühr für Konversion
     */
    public double calculateFee(double localAmount) {
        return localAmount - convertLocalToGlobal(localAmount);
    }
    
    /**
     * Berechne Konversions-Prozentsatz
     */
    public double getConversionLossPercent() {
        return ((conversionRate - 1.0) / conversionRate) * 100;
    }
    
    public double getConversionRate() {
        return conversionRate;
    }
}
