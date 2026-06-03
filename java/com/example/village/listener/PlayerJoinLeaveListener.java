package com.example.village.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Player;

import com.example.village.service.CurrencyService;

import java.util.*;
import java.util.logging.Logger;

/**
 * Listener für Spieler Join/Quit Events
 * 
 * Verantwortlich für:
 * - Laden von Spieler-Daten aus DB beim Join
 * - Speichern von Spieler-Daten in DB beim Quit
 * - Initialisieren von neuen Spielern
 */
public class PlayerJoinLeaveListener implements Listener {
    
    private final CurrencyService currencyService;
    private final Logger logger;

    public PlayerJoinLeaveListener(CurrencyService currencyService, Logger logger) {
        this.currencyService = Objects.requireNonNull(currencyService);
        this.logger = Objects.requireNonNull(logger);
    }
    
    /**
     * Spieler betritt Server: Lade Daten
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        try {
            Player player = event.getPlayer();
            currencyService.ensureJoinBonus(player.getUniqueId());
            double balance = currencyService.getBalance(player.getUniqueId(), currencyService.getGlobalCurrencyId());
            player.sendMessage("§6Startguthaben geprüft. Globales Guthaben: §a" + balance);
            
        } catch (Exception e) {
            logger.severe("Error loading player data on join: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Spieler verlässt Server: Speichere Daten
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        currencyService.save();
    }
}
