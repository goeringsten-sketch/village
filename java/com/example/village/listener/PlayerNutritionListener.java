package com.example.village.listener;

import com.example.village.service.PlayerNutritionService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.inventory.ItemStack;

public final class PlayerNutritionListener implements Listener {

    private final PlayerNutritionService nutritionService;

    public PlayerNutritionListener(PlayerNutritionService nutritionService) {
        this.nutritionService = nutritionService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        nutritionService.ensureState(player);
    }

    @EventHandler
    public void onEat(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (!nutritionService.isEnabledFor(player)) return;

        // Prevent client-side hunger animation by cancelling the consume event
        event.setCancelled(true);

        // Remove a single item from the player's hand/inventory
        ItemStack consumed = event.getItem().clone();
        consumed.setAmount(1);
        player.getInventory().removeItem(consumed);

        Material foodMaterial = event.getItem().getType();
        nutritionService.applyFood(player, foodMaterial);
        // Update scoreboard if visible
        nutritionService.updatePlayerScoreboardIfVisible(player);
    }

    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (!nutritionService.isEnabledFor(player)) return;
        // Cancel any vanilla food level changes for players using the custom nutrition system
        event.setCancelled(true);
    }
}
