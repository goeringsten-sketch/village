package com.example.village.listener;

import com.example.village.config.VillageConfigManager;
import com.example.village.gui.GuiManager;
import com.example.village.hook.VaultHook;
import com.example.village.model.Village;
import com.example.village.service.VillageManager;
import com.example.village.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Optional;

/**
 * Listens for right-click on bells to open the village founding GUI.
 * Validates the well structure: Water -> Air (max configured) -> Bell.
 */
public final class BellInteractListener implements Listener {

    private final VillageConfigManager configManager;
    private final VillageManager villageManager;
    private final GuiManager guiManager;
    private final GuiClickListener guiClickListener;

    public BellInteractListener(VillageConfigManager configManager,
                                VillageManager villageManager,
                                GuiManager guiManager,
                                GuiClickListener guiClickListener) {
        this.configManager = configManager;
        this.villageManager = villageManager;
        this.guiManager = guiManager;
        this.guiClickListener = guiClickListener;
    }

    @EventHandler
    public void onBellInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null || clickedBlock.getType() != Material.BELL) return;

        Player player = event.getPlayer();
        Location bellLocation = clickedBlock.getLocation();

        // Check if this bell is already part of a village
        Optional<Village> existingVillage = villageManager.getVillageByBell(bellLocation);
        if (existingVillage.isPresent()) {
            Village village = existingVillage.get();
            if (player.hasPermission("village.admin") || village.isMember(player.getUniqueId())) {
                guiManager.openMainGui(player, village);
            } else if (villageManager.isInAnyVillage(player.getUniqueId())) {
                MessageUtil.send(player, configManager.getPrefix(),
                        configManager.message("already-in-village"));
            } else if (villageManager.addJoinRequest(village, player.getUniqueId())) {
                MessageUtil.send(player, configManager.getPrefix(),
                        configManager.message("join-request-created").replace("%name%", village.getName()));
                MessageUtil.notifyJoinRequestReviewers(configManager.getPrefix(), village, player);
            } else {
                MessageUtil.send(player, configManager.getPrefix(),
                        configManager.message("join-request-already-exists").replace("%name%", village.getName()));
            }
            event.setCancelled(true);
            return;
        }

        // Validate well structure
        if (!isValidWellStructure(clickedBlock)) return;

        // Check permission
        String permission = configManager.getFoundingPermission();
        if (!permission.isEmpty() && !player.hasPermission(permission)) {
            MessageUtil.send(player, configManager.getPrefix(),
                    configManager.message("no-permission"));
            return;
        }

        // Check if player is already in a village
        if (villageManager.isInAnyVillage(player.getUniqueId())) {
            MessageUtil.send(player, configManager.getPrefix(),
                    configManager.message("already-in-village"));
            return;
        }

        // Store bell location for the founding flow, then open GUI
        guiClickListener.getPendingBellLocations().put(player.getUniqueId(), bellLocation);
        guiManager.openFoundingGui(player);
        event.setCancelled(true);
    }

    /**
     * Validates the well structure:
     * 1. Water block below
     * 2. Max N air blocks between water and bell
     * 3. Bell on top
     */
    private boolean isValidWellStructure(Block bellBlock) {
        int maxAirBlocks = configManager.getWellMaxAirBlocks();
        World world = bellBlock.getWorld();
        int bellX = bellBlock.getX();
        int bellZ = bellBlock.getZ();
        int bellY = bellBlock.getY();

        // Search downward from the bell for air then water
        int airCount = 0;
        for (int y = bellY - 1; y >= bellY - maxAirBlocks - 1; y--) {
            Block below = world.getBlockAt(bellX, y, bellZ);
            Material type = below.getType();

            if (type == Material.AIR || type == Material.CAVE_AIR) {
                airCount++;
                if (airCount > maxAirBlocks) return false;
            } else if (type == Material.WATER) {
                return airCount > 0;
            } else {
                return false;
            }
        }
        return false;
    }
}
