package com.example.village.listener;

import com.example.village.command.TradeCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

public class PlayerTradeInteractListener implements Listener {

    private final TradeCommand tradeCommand;

    public PlayerTradeInteractListener(TradeCommand tradeCommand) {
        this.tradeCommand = tradeCommand;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!(event.getRightClicked() instanceof Player target)) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }

        event.setCancelled(true);
        if (tradeCommand.hasPendingRequest(target, player)) {
            tradeCommand.acceptRequestFrom(player, target);
            return;
        }
        tradeCommand.sendTradeRequest(player, target);
    }
}
