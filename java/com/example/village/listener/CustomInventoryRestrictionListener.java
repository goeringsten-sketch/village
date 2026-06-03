package com.example.village.listener;

import com.example.village.gui.BuildingMenuGui;
import com.example.village.gui.VillageMenuHolder;
import com.example.village.gui.VillagerInventoryGui;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;

public final class CustomInventoryRestrictionListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        if (player.getGameMode() != GameMode.CREATIVE) {
            return;
        }

        if (!isCustomVillageInventory(event.getView())) {
            return;
        }

        event.setCancelled(true);
        player.closeInventory();
        player.sendMessage("§cCustom-Inventare sind im Kreativmodus nicht erlaubt.");
    }

    private boolean isCustomVillageInventory(InventoryView view) {
        if (view == null) {
            return false;
        }

        InventoryHolder holder = view.getTopInventory().getHolder();
        if (holder instanceof VillageMenuHolder
                || holder instanceof BuildingMenuGui
                || holder instanceof VillagerInventoryGui) {
            return true;
        }

        String title = view.getTitle();
        if (title == null) {
            return false;
        }

        return title.equals("§b§lQuests")
                || title.startsWith("§6Job wählen: ")
                || title.endsWith(" - Skills")
                || title.endsWith(" - Menü");
    }
}
