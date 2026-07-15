package com.example.village.currency;

import java.util.*;
import java.util.logging.Logger;
import java.util.concurrent.ConcurrentHashMap;
import com.example.village.data.CurrencyQueries;

/**
 * Manager für lokale Dorf-Währung
 * Eine Instanz pro Dorf
 */
public class LocalCurrencyManager implements CurrencyManager {
    
    private final String villageId;
    private final String villageUUID;
    private final String currencyId;
    private final String currencyName;
    private final String currencySymbol;
    private final Logger logger;
    private final PlayerCurrencyAPI currencyAPI;
    
    // In-Memory Cache für schnellen Zugriff
    private final Map<UUID, Double> playerBalances;
    private final Map<String, Double> villagerBalances;
    
    // Datenbank-Backend
    private CurrencyQueries queries;
    
    /**
     * Erstelle LocalCurrencyManager für ein Dorf
     * @param villageUUID Unique identifier des Dorfes
     * @param currencyName Name der Dorf-Währung (z.B. "Waldstein-Taler")
     * @param currencySymbol Symbol (z.B. "Ɖ")
     * @param logger Bukkit Logger
     */
    public LocalCurrencyManager(String villageUUID, String currencyId, String currencyName,
                               String currencySymbol, Logger logger) {
        this(villageUUID, currencyId, currencyName, currencySymbol, logger, null, false);
    }

    public LocalCurrencyManager(String villageUUID, String currencyId, String currencyName,
                               String currencySymbol, Logger logger, PlayerCurrencyAPI currencyAPI) {
        this(villageUUID, currencyId, currencyName, currencySymbol, logger, currencyAPI, false);
    }

    public LocalCurrencyManager(String villageUUID, String currencyId, String currencyName,
                               String currencySymbol, Logger logger, PlayerCurrencyAPI currencyAPI,
                               boolean logCreation) {
        this.villageUUID = Objects.requireNonNull(villageUUID, "Village UUID cannot be null");
        this.currencyId = Objects.requireNonNull(currencyId, "Currency id cannot be null");
        this.currencyName = Objects.requireNonNull(currencyName, "Currency name cannot be null");
        this.currencySymbol = Objects.requireNonNull(currencySymbol, "Currency symbol cannot be null");
        this.logger = Objects.requireNonNull(logger, "Logger cannot be null");
        this.currencyAPI = currencyAPI;
        
        // Extract village ID from UUID (for logging)
        this.villageId = villageUUID.substring(0, 8);
        
        // Thread-safe Maps
        this.playerBalances = Collections.synchronizedMap(new HashMap<>());
        this.villagerBalances = Collections.synchronizedMap(new HashMap<>());
        
        if (logCreation) {
            logger.info("LocalCurrencyManager created for village " + villageId +
                    " with currency: " + currencyName);
        }
    }

    /**
     * Setze das Queries Backend für Datenbank-Persistence
     */
    public void setQueries(CurrencyQueries queries) {
        this.queries = queries;
    }
    
    /**
     * Initialisiere einen Spieler mit Startguthaben
     * (wird beim Trading-Unlock aufgerufen)
     */
    public void initializePlayer(UUID player, double startingAmount) throws InvalidOperationException {
        if (startingAmount < 0) {
            throw new InvalidOperationException("Starting amount cannot be negative");
        }

        if (currencyAPI != null) {
            double current = currencyAPI.getBalance(player, currencyId);
            if (current <= 0 && startingAmount > 0) {
                currencyAPI.addBalance(player, currencyId, startingAmount);
            }
            return;
        }

        if (!playerBalances.containsKey(player)) {
            playerBalances.put(player, startingAmount);
            logger.fine("Initialized player " + player + " with " + startingAmount + " " + currencyName);
            if (queries != null) {
                queries.setPlayerBalance(villageUUID, player.toString(), startingAmount);
            }
        }
    }
    
    /**
     * Initialisiere einen Villager mit Startguthaben
     */
    public void initializeVillager(String villagerUUID, double startingAmount) throws InvalidOperationException {
        if (startingAmount < 0) {
            throw new InvalidOperationException("Starting amount cannot be negative");
        }
        
        if (!villagerBalances.containsKey(villagerUUID)) {
            villagerBalances.put(villagerUUID, startingAmount);
            logger.fine("Initialized villager " + villagerUUID + " with " + startingAmount + " " + currencyName);
            if (queries != null) {
                queries.setVillagerBalance(villageUUID, villagerUUID, startingAmount);
            }
        }
    }
    
    /**
     * Hole Balance eines Spielers
     */
    @Override
    public double getBalance(UUID player) {
        if (currencyAPI != null) {
            return currencyAPI.getBalance(player, currencyId);
        }
        return playerBalances.getOrDefault(player, 0.0);
    }
    
    /**
     * Hole Balance eines Villagers
     */
    public double getVillagerBalance(String villagerUUID) {
        return villagerBalances.getOrDefault(villagerUUID, 0.0);
    }
    
    /**
     * Addiere Geld zu Spieler-Konto (Spieler from Dorf)
     */
    @Override
    public void addBalance(UUID player, double amount) throws InvalidOperationException {
        if (amount < 0) {
            throw new InvalidOperationException("Cannot add negative amount");
        }

        if (currencyAPI != null) {
            currencyAPI.addBalance(player, currencyId, amount);
            return;
        }

        playerBalances.merge(player, amount, Double::sum);
        logger.finest("Added " + amount + " " + currencyName + " to player " + player);
        if (queries != null) {
            queries.setPlayerBalance(villageUUID, player.toString(), getBalance(player));
        }
    }
    
    /**
     * Addiere Geld zu Villager-Konto
     */
    public void addVillagerBalance(String villagerUUID, double amount) throws InvalidOperationException {
        if (amount < 0) {
            throw new InvalidOperationException("Cannot add negative amount");
        }
        
        villagerBalances.merge(villagerUUID, amount, Double::sum);
        logger.finest("Added " + amount + " " + currencyName + " to villager " + villagerUUID);
        if (queries != null) {
            queries.setVillagerBalance(villageUUID, villagerUUID, getVillagerBalance(villagerUUID));
        }
    }
    
    /**
     * Subtrahiere Geld von Spieler-Konto
     */
    @Override
    public void removeBalance(UUID player, double amount) 
            throws InsufficientBalanceException, InvalidOperationException {
        if (amount < 0) {
            throw new InvalidOperationException("Cannot remove negative amount");
        }
        
        double currentBalance = getBalance(player);
        if (currentBalance < amount) {
            throw new InsufficientBalanceException(
                "Player has only " + currentBalance + " but needs " + amount
            );
        }

        if (currencyAPI != null) {
            currencyAPI.removeBalance(player, currencyId, amount);
            return;
        }

        playerBalances.merge(player, -amount, Double::sum);
        logger.finest("Removed " + amount + " " + currencyName + " from player " + player);
        if (queries != null) {
            queries.setPlayerBalance(villageUUID, player.toString(), getBalance(player));
        }
    }
    
    /**
     * Subtrahiere Geld von Villager-Konto
     */
    public void removeVillagerBalance(String villagerUUID, double amount) 
            throws InsufficientBalanceException, InvalidOperationException {
        if (amount < 0) {
            throw new InvalidOperationException("Cannot remove negative amount");
        }
        
        double currentBalance = getVillagerBalance(villagerUUID);
        if (currentBalance < amount) {
            throw new InsufficientBalanceException(
                "Villager has only " + currentBalance + " but needs " + amount
            );
        }
        
        villagerBalances.merge(villagerUUID, -amount, Double::sum);
        logger.finest("Removed " + amount + " " + currencyName + " from villager " + villagerUUID);
        if (queries != null) {
            queries.setVillagerBalance(villageUUID, villagerUUID, getVillagerBalance(villagerUUID));
        }
    }
    
    /**
     * Transferiere zwischen zwei Spielern (Spieler → Spieler)
     */
    @Override
    public void transfer(UUID from, UUID to, double amount) 
            throws InsufficientBalanceException, InvalidOperationException {
        if (amount <= 0) {
            throw new InvalidOperationException("Transfer amount must be positive");
        }
        
        removeBalance(from, amount);  // Prüft Guthaben
        addBalance(to, amount);
        logger.info("Transferred " + amount + " " + currencyName + " from " + from + " to " + to);
    }
    
    /**
     * Transferiere von Spieler zu Villager (Spieler kauft)
     */
    public void transferToVillager(UUID player, String villagerUUID, double amount) 
            throws InsufficientBalanceException, InvalidOperationException {
        removeBalance(player, amount);
        addVillagerBalance(villagerUUID, amount);
        logger.fine("Transferred " + amount + " " + currencyName + 
                   " from player " + player + " to villager " + villagerUUID);
    }
    
    /**
     * Transferiere von Villager zu Spieler (Spieler verkauft)
     */
    public void transferFromVillager(String villagerUUID, UUID player, double amount) 
            throws InsufficientBalanceException, InvalidOperationException {
        removeVillagerBalance(villagerUUID, amount);
        addBalance(player, amount);
        logger.fine("Transferred " + amount + " " + currencyName + 
                   " from villager " + villagerUUID + " to player " + player);
    }
    
    /**
     * Formatiere Betrag mit Währungs-Symbol
     */
    @Override
    public String formatCurrency(double amount) {
        return String.format("%s %.0f", currencySymbol, amount);
    }
    
    // Getter
    public String getVillageUUID() {
        return villageUUID;
    }
    
    public String getVillageId() {
        return villageId;
    }
    
    public String getCurrencyName() {
        return currencyName;
    }

    public String getCurrencyId() {
        return currencyId;
    }
    
    public String getCurrencySymbol() {
        return currencySymbol;
    }
    
    /**
     * Statistik: Gesamtguthaben im Umlauf
     */
    public double getTotalInCirculation() {
        double total = 0;
        for (Double balance : playerBalances.values()) {
            total += balance;
        }
        for (Double balance : villagerBalances.values()) {
            total += balance;
        }
        return total;
    }
}
