package com.example.village.currency;

import java.util.UUID;

/**
 * Base interface für Währungs-Management
 */
public interface CurrencyManager {
    
    /**
     * Hole Balance eines Spielers
     */
    double getBalance(UUID player);
    
    /**
     * Addiere Geld zu Spieler-Konto
     */
    void addBalance(UUID player, double amount) throws InvalidOperationException;
    
    /**
     * Subtrahiere Geld von Spieler-Konto
     */
    void removeBalance(UUID player, double amount) throws InsufficientBalanceException, InvalidOperationException;
    
    /**
     * Transferiere Geld zwischen Spielern
     */
    void transfer(UUID from, UUID to, double amount) throws InsufficientBalanceException, InvalidOperationException;
    
    /**
     * Formatiere Betrag mit Währungs-Symbol
     */
    String formatCurrency(double amount);
}
