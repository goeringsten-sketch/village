package com.example.village.trading;

import org.bukkit.entity.Player;
import com.example.village.model.TradeOffer;
import java.util.Optional;

/**
 * Base interface für Trading-UIs
 * Alle Trading-Szenarien implementieren dieses Interface
 */
public interface TradingUI {
    
    /**
     * Öffne das Trading-Interface für einen Spieler
     */
    void openTradingUI(Player player);
    
    /**
     * Verarbeite einen Trade (Kauf oder Verkauf)
     */
    TradeResult processTrade(Player buyer, TradeOffer offer, int quantity);
    
    /**
     * Hole das Angebot für ein Slot-Index in der offenen GUI
     */
    Optional<TradeOffer> getOfferForSlot(int slot);
    
    /**
     * Formatiere einen Währungs-Betrag zur Anzeige
     */
    default String formatPrice(double price, String symbol) {
        return String.format("%s %.0f", symbol, price);
    }
}
