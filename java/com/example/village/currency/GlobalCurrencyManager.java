package com.example.village.currency;

import java.util.UUID;
import java.util.logging.Logger;
import java.util.Objects;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Manager für globale Server-Wirtschaft
 * Integriert mit PlayerCurrency-Plugin
 */
public class GlobalCurrencyManager implements CurrencyManager {
    
    private final PlayerCurrencyAPI currencyAPI;
    private final Logger logger;
    private final String currencyName;
    private final String currencySymbol;
    
    public GlobalCurrencyManager() {
        this(new InMemoryPlayerCurrencyAPI(), Logger.getLogger(GlobalCurrencyManager.class.getName()),
             "Gold", "⊕");
        logger.info("PlayerCurrency plugin not found - using local in-memory currency fallback.");
    }

    /**
     * Initialisiere mit PlayerCurrency API
     * @param api PlayerCurrency API instance
     * @param logger Bukkit Logger
     * @param currencyName Name der globalen Währung
     * @param currencySymbol Symbol (z.B. "⊕")
     */
    public GlobalCurrencyManager(PlayerCurrencyAPI api, Logger logger, 
                                 String currencyName, String currencySymbol) {
        this.currencyAPI = Objects.requireNonNull(api, "PlayerCurrencyAPI cannot be null");
        this.logger = Objects.requireNonNull(logger, "Logger cannot be null");
        this.currencyName = Objects.requireNonNull(currencyName, "Currency name cannot be null");
        this.currencySymbol = Objects.requireNonNull(currencySymbol, "Currency symbol cannot be null");
        logger.info("GlobalCurrencyManager initialized with PlayerCurrency API");
    }
    
    private static class InMemoryPlayerCurrencyAPI implements PlayerCurrencyAPI {
        private final Map<UUID, Double> balances = Collections.synchronizedMap(new HashMap<>());

        @Override
        public double getBalance(UUID player, String currencyName) {
            return balances.getOrDefault(player, 0.0);
        }

        @Override
        public void addBalance(UUID player, String currencyName, double amount) {
            balances.merge(player, amount, Double::sum);
        }

        @Override
        public void removeBalance(UUID player, String currencyName, double amount) {
            double current = balances.getOrDefault(player, 0.0);
            if (current < amount) {
                throw new IllegalStateException("Insufficient balance");
            }
            balances.put(player, current - amount);
        }
    }
    
    /**
     * Hole Balance eines Spielers
     */
    @Override
    public double getBalance(UUID player) {
        try {
            return currencyAPI.getBalance(player, currencyName);
        } catch (Exception e) {
            logger.warning("Failed to get balance for " + player + ": " + e.getMessage());
            return 0.0;
        }
    }
    
    /**
     * Addiere Geld zu Spieler-Konto
     */
    @Override
    public void addBalance(UUID player, double amount) throws InvalidOperationException {
        if (amount < 0) {
            throw new InvalidOperationException("Cannot add negative amount");
        }
        try {
            currencyAPI.addBalance(player, currencyName, amount);
            logger.finest("Added " + amount + " " + currencyName + " to " + player);
        } catch (Exception e) {
            throw new InvalidOperationException("Failed to add balance: " + e.getMessage());
        }
    }
    
    /**
     * Subtrahiere Geld von Spieler-Konto
     */
    @Override
    public void removeBalance(UUID player, double amount) throws InsufficientBalanceException, InvalidOperationException {
        if (amount < 0) {
            throw new InvalidOperationException("Cannot remove negative amount");
        }
        
        double currentBalance = getBalance(player);
        if (currentBalance < amount) {
            throw new InsufficientBalanceException(
                "Player has only " + currentBalance + " but needs " + amount
            );
        }
        
        try {
            currencyAPI.removeBalance(player, currencyName, amount);
            logger.finest("Removed " + amount + " " + currencyName + " from " + player);
        } catch (Exception e) {
            throw new InvalidOperationException("Failed to remove balance: " + e.getMessage());
        }
    }
    
    /**
     * Transferiere Geld zwischen Spielern (atomic)
     */
    @Override
    public void transfer(UUID from, UUID to, double amount) 
            throws InsufficientBalanceException, InvalidOperationException {
        if (amount <= 0) {
            throw new InvalidOperationException("Transfer amount must be positive");
        }
        
        // Check balance first
        if (getBalance(from) < amount) {
            throw new InsufficientBalanceException(
                "Player " + from + " has insufficient balance. Has: " + getBalance(from) + ", needs: " + amount
            );
        }
        
        try {
            // Atomic transfer
            removeBalance(from, amount);
            addBalance(to, amount);
            logger.info("Transferred " + amount + " " + currencyName + " from " + from + " to " + to);
        } catch (Exception e) {
            // Rollback attempt
            logger.severe("Transfer failed between " + from + " and " + to + ": " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * Setze Balance direkt (nur für Admin)
     */
    public void setBalance(UUID player, double amount) throws InvalidOperationException {
        if (amount < 0) {
            throw new InvalidOperationException("Balance cannot be negative");
        }
        try {
            double current = getBalance(player);
            if (current < amount) {
                addBalance(player, amount - current);
            } else if (current > amount) {
                removeBalance(player, current - amount);
            }
        } catch (Exception e) {
            throw new InvalidOperationException("Failed to set balance: " + e.getMessage());
        }
    }
    
    /**
     * Formatiere Betrag mit Währungs-Symbol
     */
    @Override
    public String formatCurrency(double amount) {
        return String.format("%s %.0f", currencySymbol, amount);
    }
    
    public String getCurrencyName() {
        return currencyName;
    }
    
    public String getCurrencySymbol() {
        return currencySymbol;
    }

    public PlayerCurrencyAPI getCurrencyAPI() {
        return currencyAPI;
    }
}
