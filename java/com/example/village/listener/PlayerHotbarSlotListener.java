package com.example.village.listener;

import com.example.village.service.PlayerNutritionService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Score;

import java.util.Locale;
import java.util.Map;

public final class PlayerHotbarSlotListener implements Listener {

    private final PlayerNutritionService nutritionService;

    public PlayerHotbarSlotListener(PlayerNutritionService nutritionService) {
        this.nutritionService = nutritionService;
    }

    @EventHandler
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!nutritionService.isEnabled()) return;
        if (!nutritionService.isEnabledFor(player)) return;
        if (!nutritionService.isHotbarVisible(player)) return;

        int slot = event.getNewSlot();
        showHotbarSlotDetails(player, slot);
    }

    private void showHotbarSlotDetails(Player player, int slot) {
        if (slot < 0 || slot > 8) return;

        // Create a temporary scoreboard for details (or reuse existing)
        ScoreboardManager mgr = Bukkit.getScoreboardManager();
        if (mgr == null) return;

        Scoreboard board = mgr.getNewScoreboard();
        Objective obj = board.registerNewObjective("hotbar_detail", "dummy", "§b§lNährwert-Details");
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        if (slot < 8) {
            // Show nutrient details
            Map<String, Double> percents = nutritionService.getNutrientPercentages(player);
            java.util.List<String> keys = new java.util.ArrayList<>(percents.keySet());

            if (slot < keys.size()) {
                String key = keys.get(slot);
                double percent = percents.getOrDefault(key, 0.0);
                
                // Get detailed state info
                Map<String, Object> state = nutritionService.getNutrientDetail(player, key);
                
                Score s = obj.getScore("§e" + capitalize(key));
                s.setScore(100);

                Score pct = obj.getScore("§7Auslastung: §f" + String.format("%.1f%%", percent));
                pct.setScore(99);

                if (state != null) {
                    Double curr = (Double) state.get("current");
                    Double cap = (Double) state.get("capacity");
                    if (curr != null && cap != null) {
                        Score val = obj.getScore("§7Wert: §f" + String.format("%.0f / %.0f", curr, cap));
                        val.setScore(98);
                    }

                    Boolean boosted = (Boolean) state.get("boosted");
                    if (boosted != null && boosted) {
                        Score boost = obj.getScore("§a[BOOSTED] +50%");
                        boost.setScore(97);
                    }
                }

                Score decay = obj.getScore("§7Verfallrate: §f1.0 /min");
                decay.setScore(96);
            }
        } else if (slot == 8) {
            // Show active effects
            Score header = obj.getScore("§6Aktive Effekte");
            header.setScore(100);

            Score empty = obj.getScore("§7Keine Effekte");
            empty.setScore(99);
        }

        player.setScoreboard(board);

        // Clear the detail board after 4 seconds (80 ticks)
        Bukkit.getScheduler().scheduleSyncDelayedTask(
                Bukkit.getPluginManager().getPlugin("Village"),
                () -> {
                    if (player.isOnline()) {
                        // Restore to main nutrition scoreboard if visible, else main scoreboard
                        if (nutritionService.isScoreboardVisible(player)) {
                            nutritionService.updatePlayerScoreboardIfVisible(player);
                        } else {
                            player.setScoreboard(mgr.getMainScoreboard());
                        }
                    }
                },
                80L
        );
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }
}
