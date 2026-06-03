package com.example.village.data;

import java.sql.*;
import java.util.*;
import java.util.logging.Logger;

/**
 * Datenbankabfrage-Interface für Currency-Operationen
 * 
 * Diese Klasse kapseltt alle SQL-Queries für:
 * - Spieler-Guthaben laden/speichern
 * - Villager-Ressourcen laden/speichern
 * - Transaktions-Historie
 */
public class CurrencyQueries {
    
    private final Connection connection;
    private final Logger logger;
    
    public CurrencyQueries(Connection connection, Logger logger) {
        this.connection = Objects.requireNonNull(connection);
        this.logger = Objects.requireNonNull(logger);
    }
    
    // ===== VILLAGE CURRENCY =====
    
    /**
     * Erstelle neue Dorf-Währung Eintrag
     */
    public void createVillageCurrency(String villageId, String currencyName, String currencySymbol) {
        String sql = "INSERT OR REPLACE INTO village_currencies (village_id, currency_name, currency_symbol) VALUES (?, ?, ?)";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, villageId);
            pstmt.setString(2, currencyName);
            pstmt.setString(3, currencySymbol);
            pstmt.executeUpdate();
            
            logger.fine("Created village currency: " + villageId);
        } catch (SQLException e) {
            logger.warning("Failed to create village currency: " + e.getMessage());
        }
    }
    
    // ===== VILLAGE PLAYER BALANCES (Lokale Währung) =====
    
    /**
     * Hole Spieler-Guthaben für Dorf
     */
    public double getPlayerBalance(String villageId, String playerUUID) {
        String sql = "SELECT balance FROM village_player_balances WHERE village_id = ? AND player_uuid = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, villageId);
            pstmt.setString(2, playerUUID);
            
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getDouble("balance");
            }
        } catch (SQLException e) {
            logger.warning("Failed to get player balance: " + e.getMessage());
        }
        
        return 0.0;
    }
    
    /**
     * Setze Spieler-Guthaben
     */
    public void setPlayerBalance(String villageId, String playerUUID, double balance) {
        String sql = "INSERT OR REPLACE INTO village_player_balances (village_id, player_uuid, balance) VALUES (?, ?, ?)";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, villageId);
            pstmt.setString(2, playerUUID);
            pstmt.setDouble(3, balance);
            pstmt.executeUpdate();
            
            logger.fine("Set player balance: " + playerUUID + " = " + balance);
        } catch (SQLException e) {
            logger.warning("Failed to set player balance: " + e.getMessage());
        }
    }
    
    /**
     * Addiere zu Spieler-Guthaben
     */
    public void addToPlayerBalance(String villageId, String playerUUID, double amount) {
        String sql = "UPDATE village_player_balances SET balance = balance + ? WHERE village_id = ? AND player_uuid = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setDouble(1, amount);
            pstmt.setString(2, villageId);
            pstmt.setString(3, playerUUID);
            pstmt.executeUpdate();
            
            logger.fine("Added to player balance: " + playerUUID + " += " + amount);
        } catch (SQLException e) {
            logger.warning("Failed to add to player balance: " + e.getMessage());
        }
    }
    
    // ===== VILLAGE VILLAGER BALANCES (Lokale Währung) =====
    
    /**
     * Hole Villager-Guthaben
     */
    public double getVillagerBalance(String villageId, String villagerUUID) {
        String sql = "SELECT balance FROM village_villager_balances WHERE village_id = ? AND villager_uuid = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, villageId);
            pstmt.setString(2, villagerUUID);
            
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getDouble("balance");
            }
        } catch (SQLException e) {
            logger.warning("Failed to get villager balance: " + e.getMessage());
        }
        
        return 0.0;
    }
    
    /**
     * Setze Villager-Guthaben
     */
    public void setVillagerBalance(String villageId, String villagerUUID, double balance) {
        String sql = "INSERT OR REPLACE INTO village_villager_balances (village_id, villager_uuid, balance) VALUES (?, ?, ?)";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, villageId);
            pstmt.setString(2, villagerUUID);
            pstmt.setDouble(3, balance);
            pstmt.executeUpdate();
            
            logger.fine("Set villager balance: " + villagerUUID + " = " + balance);
        } catch (SQLException e) {
            logger.warning("Failed to set villager balance: " + e.getMessage());
        }
    }
    
    // ===== GLOBAL PLAYER BALANCES =====
    
    /**
     * Hole globales Spieler-Guthaben
     */
    public double getGlobalPlayerBalance(String playerUUID) {
        String sql = "SELECT balance FROM global_player_balances WHERE player_uuid = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerUUID);
            
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getDouble("balance");
            }
        } catch (SQLException e) {
            logger.warning("Failed to get global balance: " + e.getMessage());
        }
        
        return 0.0;
    }
    
    /**
     * Setze globales Spieler-Guthaben
     */
    public void setGlobalPlayerBalance(String playerUUID, double balance) {
        String sql = "INSERT OR REPLACE INTO global_player_balances (player_uuid, balance) VALUES (?, ?)";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerUUID);
            pstmt.setDouble(2, balance);
            pstmt.executeUpdate();
            
            logger.fine("Set global balance: " + playerUUID + " = " + balance);
        } catch (SQLException e) {
            logger.warning("Failed to set global balance: " + e.getMessage());
        }
    }
    
    /**
     * Addiere zu globalem Guthaben
     */
    public void addToGlobalBalance(String playerUUID, double amount) {
        String sql = "UPDATE global_player_balances SET balance = balance + ? WHERE player_uuid = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setDouble(1, amount);
            pstmt.setString(2, playerUUID);
            int updated = pstmt.executeUpdate();
            
            // Falls Spieler nicht existiert, erstelle Eintrag
            if (updated == 0) {
                setGlobalPlayerBalance(playerUUID, amount);
            }
            
            logger.fine("Added to global balance: " + playerUUID + " += " + amount);
        } catch (SQLException e) {
            logger.warning("Failed to add to global balance: " + e.getMessage());
        }
    }
    
    // ===== TRANSACTIONS (Audit Trail) =====
    
    /**
     * Schreibe Transaktions-Datensatz
     */
    public void logTransaction(String villageId, String fromUUID, String toUUID, double amount, String type) {
        String sql = "INSERT INTO village_transactions (village_id, from_uuid, to_uuid, amount, transaction_type) VALUES (?, ?, ?, ?, ?)";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, villageId);
            pstmt.setString(2, fromUUID);
            pstmt.setString(3, toUUID);
            pstmt.setDouble(4, amount);
            pstmt.setString(5, type);
            pstmt.executeUpdate();
            
            logger.fine("Logged transaction: " + fromUUID + " -> " + toUUID + " (" + type + ")");
        } catch (SQLException e) {
            logger.warning("Failed to log transaction: " + e.getMessage());
        }
    }
    
    /**
     * Hole alle Transaktionen für Dorf
     */
    public List<Map<String, Object>> getTransactions(String villageId, int limit) {
        String sql = "SELECT * FROM village_transactions WHERE village_id = ? ORDER BY created_at DESC LIMIT ?";
        List<Map<String, Object>> transactions = new ArrayList<>();
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, villageId);
            pstmt.setInt(2, limit);
            
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Map<String, Object> trans = new HashMap<>();
                trans.put("id", rs.getInt("id"));
                trans.put("from_uuid", rs.getString("from_uuid"));
                trans.put("to_uuid", rs.getString("to_uuid"));
                trans.put("amount", rs.getDouble("amount"));
                trans.put("type", rs.getString("transaction_type"));
                trans.put("created_at", rs.getString("created_at"));
                transactions.add(trans);
            }
        } catch (SQLException e) {
            logger.warning("Failed to get transactions: " + e.getMessage());
        }
        
        return transactions;
    }
    
    // ===== HELPER =====
    
    /**
     * Prüfe ob Spieler im Dorf existiert
     */
    public boolean playerExists(String villageId, String playerUUID) {
        String sql = "SELECT 1 FROM village_player_balances WHERE village_id = ? AND player_uuid = ? LIMIT 1";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, villageId);
            pstmt.setString(2, playerUUID);
            
            ResultSet rs = pstmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            logger.warning("Failed to check player existence: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Zähle alle Transaktionen in Dorf
     */
    public int countTransactions(String villageId) {
        String sql = "SELECT COUNT(*) as count FROM village_transactions WHERE village_id = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, villageId);
            
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("count");
            }
        } catch (SQLException e) {
            logger.warning("Failed to count transactions: " + e.getMessage());
        }
        
        return 0;
    }
}
