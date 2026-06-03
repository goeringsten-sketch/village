package com.example.village.listener;

import com.example.village.model.PendingBlockSelection;
import com.example.village.model.Village;
import com.example.village.model.VillageBuilding;
import com.example.village.service.BuildingService;
import com.example.village.service.VillageManager;
import com.example.village.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Optional;

/**
 * Handles interactive block selection for building operations like moving signs.
 */
public final class BlockSelectionListener implements Listener {

    private final BuildingService buildingService;
    private final VillageManager villageManager;

    public BlockSelectionListener(BuildingService buildingService, VillageManager villageManager) {
        this.buildingService = buildingService;
        this.villageManager = villageManager;
    }

    @EventHandler
    public void onBlockInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();

        // Schematic tool selection handling
        org.bukkit.inventory.ItemStack item = player.getInventory().getItemInMainHand();
        if (item != null && item.getType() == org.bukkit.Material.BLAZE_ROD && item.hasItemMeta()) {
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            if (meta.hasDisplayName() && "§6Schematic Auswahl-Werkzeug".equals(meta.getDisplayName())) {
                event.setCancelled(true);
                BuildingService.SchematicSelection schematicSel = buildingService.getOrCreateSchematicSelection(player.getUniqueId());
                org.bukkit.block.Block clicked = event.getClickedBlock();
                if (clicked != null) {
                    if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                        schematicSel.setPos1(clicked.getLocation());
                        player.sendMessage("§a[Schematic] Position 1 gesetzt auf: §e" 
                            + clicked.getX() + ", " + clicked.getY() + ", " + clicked.getZ());
                    } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                        if (player.isSneaking()) {
                            schematicSel.setOrigin(clicked.getLocation());
                            player.sendMessage("§a[Schematic] Ursprung (Origin) gesetzt auf: §e" 
                                + clicked.getX() + ", " + clicked.getY() + ", " + clicked.getZ());
                        } else {
                            schematicSel.setPos2(clicked.getLocation());
                            player.sendMessage("§a[Schematic] Position 2 gesetzt auf: §e" 
                                + clicked.getX() + ", " + clicked.getY() + ", " + clicked.getZ());
                        }
                    }
                }
                return;
            }
        }

        PendingBlockSelection selection = buildingService.getPendingBlockSelection(player.getUniqueId());

        if (selection == null) {
            return;
        }

        event.setCancelled(true);

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }

        // Validate the block location
        if (!isValidSignLocation(clickedBlock)) {
            MessageUtil.send(player, "", "&cUngültiger Block! Wähle einen festen Block für das Schild.");
            return;
        }

        // Handle the selection based on type
        BlockFace clickedFace = event.getBlockFace();
        switch (selection.getType()) {
            case MOVE_BUILDING_SIGN -> handleMoveBuildingSign(player, selection, clickedBlock, clickedFace);
        }

        // Clear the pending selection
        buildingService.clearPendingBlockSelection(player.getUniqueId());
    }

    private void handleMoveBuildingSign(Player player, PendingBlockSelection selection, Block clickedBlock, BlockFace clickedFace) {
        Optional<Village> villageOpt = villageManager.getPlayerVillage(player.getUniqueId());
        if (villageOpt.isEmpty()) {
            MessageUtil.send(player, "", "&cDu bist in keinem Dorf.");
            return;
        }

        Village village = villageOpt.get();
        VillageBuilding building = village.getBuildings().stream()
                .filter(b -> b.getId().equals(selection.getBuildingId()))
                .findFirst()
                .orElse(null);

        if (building == null) {
            MessageUtil.send(player, "", "&cGebäude nicht gefunden.");
            return;
        }

        // Remove old sign
        if (building.getSignLocation() != null) {
            buildingService.removeSignAt(building.getSignLocation());
        }

        // Place as wall sign on side click, or standing sign when top-face was clicked.
        Location signLocation = buildingService.placeBuildingInfoSignAtFace(clickedBlock, clickedFace,
                buildingService.getDisplayNameForType(building.getTypeKey()));

        if (signLocation != null) {
            building.setSignLocation(signLocation);
            building.setSignHidden(false);
            buildingService.applyBuildingSignTemplate(player, building, buildingService.getConfigManager());
            buildingService.refreshBuildingProtectionAndMarker(village, building);
            villageManager.saveVillage(village);
            MessageUtil.send(player, "", "&aGebäude-Schild wurde verschoben!");
        } else {
            MessageUtil.send(player, "", "&cFehler beim Platzieren des Schildes.");
        }
    }

    private boolean isValidSignLocation(Block block) {
        Material material = block.getType();
        // Signs can be placed on most solid blocks
        return material.isSolid() &&
               !material.toString().contains("SIGN") &&
               !material.toString().contains("DOOR") &&
               !material.toString().contains("GATE");
    }
}
