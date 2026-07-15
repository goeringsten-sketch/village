package com.example.village.integration;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import com.example.village.VillagePlugin;
import com.example.village.currency.*;
import com.example.village.trading.*;
import com.example.village.config.*;
import com.example.village.data.*;
import com.example.village.hook.OpenEcoCurrencyHook;
import com.example.village.model.Village;

import java.io.File;
import java.util.*;
import java.util.logging.Logger;

/**
 * Integration Manager der das komplette Handel-System zusammenbindet
 * 
 * Initialisiert:
 * - Currency Manager (global + lokal)
 * - Trading UIs (4 Szenarien)
 * - Config Manager
 * - Database Manager
 * - Query Layer
 */
public class CurrencyIntegrationManager {
    
    private final Plugin plugin;
    private final Logger logger;
    
    // Manager
    private GlobalCurrencyManager globalCurrencyManager;
    private Map<String, LocalCurrencyManager> localCurrencyManagers;  // Per Village
    private CurrencyConverter currencyConverter;
    
    // Trading
    private Map<String, VillagerLocalTradeUI> localTradeUIs;
    private Map<String, VillagerExternalTradeUI> externalTradeUIs;
    private PlayerToPlayerTradeUI playerToPlayerTradeUI;
    private MarketplaceManager marketplaceManager;
    
    // Configuration & Data
    private CurrencyConfigManager configManager;
    private SQLiteDatabaseManager databaseManager;
    private CurrencyQueries queries;
    
    public CurrencyIntegrationManager(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin);
        this.logger = plugin.getLogger();
        this.localCurrencyManagers = Collections.synchronizedMap(new HashMap<>());
        this.localTradeUIs = Collections.synchronizedMap(new HashMap<>());
        this.externalTradeUIs = Collections.synchronizedMap(new HashMap<>());
    }
    
    /**
     * Initialisiere das komplette System
     */
    public void initialize() {
        try {
            logger.info("Initializing Currency & Trading System...");
            
            // 1. Lade Konfiguration
            initializeConfig();
            
            // 2. Verbinde zur Datenbank
            initializeDatabase();
            
            // 3. Erstelle Currency Manager
            initializeCurrencyManagers();
            
            // 4. Erstelle Trading UIs
            initializeTradingUIs();
            
            logger.info("Currency & Trading System initialized successfully!");
            
        } catch (Exception e) {
            logger.severe("Failed to initialize Currency System: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Initialisiere Konfiguration
     */
    private void initializeConfig() {
        File configFile = new File(plugin.getDataFolder(), "config/currencies.yml");
        
        // Fallback: Prüfe ob die Datei im alten Pfad noch existiert
        if (!configFile.exists()) {
            File oldFile = new File(plugin.getDataFolder(), "currencies.yml");
            if (oldFile.exists()) {
                configFile = oldFile;
            }
        }

        if (!configFile.exists()) {
            logger.warning("currencies.yml not found, creating default...");
            if (plugin instanceof JavaPlugin javaPlugin) {
                try (var in = javaPlugin.getResource("config/currencies.yml")) {
                    if (in != null) {
                        java.nio.file.Files.copy(in, configFile.toPath());
                        logger.info("Created default currencies.yml from plugin resources.");
                    } else {
                        logger.warning("Default currency resource not found in plugin jar.");
                    }
                } catch (java.io.IOException e) {
                    logger.severe("Failed to create default currencies.yml: " + e.getMessage());
                }
            }
        }

        configManager = new CurrencyConfigManager(configFile, logger);
        configManager.loadConfig();
        configManager.printAllValues();
    }
    
    /**
     * Initialisiere Datenbank
     */
    private void initializeDatabase() {
        File dbFolder = new File(plugin.getDataFolder(), "databases");
        File dbFile = new File(dbFolder, "village.db");
        
        databaseManager = new SQLiteDatabaseManager(dbFile, logger);
        databaseManager.connect();
        databaseManager.initializeTables();
        
        queries = new CurrencyQueries(databaseManager.getConnection(), logger);
        
        logger.info("Database initialized at: " + dbFile.getAbsolutePath());
    }
    
    /**
     * Initialisiere Currency Manager
     */
    private void initializeCurrencyManagers() {
        PlayerCurrencyAPI api = new OpenEcoCurrencyHook(logger);
        if (api instanceof OpenEcoCurrencyHook hook && !hook.isAvailable()) {
            logger.warning("OpenEco-API nicht gefunden - fallback auf InMemory Currency API.");
            globalCurrencyManager = new GlobalCurrencyManager();
        } else {
            globalCurrencyManager = new GlobalCurrencyManager(
                    api,
                    logger,
                    configManager.getGlobalCurrencyName(),
                    configManager.getGlobalCurrencySymbol()
            );
        }
        logger.info("Global currency manager initialized");
        
        // Converter mit Rate aus Config
        double conversionRate = configManager.getConversionRate();
        currencyConverter = new CurrencyConverter(conversionRate, logger);
        logger.info("Currency converter initialized with rate: " + conversionRate);
        
        // Lokale Manager werden pro Dorf erstellt via createVillageManager()
    }
    
    /**
     * Erstelle Dorf-spezifischen Local Currency Manager
     */
    public LocalCurrencyManager createVillageManager(String villageId) {
        return createVillageManager(villageId, villageId);
    }

    private boolean shouldLogCurrencyDebug() {
        if (!(plugin instanceof VillagePlugin villagePlugin)) {
            return false;
        }
        DebugConfigManager debugConfigManager = villagePlugin.getDebugConfigManager();
        return debugConfigManager != null && debugConfigManager.isEnabled("currency");
    }

    public LocalCurrencyManager createVillageManager(String villageId, String villageName) {
        if (!localCurrencyManagers.containsKey(villageId)) {
            String currencyId = configManager.getVillageCurrencyId(villageName);
            String currencyName = configManager.getVillageCurrencyName(villageName);
            String currencySymbol = configManager.getVillageCurrencySymbolTemplate();
            PlayerCurrencyAPI api = globalCurrencyManager != null ? globalCurrencyManager.getCurrencyAPI() : null;
            LocalCurrencyManager manager = new LocalCurrencyManager(
                    villageId,
                    currencyId,
                    currencyName,
                    currencySymbol,
                    logger,
                    api,
                    shouldLogCurrencyDebug()
            );
            manager.setQueries(queries);
            localCurrencyManagers.put(villageId, manager);

            if (api != null && !api.hasCurrency(currencyId)) {
                logger.warning("OpenEco Currency-ID nicht gefunden: " + currencyId + " (Village " + villageName + ")");
            }
        }

        return localCurrencyManagers.get(villageId);
    }

    public void onVillageCreated(Village village) {
        if (village == null) return;
        String villageId = village.getId().toString();
        LocalCurrencyManager manager = createVillageManager(villageId, village.getName());
        double startBalance = configManager.getVillagePlayerStartingAmount();
        for (UUID memberId : village.getMembers().keySet()) {
            try {
                if (globalCurrencyManager != null && globalCurrencyManager.getCurrencyAPI() != null) {
                    String name = Bukkit.getOfflinePlayer(memberId).getName();
                    globalCurrencyManager.getCurrencyAPI().ensureAccount(memberId, name != null ? name : memberId.toString().substring(0, 16));
                }
                manager.initializePlayer(memberId, startBalance);
            } catch (InvalidOperationException e) {
                logger.warning("Initialisierung Dorfwaehrung fehlgeschlagen fuer " + memberId + ": " + e.getMessage());
            }
        }
    }

    public void onVillageDeleted(Village village) {
        if (village == null) return;
        String villageId = village.getId().toString();
        localCurrencyManagers.remove(villageId);
        localTradeUIs.entrySet().removeIf(e -> e.getKey().startsWith(villageId + ":"));
        externalTradeUIs.entrySet().removeIf(e -> e.getKey().startsWith(villageId + ":"));
    }
    
    /**
     * Initialisiere Trading UIs
     */
    private void initializeTradingUIs() {
        // Player-zu-Spieler UI (global)
        playerToPlayerTradeUI = new PlayerToPlayerTradeUI(globalCurrencyManager, logger, ((JavaPlugin) plugin).getDataFolder());
        logger.info("Player-to-Player trading UI initialized");

        // Marketplace Manager
        marketplaceManager = new MarketplaceManager(logger);
        logger.info("Marketplace manager initialized");

        logger.info("Trading UI system initialized (4 scenarios ready)");
    }
    
    /**
     * Erstelle Villager-Local-Trade-UI für Dorf
     */
    public VillagerLocalTradeUI createVillagerLocalTradeUI(String villagerUUID, String villagerName,
                                                          String villageId, 
                                                          com.example.village.model.VillagerInventory inventory) {
        LocalCurrencyManager manager = createVillageManager(villageId);
        
        VillagerLocalTradeUI ui = new VillagerLocalTradeUI(
            villagerUUID,
            villagerName,
            villageId,
            inventory,
            manager,
            logger
        );
        
        String key = villageId + ":" + villagerUUID;
        localTradeUIs.put(key, ui);
        
        logger.info("Created local trade UI for villager: " + villagerName + " in " + villageId);
        return ui;
    }
    
    /**
     * Erstelle Villager-External-Trade-UI für Dorf
     */
    public VillagerExternalTradeUI createVillagerExternalTradeUI(String villagerUUID, String villagerName,
                                                                 String villageId,
                                                                 com.example.village.model.VillagerInventory inventory) {
        LocalCurrencyManager localManager = createVillageManager(villageId);
        
        VillagerExternalTradeUI ui = new VillagerExternalTradeUI(
            villagerUUID,
            villagerName,
            villageId,
            inventory,
            globalCurrencyManager,
            localManager,
            logger
        );
        
        String key = villageId + ":" + villagerUUID;
        externalTradeUIs.put(key, ui);
        
        logger.info("Created external trade UI for villager: " + villagerName + " in " + villageId);
        return ui;
    }
    
    /**
     * Erstelle Marktplatz-Shop
     * Leitet das Dorf automatisch aus der Shop-Location ab.
     */
    public MarketplaceUI createMarketplace(String shopOwnerUUID, String shopName, 
                                          org.bukkit.Location location) {
        LocalCurrencyManager localManager = null;

        // Dorf aus Location bestimmen
        if (location != null && plugin instanceof org.bukkit.plugin.java.JavaPlugin) {
            try {
                com.example.village.VillagePlugin vp =
                        org.bukkit.plugin.java.JavaPlugin.getPlugin(com.example.village.VillagePlugin.class);
                if (vp != null && vp.getVillageManager() != null) {
                    java.util.Optional<com.example.village.model.Village> village =
                            vp.getVillageManager().getVillageAtLocation(location);
                    if (village.isPresent()) {
                        String villageId = village.get().getId().toString();
                        localManager = createVillageManager(villageId, village.get().getName());
                        logger.info("Marketplace " + shopName + " linked to village: " + village.get().getName());
                    }
                }
            } catch (Exception e) {
                logger.warning("Could not determine village from location for marketplace: " + e.getMessage());
            }
        }

        // Fallback auf Default-Dorf wenn kein Dorf in Reichweite
        if (localManager == null) {
            logger.warning("No village found at marketplace location – using default-village fallback for: " + shopName);
            localManager = createVillageManager("default-village");
        }

        MarketplaceUI marketplace = marketplaceManager.createShop(
            shopOwnerUUID,
            shopName,
            location,
            globalCurrencyManager,
            localManager
        );

        logger.info("Created marketplace: " + shopName + " owned by " + shopOwnerUUID);
        return marketplace;
    }
    
    /**
     * Lade Spieler-Daten aus Datenbank beim Beitritt (mit bekannter VillageId)
     */
    public void loadPlayerData(String villageId, String playerUUID) {
        try {
            // Hole lokale Balance
            double localBalance = queries.getPlayerBalance(villageId, playerUUID);
            LocalCurrencyManager manager = createVillageManager(villageId);
            manager.initializePlayer(UUID.fromString(playerUUID), localBalance);
            
            // Hole globale Balance
            double globalBalance = queries.getGlobalPlayerBalance(playerUUID);
            globalCurrencyManager.setBalance(UUID.fromString(playerUUID), globalBalance);
            
            logger.info("Loaded player data: " + playerUUID + " (Village: " + villageId + ")");
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid UUID: " + playerUUID);
        } catch (InvalidOperationException e) {
            logger.severe("Failed to initialize currency data for " + playerUUID + ": " + e.getMessage());
        }
    }

    /**
     * Lade Spieler-Daten beim Beitritt – ermittelt das Dorf automatisch aus dem VillageManager.
     */
    public void loadPlayerData(java.util.UUID playerUUID) {
        try {
            com.example.village.VillagePlugin vp =
                    org.bukkit.plugin.java.JavaPlugin.getPlugin(com.example.village.VillagePlugin.class);
            if (vp != null && vp.getVillageManager() != null) {
                java.util.Optional<com.example.village.model.Village> village =
                        vp.getVillageManager().getPlayerVillage(playerUUID);
                if (village.isPresent()) {
                    loadPlayerData(village.get().getId().toString(), playerUUID.toString());
                    return;
                }
            }
            // Kein Dorf: nur globale Balance laden
            double globalBalance = queries.getGlobalPlayerBalance(playerUUID.toString());
            globalCurrencyManager.setBalance(playerUUID, globalBalance);
        } catch (Exception e) {
            logger.warning("Failed to load player data for " + playerUUID + ": " + e.getMessage());
        }
    }
    
    /**
     * Speichere Spieler-Daten zur Datenbank beim Verlassen
     */
    public void savePlayerData(String villageId, String playerUUID) {
        try {
            // Speichere lokale Balance
            LocalCurrencyManager manager = localCurrencyManagers.get(villageId);
            if (manager != null) {
                double balance = manager.getBalance(UUID.fromString(playerUUID));
                queries.setPlayerBalance(villageId, playerUUID, balance);
            }
            
            // Speichere globale Balance
            double globalBalance = globalCurrencyManager.getBalance(UUID.fromString(playerUUID));
            queries.setGlobalPlayerBalance(playerUUID, globalBalance);
            
            logger.info("Saved player data: " + playerUUID + " (Village: " + villageId + ")");
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid UUID: " + playerUUID);
        }
    }
    
    /**
     * Shutdown: Speichere alle Spieler- und Villager-Daten, trenne Datenbank
     */
    public void shutdown() {
        logger.info("Shutting down Currency & Trading System...");

        if (queries != null) {
            // Speichere alle lokalen Guthaben (Player + Villager) aller Dörfer
            for (Map.Entry<String, LocalCurrencyManager> entry : localCurrencyManagers.entrySet()) {
                String villageId = entry.getKey();
                // LocalCurrencyManager persists balances on every change via queries;
                // this is a belt-and-suspenders flush (no-op when already in sync).
                logger.fine("Currency shutdown flush for village: " + villageId);
            }
        }

        // Trenne Datenbank
        if (databaseManager != null) {
            databaseManager.disconnect();
        }

        logger.info("Currency & Trading System shut down successfully");
    }
    
    // ===== GETTER =====
    
    public GlobalCurrencyManager getGlobalCurrencyManager() {
        return globalCurrencyManager;
    }
    
    public LocalCurrencyManager getLocalCurrencyManager(String villageId) {
        return localCurrencyManagers.get(villageId);
    }
    
    public CurrencyConfigManager getConfigManager() {
        return configManager;
    }
    
    public SQLiteDatabaseManager getDatabaseManager() {
        return databaseManager;
    }
    
    public CurrencyQueries getQueries() {
        return queries;
    }
    
    public PlayerToPlayerTradeUI getPlayerToPlayerTradeUI() {
        return playerToPlayerTradeUI;
    }

    public MarketplaceManager getMarketplaceManager() {
        return marketplaceManager;
    }
}
