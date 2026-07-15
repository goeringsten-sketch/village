package com.example.village.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.Bukkit;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Player;

import com.example.village.service.CurrencyService;
import com.example.village.util.MessageUtil;

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
    private final com.example.village.VillagePlugin plugin = com.example.village.VillagePlugin.getPlugin(com.example.village.VillagePlugin.class);

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
            player.sendMessage(MessageUtil.color(plugin.getConfigManager().text("messages.join-balance", "§6Startguthaben geprüft. Globales Guthaben: §a%balance%").replace("%balance%", String.valueOf(balance))));

            // Check for newly accessible buildings/upgrades since last login
            // (catches permission grants made while the player was offline)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                com.example.village.gui.GuiManager gm = plugin.getGuiManager();
                com.example.village.service.VillageManager vm = plugin.getVillageManager();
                com.example.village.service.BuildingService bs = plugin.getBuildingService();
                if (gm == null || vm == null || bs == null) return;
                vm.getPlayerVillage(player.getUniqueId()).ifPresent(village ->
                        gm.checkAndMarkNewlyAccessible(player, village));
            }, 20L); // 1 s delay so permissions are fully loaded
            
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
