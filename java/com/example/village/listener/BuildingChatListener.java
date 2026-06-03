package com.example.village.listener;

import com.example.village.config.VillageConfigManager;
import com.example.village.service.BuildingService;
import com.example.village.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import java.util.UUID;

/**
 * Handles chat-based building commands ("bestätigen", "cancel").
 */
public final class BuildingChatListener implements Listener {

    private final VillageConfigManager configManager;
    private final BuildingService buildingService;

    public BuildingChatListener(VillageConfigManager configManager,
                                BuildingService buildingService) {
        this.configManager = configManager;
        this.buildingService = buildingService;
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        String message = event.getMessage().toLowerCase().trim();

        // Check for "bestätigen" command (confirm placement)
        if (message.equals("bestätigen") || message.equals("best\u00e4tigen") || 
            message.equals("bestaetigen") || message.equals("confirm") || message.equals("c")) {
            
            if (buildingService.getPendingPlacementMode(playerId) != null) {
                event.setCancelled(true);
                
                // Execute on sync thread
                org.bukkit.Bukkit.getScheduler().runTask(
                    org.bukkit.Bukkit.getPluginManager().getPlugin("village"),
                    () -> {
                        BuildingService.PlaceResult result = buildingService.confirmBuilding(player);
                        if (result == BuildingService.PlaceResult.SUCCESS) {
                            MessageUtil.send(player, configManager.getPrefix(), 
                                "&aGebäude erfolgreich platziert!");
                        } else {
                            MessageUtil.send(player, configManager.getPrefix(), 
                                "&cFehler beim Platzieren: " + result);
                        }
                    }
                );
                return;
            }
        }

        // Check for "cancel" command (abort placement)
        if (message.equals("cancel") || message.equals("abbrechen") || message.equals("x")) {
            
            if (buildingService.getPendingPlacementMode(playerId) != null) {
                event.setCancelled(true);
                
                buildingService.cancelPlacement(playerId);
                MessageUtil.send(player, configManager.getPrefix(), 
                    "&cPlatzierungsmodus beendet.");
                return;
            }
        }
    }
}
