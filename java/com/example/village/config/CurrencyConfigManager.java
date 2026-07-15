package com.example.village.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

/**
 * Parst und managed die currencies.yml Konfiguration
 * 
 * Struktur:
 * global:
 *   name: "Goldmünze"
 *   symbol: "⊕"
 *   starting_amount: 100
 *   conversion_rate: 1.25
 *   
 * villages:
 *   template_name: "{VILLAGE_NAME}-Taler"
 *   template_symbol: "Ɖ"
 *   player_starting: 100
 *   villager_starting: 50
 */
public class CurrencyConfigManager {
    
    private final File configFile;
    private FileConfiguration config;
    private final Logger logger;
    
    public CurrencyConfigManager(File configFile, Logger logger) {
        this.configFile = configFile;
        this.logger = logger;
    }
    
    /**
     * Lade Konfiguration aus Datei
     */
    public void loadConfig() {
        if (!configFile.exists()) {
            logger.warning("Currency config not found: " + configFile.getAbsolutePath());
        }
        
        config = YamlConfiguration.loadConfiguration(configFile);
    }
    
    /**
     * Speichere Konfiguration
     */
    public void saveConfig() {
        if (config != null) {
            try {
                config.save(configFile);
                logger.info("Currency config saved: " + configFile.getAbsolutePath());
            } catch (IOException e) {
                logger.severe("Failed to save currency config: " + e.getMessage());
            }
        }
    }
    
    // ====== GLOBALE WÄHRUNG ======
    
    /**
     * Hole globale Währungs-Name
     */
    public String getGlobalCurrencyName() {
        return config.getString("global.name", "Gold");
    }

    public String getGlobalCurrencyId() {
        return config.getString("global.id", "global");
    }
    
    /**
     * Hole globale Währungs-Symbol
     */
    public String getGlobalCurrencySymbol() {
        return config.getString("global.symbol", "⊕");
    }
    
    /**
     * Hole Start-Guthaben für globale Währung
     */
    public double getGlobalStartingAmount() {
        return config.getDouble("global.startingAmount", 100.0);
    }
    
    /**
     * Hole Umwandlungs-Rate (lokal→global)
     */
    public double getConversionRate() {
        return config.getDouble("global.conversion.rate", 1.25);
    }
    
    // ====== DORF-WÄHRUNG ======
    
    /**
     * Hole Dorf-Währungs-Namen Template
     */
    public String getVillageCurrencyNameTemplate() {
        return config.getString("villages.default.nameTemplate", "{VILLAGE_NAME}-Taler");
    }
    
    /**
     * Hole Dorf-Währungs-Symbol Template
     */
    public String getVillageCurrencySymbolTemplate() {
        return config.getString("villages.default.symbol", "Ɖ");
    }
    
    /**
     * Generiere Dorf-Währungs-Namen
     */
    public String getVillageCurrencyName(String villageName) {
        String template = getVillageCurrencyNameTemplate();
        return template.replace("{VILLAGE_NAME}", villageName);
    }

    public String getVillageCurrencyId(String villageName) {
        String template = config.getString("villages.default.idTemplate", "{VILLAGE_ID}");
        String normalizedName = villageName == null ? "" : villageName.toLowerCase(Locale.ROOT).replace(" ", "_");
        return template
                .replace("{VILLAGE_ID}", normalizedName)
                .replace("{VILLAGE_NAME}", normalizedName);
    }
    
    /**
     * Hole Start-Guthaben für Spieler in lokaler Währung
     */
    public double getVillagePlayerStartingAmount() {
        return config.getDouble("villages.default.initialPlayersBalance", 100.0);
    }
    
    /**
     * Hole Start-Guthaben für Villager in lokaler Währung
     */
    public double getVillageVillagerStartingAmount() {
        return config.getDouble("villages.default.initialVillagerBalance", 50.0);
    }
    
    // ====== HANDELS-REGELN ======
    
    /**
     * Hole Liste der Jobs die lokal handeln dürfen
     */
    public List<String> getTradingJobs() {
        List<String> jobs = config.getStringList("villages.default.tradersJobs");
        if (jobs.isEmpty()) {
            jobs = List.of("FARMER", "MERCHANT", "BLACKSMITH", "SCHOLAR", "CARTOGRAPHER", "BAKER", "MINER", "LUMBERJACK", "CARPENTER", "MASON", "BEEKEEPER", "BREWER", "FISHER", "HUNTER", "MEDIC", "COURIER", "GUARD");
        }
        return jobs;
    }
    
    /**
     * Hole max Lager-Grösse für Villager
     */
    public int getVillagerStorageSlots() {
        return config.getInt("villages.default.villagerStorageSlots", 27);
    }
    
    /**
     * Hole Produktions-Bonus für Upgrades
     */
    public double getProductionBonus() {
        return config.getDouble("villages.default.earnPerProductionItem", 5.0);
    }
    
    /**
     * Prüfe ob Spieler-zu-Spieler Handel aktiviert ist
     */
    public boolean isPlayerToPlayerTradingEnabled() {
        return config.getBoolean("trading.playerTrades.enabled", true);
    }

    public int getPlayerTradeRequestTimeoutSeconds() {
        return config.getInt("trading.playerTrades.requestTimeout", 30);
    }

    public int getPlayerTradeMaxPendingRequests() {
        return config.getInt("trading.playerTrades.maxPendingRequests", 10);
    }

    public double getPlayerTradeMaxDistance() {
        return config.getDouble("trading.playerTrades.maxDistance", 8.0);
    }
    
    /**
     * Prüfe ob Marktplatz aktiviert ist
     */
    public boolean isMarketplaceEnabled() {
        return config.getBoolean("trading.marketplace.enabled", true);
    }
    
    /**
     * Hole max Shop-Größe
     */
    public int getMaxShopSize() {
        return config.getInt("trading.maxPlayerShops", 54);
    }
    
    /**
     * Prüfe ob Spieler-Benachrichtigungen aktiviert sind
     */
    public boolean isPlayerNotificationsEnabled() {
        return config.getBoolean("trading.player_notifications", true);
    }
    
    // ====== DEBUG ======
    
    /**
     * Drucke all Config-Werte aus (für Debugging)
     */
    public void printAllValues() {
        logger.info("=== CURRENCY CONFIG ===");
        logger.info("Global currency: " + getGlobalCurrencyName() + " (" + getGlobalCurrencySymbol() + ")");
        logger.info("Global starting: " + getGlobalStartingAmount());
        logger.info("Conversion rate: " + getConversionRate());
        logger.info("Village template: " + getVillageCurrencyNameTemplate());
        logger.info("Village starting (player): " + getVillagePlayerStartingAmount());
        logger.info("Village starting (villager): " + getVillageVillagerStartingAmount());
        logger.info("Trading jobs: " + getTradingJobs());
        logger.info("======================");
    }
}
