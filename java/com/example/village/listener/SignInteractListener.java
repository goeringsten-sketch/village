package com.example.village.listener;

import com.example.village.VillagePlugin;
import com.example.village.gui.GuiManager;
import com.example.village.model.Village;
import com.example.village.model.VillageBuilding;
import com.example.village.service.VillageManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Optional;

public final class SignInteractListener implements Listener {

    private final VillagePlugin plugin;
    private final VillageManager villageManager;
    private final GuiManager guiManager;

    public SignInteractListener(VillagePlugin plugin, VillageManager villageManager, GuiManager guiManager) {
        this.plugin = plugin;
        this.villageManager = villageManager;
        this.guiManager = guiManager;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        
        Block block = event.getClickedBlock();
        if (block == null || !block.getType().toString().contains("SIGN")) return;

        Player player = event.getPlayer();
        Optional<Village> villageOpt = villageManager.getPlayerVillage(player.getUniqueId());
        if (!villageOpt.isPresent()) return;

        Village village = villageOpt.get();

        // Check if this is a building sign
        for (VillageBuilding building : village.getBuildings()) {
            if (building.getSignLocation() != null && building.getSignLocation().equals(block.getLocation())) {
                if (building.isCompleted()) {
                    // Open building options for this specific building
                    guiManager.openBuildingDetailGui(player, village, building.getId());
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }
}
