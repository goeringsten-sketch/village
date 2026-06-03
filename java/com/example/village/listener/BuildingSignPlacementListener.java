package com.example.village.listener;

import com.example.village.config.VillageConfigManager;
import com.example.village.model.PendingBlockSelection;
import com.example.village.model.Village;
import com.example.village.model.VillageBuilding;
import com.example.village.service.BuildingService;
import com.example.village.service.VillageManager;
import com.example.village.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.Optional;

/**
 * Handles automatic sign creation and linking when a building sign is placed.
 * Triggered when a player places a sign and has an active PendingBlockSelection for MOVE_BUILDING_SIGN.
 */
public final class BuildingSignPlacementListener implements Listener {

    private final BuildingService buildingService;
    private final VillageManager villageManager;
    private final VillageConfigManager configManager;

    public BuildingSignPlacementListener(BuildingService buildingService, VillageManager villageManager,
                                       VillageConfigManager configManager) {
        this.buildingService = buildingService;
        this.villageManager = villageManager;
        this.configManager = configManager;
    }

    @EventHandler
    public void onSignPlace(BlockPlaceEvent event) {
        Block placedBlock = event.getBlockPlaced();
        
        // Check if a sign was placed
        if (!placedBlock.getType().toString().contains("SIGN")) {
            return;
        }

        Player player = event.getPlayer();
        
        // Check if player has pending building sign placement
        PendingBlockSelection selection = buildingService.getPendingBlockSelection(player.getUniqueId());
        if (selection == null || selection.getType() != PendingBlockSelection.SelectionType.MOVE_BUILDING_SIGN) {
            return;
        }

        // Get the village and building
        Optional<Village> villageOpt = villageManager.getPlayerVillage(player.getUniqueId());
        if (villageOpt.isEmpty()) {
            return;
        }

        Village village = villageOpt.get();
        VillageBuilding building = village.getBuildings().stream()
                .filter(b -> b.getId().equals(selection.getBuildingId()))
                .findFirst()
                .orElse(null);

        if (building == null) {
            MessageUtil.send(player, configManager.getPrefix(), "&cGebäude nicht gefunden!");
            buildingService.clearPendingBlockSelection(player.getUniqueId());
            return;
        }

        // Update building sign location
        building.setSignLocation(placedBlock.getLocation());
        building.setSignHidden(false);

        // Auto-label the sign
        if (placedBlock.getState() instanceof Sign sign) {
            String displayName = buildingService.getDisplayNameForType(building.getTypeKey());
            sign.getSide(Side.FRONT).line(0,
                    net.kyori.adventure.text.Component.text("§6" + displayName));
            sign.getSide(Side.FRONT).line(1,
                    net.kyori.adventure.text.Component.text(building.isCompleted() ? "§aFertig" : "§cIm Bau"));
            sign.getSide(Side.FRONT).line(2,
                    net.kyori.adventure.text.Component.text(""));
            sign.getSide(Side.FRONT).line(3,
                    net.kyori.adventure.text.Component.text("[Rechtsklick]"));
            sign.setWaxed(true);
            sign.update();
        }

        // Save village
        buildingService.refreshBuildingProtectionAndMarker(village, building);
        villageManager.saveVillage(village);

        // Clear pending selection
        buildingService.clearPendingBlockSelection(player.getUniqueId());

        // Send success message
        MessageUtil.send(player, configManager.getPrefix(),
                "&aGebäude-Schild erfolgreich platziert!");
    }
}
