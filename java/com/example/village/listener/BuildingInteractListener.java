package com.example.village.listener;

import com.example.village.config.VillageConfigManager;
import com.example.village.service.BuildingService;
import com.example.village.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;

/**
 * Handles interactions with building signs for preview, cancel, and completion.
 * Also protects building signs from being destroyed.
 */
public final class BuildingInteractListener implements Listener {

    private final VillageConfigManager configManager;
    private final BuildingService buildingService;

    public BuildingInteractListener(VillageConfigManager configManager,
                                    BuildingService buildingService) {
        this.configManager = configManager;
        this.buildingService = buildingService;
    }

    @EventHandler
    public void onSignInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!(block.getState() instanceof Sign sign)) return;

        Player player = event.getPlayer();
        // If player is currently in a pending placement mode or has a pending confirmation,
        // do not open the regular building menu (prevents duplicate messages).
        if (!buildingService.isInBuildingSession(player.getUniqueId())
            || buildingService.getPendingPlacementMode(player.getUniqueId()) != null
            || buildingService.hasPendingConfirmation(player.getUniqueId())) return;

        String line = sign.getSide(org.bukkit.block.sign.Side.FRONT).line(0).toString();
        if (line == null) return;

        // Remove formatting codes for comparison
        String plainLine = line.replaceAll("(?i)&[0-9a-fk-or]", "")
                .replaceAll("§[0-9a-fk-or]", "")
                .trim().toLowerCase();

        // Check if this is a building menu sign (contains building name)
        if (isBuildingSign(plainLine)) {
            openBuildingMenu(player, event);
            event.setCancelled(true);
        }
    }

    /**
     * Checks if a sign is a building menu sign by checking the first line.
     */
    private boolean isBuildingSign(String plainLine) {
        // Building signs have their name on the first line
        // They should not contain standard action keywords
        return !plainLine.contains("village") && !plainLine.isEmpty();
    }

    /**
     * Opens the building menu when player right-clicks the sign.
     */
    private void openBuildingMenu(Player player, PlayerInteractEvent event) {
        buildingService.openBuildingMenu(player);
        event.setCancelled(true);
    }

    /**
     * Protects building signs from being destroyed (even with OP permissions).
     */
    @EventHandler
    public void onSignBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (!(block.getState() instanceof Sign sign)) return;

        String line = sign.getSide(org.bukkit.block.sign.Side.FRONT).line(0).toString();
        if (line == null) return;

        // Remove formatting codes for comparison
        String plainLine = line.replaceAll("(?i)&[0-9a-fk-or]", "")
                .replaceAll("§[0-9a-fk-or]", "")
                .trim().toLowerCase();

        // Check if this is a building sign (building signs have content but not keywords)
        if (isBuildingSign(plainLine)) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            MessageUtil.send(player, configManager.getPrefix(),
                    "&cDieses Schild ist beschützt und kann nicht zerstört werden!");
        }
    }

    @EventHandler
    public void onVisualizationBlockClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        Player player = event.getPlayer();
        if (!buildingService.isInBuildingSession(player.getUniqueId())) return;

        BuildingService.BuildingSession session = buildingService.getSession(player.getUniqueId());
        if (session == null || !session.isPreviewVisible()) return;

        // Check if this block is in our visualization set
        String locStr = block.getX() + ":" + block.getY() + ":" + block.getZ() + ":" + block.getWorld().getName();
        if (session.getVisualizedBlockLocations().contains(locStr)) {
            // Remove the block from visualization and show the actual world block
            player.sendBlockChange(block.getLocation(), block.getBlockData());
            session.getVisualizedBlockLocations().remove(locStr);
            event.setCancelled(true);  // Prevent default interaction
        }
    }

    @EventHandler
    public void onPendingPlacementModeClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        // Minecraft fires PlayerInteractEvent twice (main hand + off-hand) — only process main hand
        if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        BuildingService.PendingPlacementMode mode = buildingService.getPendingPlacementMode(player.getUniqueId());

        if (mode == null) return;

        event.setCancelled(true);

        if (buildingService.hasPendingConfirmation(player.getUniqueId())) {
            buildingService.cancelBuildingPreview(player);
        }

        Block block = event.getClickedBlock();
        if (block == null) return;

        Location origin = block.getLocation();
        if (!buildingService.isPathBuilding(mode.getBuildingTypeKey())
                && !buildingService.isBlockCheckBuilding(mode.getBuildingTypeKey())) {
            origin = origin.clone().add(0, 1, 0);
        }

        if (buildingService.isPathBuilding(mode.getBuildingTypeKey())) {
            boolean started = buildingService.startPathPlacement(
                    player,
                    mode.getVillage(),
                    mode.getBuildingTypeKey(),
                    origin,
                    mode.getDirection()
            );
            if (started) {
                buildingService.clearPlacementMode(player.getUniqueId());
            } else {
                MessageUtil.send(player, configManager.getPrefix(),
                        "&cPfad konnte nicht gestartet werden. Versuche einen anderen Block.");
            }
            return;
        }

        BuildingService.PlaceResult previewResult = buildingService.previewBuilding(
                player,
                mode.getVillage(),
                mode.getBuildingTypeKey(),
                origin,
                mode.getDirection(),
                mode.getSchematicName()
        );

        if (previewResult != BuildingService.PlaceResult.SUCCESS) {
            String failureMessage;
            switch (previewResult) {
                case OUTSIDE_BORDER -> failureMessage = "&cDie vorgeschlagene Position liegt außerhalb des Dorfgebiets.";
                case BUILDING_TOO_FAR -> failureMessage = "&cDas Gebäude liegt außerhalb der maximalen Bauentfernung.";
                case ON_START_BORDER -> failureMessage = "&cDas Gebäude darf nicht auf dem Dorfbrunnen oder an einer bestehenden Grenze platziert werden.";
                case PARTIAL_OUTSIDE_BORDER -> failureMessage = "&cDas Gebäude befindet sich teilweise auf einer bestehenden Dorfgrenze.";
                case BORDER_EXPANSION_NEEDED -> failureMessage = "&cFür diesen Platz muss die Dorfgrenze zuerst erweitert werden.";
                case OVERLAPS_BUILDING -> {
                    String diag = buildingService.getLastPlacementDiagnostic(player.getUniqueId());
                    failureMessage = "&cDas Gebäude überschneidet sich mit einem bestehenden Gebäude oder Bauplatz.";
                    if (diag != null && !diag.isBlank()) {
                        failureMessage += " &7(" + diag + ")";
                    }
                }
                case INSIDE_DEFAULT_BORDER -> failureMessage = "&cDas Gebäude darf nicht vollständig in der Standard-Grenze (ID 0) liegen, wenn bereits Erweiterungen existieren.";
                case BLOCK_CHECK_VALIDATION_FAILED -> failureMessage = "&cDie Bauteilprüfung ist fehlgeschlagen. Bitte überprüfe den Bereich und versuche es erneut.";
                case SCHEMATIC_NOT_FOUND -> failureMessage = "&cSchematic nicht gefunden oder ungültig. Bitte wähle eine vorhandene Schematic aus.";
                case LEVEL_TOO_LOW -> failureMessage = configManager.message("building-village-level-too-low");
                case ALREADY_BUILDING -> failureMessage = "&cDu hast bereits eine laufende Gebäudeplatzierung.";
                case TOO_MANY_INSTANCES -> failureMessage = configManager.message("building-max-instances-reached");
                default -> failureMessage = "&cVorschau konnte nicht angezeigt werden. Versuche einen anderen Block.";
            }
            // Show error with an [ABBRECHEN] button so the player can cancel without waiting
            MessageUtil.sendClickableCommand(player, configManager.getPrefix(),
                    failureMessage + " &8[&c&lABBRECHEN&8]",
                    "/village cancel");
            return;
        }

        MessageUtil.sendYesNoAbortRunCommand(
                player,
                configManager.getPrefix(),
                "&7Gebäude platzieren?",
                "/village buildconfirm ja",
                "/village buildconfirm nein",
                "/village cancel"
        );
    }

    // NOTE: Confirmation is handled via chat input ("ja"/"abbrechen") in ChatInputListener.
}
