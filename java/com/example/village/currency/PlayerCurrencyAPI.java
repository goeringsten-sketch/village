package com.example.village.currency;

import java.util.UUID;

/**
 * Stub-Interface für PlayerCurrency API
 * Diese wird vom externen PlayerCurrency-Plugin bereitgestellt
 * Das ist nur ein lokales Interface zur Kompilierung
 */
public interface PlayerCurrencyAPI {
    
    /**
     * Hole den Balance eines Spielers für die gegebene Währung
     */
    double getBalance(UUID player, String currencyName);
    
    /**
     * Addiere zu Balance
     */
    void addBalance(UUID player, String currencyName, double amount);
    
    /**
     * Subtrahiere von Balance
     */
    void removeBalance(UUID player, String currencyName, double amount);

    default boolean hasCurrency(String currencyId) { return true; }
    default void ensureAccount(UUID playerId, String playerName) { }
}
