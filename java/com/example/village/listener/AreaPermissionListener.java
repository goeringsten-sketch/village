package com.example.village.listener;

import com.example.village.config.VillageConfigManager;
import com.example.village.hook.WorldGuardHook;
import com.example.village.model.Village;
import com.example.village.model.VillageBorder;
import com.example.village.service.VillageManager;
import com.example.village.util.MessageUtil;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/**
 * Enforces per-border owner permissions:
 * - If a border has NO owners -> everyone may build/interact.
 * - If a border has owners -> only owners and the founder may build/interact.
 */
public final class AreaPermissionListener implements Listener {

    private final VillageConfigManager configManager;
    private final VillageManager villageManager;
    private final WorldGuardHook worldGuardHook;

    public AreaPermissionListener(VillageConfigManager configManager, VillageManager villageManager,
                                  WorldGuardHook worldGuardHook) {
        this.configManager = configManager;
        this.villageManager = villageManager;
        this.worldGuardHook = worldGuardHook;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!checkAllowed(event.getPlayer(), event.getBlockPlaced())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!checkAllowed(event.getPlayer(), event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!checkAllowed(event.getPlayer(), block)) {
            event.setCancelled(true);
        }
    }

    private boolean checkAllowed(Player player, Block block) {
        if (player == null || block == null || block.getWorld() == null) return true;

        Village village = villageManager.getVillageAtLocation(block.getLocation()).orElse(null);
        if (village == null) return true;

        // Founder override
        if (village.isFounder(player.getUniqueId())) return true;

        VillageBorder border = village.getBorderAt(block.getX(), block.getZ());
        if (border == null) return true;

        // No owners -> free for all
        if (border.getOwners().isEmpty()) return true;

        if (border.isOwner(player.getUniqueId())) return true;
        if (worldGuardHook != null && worldGuardHook.isAvailable()
                && worldGuardHook.hasAccessToVillageBorder(village, border.getId(), player.getUniqueId())) {
            return true;
        }

        MessageUtil.send(player, configManager.getPrefix(),
                "&cDu hast hier keine Rechte. &7Flaeche: &e" + border.getId());
        return false;
    }
}

