package com.example.village.data;

import java.sql.*;
import java.io.File;
import java.util.logging.Logger;

/**
 * Verwaltet SQLite Datenbank für Village-Plugin
 */
public class SQLiteDatabaseManager {
    
    private final File databaseFile;
    private final Logger logger;
    private Connection connection;
    
    public SQLiteDatabaseManager(File databaseFile, Logger logger) {
        this.databaseFile = databaseFile;
        this.logger = logger;
    }
    
    /**
     * Stelle Verbindung her
     */
    public void connect() {
        try {
            // Stelle sicher dass Verzeichnis existiert
            if (!databaseFile.getParentFile().exists()) {
                databaseFile.getParentFile().mkdirs();
            }
            
            String url = "jdbc:sqlite:" + databaseFile.getAbsolutePath();
            connection = DriverManager.getConnection(url);
            
            logger.info("Connected to database: " + databaseFile.getAbsolutePath());
        } catch (SQLException e) {
            logger.severe("Failed to connect to database: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Trenne Verbindung
     */
    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                logger.info("Disconnected from database");
            }
        } catch (SQLException e) {
            logger.severe("Failed to disconnect: " + e.getMessage());
        }
    }
    
    /**
     * Initialisiere alle Tabellen
     */
    public void initializeTables() {
        try {
            // Prüfe ob Tabellen bereits existieren
            DatabaseMetaData metaData = connection.getMetaData();
            ResultSet tables = metaData.getTables(null, null, "%", new String[]{"TABLE"});
            
            boolean hasVillageCurrencies = false;
            while (tables.next()) {
                if (tables.getString("TABLE_NAME").equals("village_currencies")) {
                    hasVillageCurrencies = true;
                    break;
                }
            }
            
            // Erstelle Tabellen falls nicht vorhanden
            if (!hasVillageCurrencies) {
                createTables();
            } else {
                logger.info("Database tables already exist");
            }
            
        } catch (SQLException e) {
            logger.severe("Failed to check tables: " + e.getMessage());
        }
    }
    
    /**
     * Erstelle alle erforderlichen Tabellen
     */
    private void createTables() {
        try (Statement stmt = connection.createStatement()) {
            
            // 1. Village Currencies (Metadata)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS village_currencies (
                    village_id TEXT PRIMARY KEY,
                    currency_name TEXT NOT NULL,
                    currency_symbol TEXT NOT NULL,
                    total_in_circulation REAL DEFAULT 0,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
                """);
            
            // 2. Village Player Balances (Lokale Währung)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS village_player_balances (
                    village_id TEXT NOT NULL,
                    player_uuid TEXT NOT NULL,
                    balance REAL DEFAULT 0,
                    last_updated DATETIME DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (village_id, player_uuid),
                    FOREIGN KEY (village_id) REFERENCES village_currencies(village_id)
                )
                """);
            
            // 3. Village Villager Balances (Lokale Währung)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS village_villager_balances (
                    village_id TEXT NOT NULL,
                    villager_uuid TEXT NOT NULL,
                    balance REAL DEFAULT 0,
                    last_updated DATETIME DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (village_id, villager_uuid),
                    FOREIGN KEY (village_id) REFERENCES village_currencies(village_id)
                )
                """);
            
            // 4. Global Player Balances (Globale Währung)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS global_player_balances (
                    player_uuid TEXT PRIMARY KEY,
                    balance REAL DEFAULT 0,
                    last_updated DATETIME DEFAULT CURRENT_TIMESTAMP
                )
                """);
            
            // 5. Transactions (Audit Trail)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS village_transactions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    village_id TEXT NOT NULL,
                    from_uuid TEXT,
                    to_uuid TEXT,
                    amount REAL NOT NULL,
                    transaction_type TEXT,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    FOREIGN KEY (village_id) REFERENCES village_currencies(village_id)
                )
                """);
            
            // 6. Villager Resources (Was Villager hat)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS village_villager_resources (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    village_id TEXT NOT NULL,
                    villager_uuid TEXT NOT NULL,
                    item_id TEXT NOT NULL,
                    quantity INTEGER DEFAULT 0,
                    FOREIGN KEY (village_id) REFERENCES village_currencies(village_id)
                )
                """);
            
            // 7. Trade Offers (Was Villager verkauft)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS village_trade_offers (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    village_id TEXT NOT NULL,
                    villager_uuid TEXT NOT NULL,
                    item_id TEXT NOT NULL,
                    display_name TEXT NOT NULL,
                    price REAL NOT NULL,
                    currency_type TEXT NOT NULL,
                    stock INTEGER DEFAULT 0,
                    quantity_per_trade INTEGER DEFAULT 1,
                    FOREIGN KEY (village_id) REFERENCES village_currencies(village_id)
                )
                """);
            
            // 8. Marketplace Shops (Spieler-Läden)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS marketplace_shops (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    shop_owner_uuid TEXT NOT NULL,
                    shop_name TEXT NOT NULL,
                    shop_location TEXT,
                    total_sales INTEGER DEFAULT 0,
                    total_revenue REAL DEFAULT 0,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
                """);
            
            // 9. Marketplace Shelf Entries (Shop-Items)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS marketplace_shelf_entries (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    shop_id INTEGER NOT NULL,
                    item_id TEXT NOT NULL,
                    display_name TEXT NOT NULL,
                    price REAL NOT NULL,
                    currency_type TEXT NOT NULL,
                    stock INTEGER DEFAULT 0,
                    FOREIGN KEY (shop_id) REFERENCES marketplace_shops(id)
                )
                """);
            
            // 10. Player Trade Requests (P2P Handels-Anfragen)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS player_trade_requests (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    initiator_uuid TEXT NOT NULL,
                    target_uuid TEXT NOT NULL,
                    status TEXT DEFAULT 'PENDING',
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
                )
                """);
            
            // Erstelle Indizes für Performance
            createIndexes();
            
            logger.info("Database tables created successfully");
            
        } catch (SQLException e) {
            logger.severe("Failed to create tables: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Erstelle Indizes für häufige Queries
     */
    private void createIndexes() {
        try (Statement stmt = connection.createStatement()) {
            
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_village_player ON village_player_balances(village_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_village_villager ON village_villager_balances(village_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_global_player ON global_player_balances(player_uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_transactions_village ON village_transactions(village_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_offers_village ON village_trade_offers(village_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_offers_villager ON village_trade_offers(villager_uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_shops_owner ON marketplace_shops(shop_owner_uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_shelf_shop ON marketplace_shelf_entries(shop_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_trades_status ON player_trade_requests(status)");
            
            logger.info("Database indexes created successfully");
            
        } catch (SQLException e) {
            logger.warning("Failed to create indexes: " + e.getMessage());
        }
    }
    
    /**
     * Prüfe ob Datenbankverbindung aktiv ist
     */
    public boolean isConnected() {
        try {
            return connection != null && !connection.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }
    
    /**
     * Hole Datenbankverbindung
     */
    public Connection getConnection() {
        return connection;
    }
}
