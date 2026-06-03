package com.example.village.listener;

import com.example.village.config.VillageConfigManager;
import com.example.village.model.Village;
import com.example.village.service.VillageManager;
import com.example.village.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Optional;

/**
 * Prevents players from breaking bells that belong to a village.
 */
public final class BellProtectionListener implements Listener {

    private final VillageConfigManager configManager;
    private final VillageManager villageManager;

    public BellProtectionListener(VillageConfigManager configManager, VillageManager villageManager) {
        this.configManager = configManager;
        this.villageManager = villageManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        // Glocke selbst schützen
        if (block.getType() == Material.BELL) {
            Optional<Village> village = villageManager.getVillageByBell(block.getLocation());
            if (village.isPresent()) {
                event.setCancelled(true);
                MessageUtil.send(player, configManager.getPrefix(),
                        "&cDiese Glocke gehoert zum Dorf &e" + village.get().getName()
                                + "&c und kann nicht abgebaut werden!");
                return;
            }
        }

        // Block ÜBER einer Dorf-Glocke schützen
        Block below = block.getRelative(BlockFace.DOWN);
        if (below.getType() == Material.BELL) {
            Optional<Village> village = villageManager.getVillageByBell(below.getLocation());
            if (village.isPresent()) {
                event.setCancelled(true);
                MessageUtil.send(player, configManager.getPrefix(),
                        "&cDieser Block stützt die Dorfglocke von &e" + village.get().getName()
                                + "&c und kann nicht entfernt werden!");
            }
        }
    }
}
