package com.example.village.command;

import com.example.village.config.VillageConfigManager;
import com.example.village.service.PlayerNutritionService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class HungerCommand implements CommandExecutor {

    private final PlayerNutritionService nutritionService;
    private final VillageConfigManager configManager;

    public HungerCommand(PlayerNutritionService nutritionService, VillageConfigManager configManager) {
        this.nutritionService = nutritionService;
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Dieser Befehl kann nur von Spielern benutzt werden.");
            return true;
        }
        Player player = (Player) sender;
        if (!nutritionService.isEnabledFor(player)) {
            player.sendMessage("Das Nährwert-System ist für dich nicht aktiviert.");
            return true;
        }

        // If an argument is provided, treat it as refresh interval in seconds
        if (args != null && args.length > 0) {
            try {
                int seconds = Integer.parseInt(args[0]);
                if (seconds < 0) seconds = 0;
                // Ensure scoreboard visible when setting interval
                if (!nutritionService.isScoreboardVisible(player)) {
                    nutritionService.togglePlayerScoreboard(player);
                }
                nutritionService.setPlayerRefreshInterval(player, seconds);
                if (seconds == 0) {
                    player.sendMessage("Auto-Refresh für Nährwerte deaktiviert (0).");
                } else {
                    player.sendMessage("Auto-Refresh für Nährwerte auf " + seconds + "s gesetzt.");
                }
                return true;
            } catch (NumberFormatException ex) {
                player.sendMessage("Ungültige Zeitangabe. Nutze: /hunger <sekunden> oder /hunger zum Ein-/Ausschalten.");
                return true;
            }
        }

        // Command options: no-args toggle scoreboard; 'hotbar' toggles hotbar visualization; '<seconds>' sets interval
        if (args != null && args.length > 0 && "hotbar".equalsIgnoreCase(args[0])) {
            nutritionService.togglePlayerHotbar(player);
            if (nutritionService.isHotbarVisible(player)) {
                player.sendMessage("Hotbar-Visualisierung aktiviert.");
            } else {
                player.sendMessage("Hotbar-Visualisierung deaktiviert und Hotbar wiederhergestellt.");
            }
            return true;
        }

        // No args: toggle scoreboard visibility; toggling off clears auto-refresh
        boolean enabled = nutritionService.togglePlayerScoreboard(player);
        if (enabled) {
            // set default interval if none set
            int def = nutritionService.getDefaultRefreshIntervalSeconds();
            nutritionService.setPlayerRefreshInterval(player, def);
            player.sendMessage("Scoreboard für Nährwerte aktiviert. Auto-Refresh: " + def + "s.");
        } else {
            nutritionService.clearPlayerRefreshInterval(player);
            player.sendMessage("Scoreboard für Nährwerte deaktiviert und Auto-Refresh entfernt.");
        }
        return true;
    }
}
