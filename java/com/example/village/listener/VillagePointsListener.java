package com.example.village.listener;

import com.example.village.model.Village;
import com.example.village.service.LevelService;
import com.example.village.service.VillageManager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.Optional;

/**
 * Automatically awards village points for player actions within village borders.
 */
public final class VillagePointsListener implements Listener {

    private final VillageManager villageManager;
    private final LevelService levelService;

    public VillagePointsListener(VillageManager villageManager, LevelService levelService) {
        this.villageManager = villageManager;
        this.levelService = levelService;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Optional<Village> village = villageManager.getPlayerVillage(player.getUniqueId());
        if (village.isEmpty()) return;

        if (!event.getBlock().getWorld().getName().equals(village.get().getWorldName())) return;
        if (village.get().containsLocation(event.getBlock().getLocation())) {
            levelService.addPointsFromSource(village.get(), "block-place");
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Optional<Village> village = villageManager.getPlayerVillage(player.getUniqueId());
        if (village.isEmpty()) return;

        if (!event.getBlock().getWorld().getName().equals(village.get().getWorldName())) return;
        if (village.get().containsLocation(event.getBlock().getLocation())) {
            levelService.addPointsFromSource(village.get(), "block-break");
        }
    }

    @EventHandler
    public void onEntityKill(EntityDeathEvent event) {
        if (event.getEntity() instanceof Villager) {
            return;
        }
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        Optional<Village> village = villageManager.getPlayerVillage(killer.getUniqueId());
        if (village.isEmpty()) return;

        if (!event.getEntity().getWorld().getName().equals(village.get().getWorldName())) return;
        if (village.get().containsLocation(event.getEntity().getLocation())) {
            levelService.addPointsFromSource(village.get(), "mob-kill");
        }
    }
}
