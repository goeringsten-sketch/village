package com.example.village.listener;

import com.example.village.VillagePlugin;
import com.example.village.service.VillageLightService;
import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class VillageLightListener implements Listener {

    private final VillagePlugin plugin;
    private final VillageLightService lightService;
    private final com.example.village.service.VillageManager villageManager;

    public VillageLightListener(VillagePlugin plugin, VillageLightService lightService, com.example.village.service.VillageManager villageManager) {
        this.plugin = plugin;
        this.lightService = lightService;
        this.villageManager = villageManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        scheduleRefresh(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lightService.forgetPlayer(event.getPlayer());
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        boolean changedChunk = event.getFrom().getChunk().getX() != event.getTo().getChunk().getX()
                || event.getFrom().getChunk().getZ() != event.getTo().getChunk().getZ()
                || !event.getFrom().getWorld().equals(event.getTo().getWorld());

        if (changedChunk) {
            lightService.checkPlayer(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        scheduleRefresh(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        // If player has no bed spawn location and is member of a village, respawn at village bell
        org.bukkit.entity.Player p = event.getPlayer();
        if (p.getBedSpawnLocation() == null) {
            java.util.Optional<com.example.village.model.Village> vOpt = villageManager.getPlayerVillage(p.getUniqueId());
            if (vOpt.isPresent() && vOpt.get().getBellLocation() != null) {
                event.setRespawnLocation(vOpt.get().getBellLocation());
            }
        }
        scheduleRefresh(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        scheduleRefresh(event.getPlayer());
    }

    @EventHandler
    public void onChunkLoad(PlayerChunkLoadEvent event) {
        lightService.refreshChunk(event.getPlayer(), event.getChunk());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        lightService.refreshChunkForNearbyTrackedPlayers(
                event.getBlock().getWorld(),
                event.getBlock().getChunk().getX(),
                event.getBlock().getChunk().getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        lightService.refreshChunkForNearbyTrackedPlayers(
                event.getBlock().getWorld(),
                event.getBlock().getChunk().getX(),
                event.getBlock().getChunk().getZ());
    }

    private void scheduleRefresh(Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> lightService.refreshPlayer(player), 1L);
    }
}
