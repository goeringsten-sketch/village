package com.example.village.service;

import com.example.village.VillagePlugin;
import com.example.village.config.VillageConfigManager;
import com.example.village.model.CustomVillager;
import com.example.village.model.Village;
import org.bukkit.Location;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Optimierter Tick-Service für Villager mit Warteschlangen-basiertem Ticking.
 */
public class VillagerTickService {
    private final VillagePlugin plugin;
    private final StateEngine stateEngine;
    private final VillageManager villageManager;
    private VillagerNutritionService nutritionService;
    private int tickTaskId = -1;

    private final Queue<VillagerQueueEntry> queue = new ArrayDeque<>();
    private final Map<UUID, Long> lastUpdateTimes = new ConcurrentHashMap<>();

    private record VillagerQueueEntry(Village village, CustomVillager villager) {}

    public VillagerTickService(VillagePlugin plugin, StateEngine stateEngine, VillageManager villageManager) {
        this.plugin = plugin;
        this.stateEngine = stateEngine;
        this.villageManager = villageManager;
    }

    public void setNutritionService(VillagerNutritionService nutritionService) {
        this.nutritionService = nutritionService;
    }

    public void start() {
        if (tickTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(tickTaskId);
        }
        VillageConfigManager configManager = plugin.getVillageConfigManager();
        long interval = configManager != null
                ? Math.max(1L, configManager.getBatchUpdateInterval())
                : 1L;
        tickTaskId = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(
            plugin,
            this::tick,
            interval,
            interval
        );
    }

    public void stop() {
        if (tickTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(tickTaskId);
            tickTaskId = -1;
        }
        queue.clear();
        lastUpdateTimes.clear();
    }

    private void tick() {
        if (queue.isEmpty()) {
            populateQueue();
        }
        if (queue.isEmpty()) {
            return;
        }

        VillageConfigManager configManager = plugin.getVillageConfigManager();
        VillagerService villagerService = plugin.getVillagerService();
        if (configManager == null || villagerService == null) {
            return;
        }

        int maxUpdates = configManager.getMaxUpdatesPerTick();
        int globalMaxUpdates = configManager.getGlobalMaxUpdatesPerTick();
        boolean chunkOptimization = configManager.isChunkOptimizationEnabled();

        int processed = 0;
        while (processed < maxUpdates && processed < globalMaxUpdates && !queue.isEmpty()) {
            VillagerQueueEntry entry = queue.poll();
            if (entry == null) continue;

            Village village = entry.village();
            CustomVillager villager = entry.villager();

            // Verify if villager is still in the village
            if (!village.getVillagers().contains(villager)) {
                lastUpdateTimes.remove(villager.getId());
                continue;
            }

            // Chunk optimization check
            if (chunkOptimization) {
                Location loc = villagerService.getVillagerLocation(village, villager);
                if (loc == null || loc.getWorld() == null || !loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                    // Skip and do not update its last update time (it pauses)
                    continue;
                }
            }

            // Tick this villager
            tickVillager(village, villager);
            processed++;
        }
    }

    private void populateQueue() {
        for (Village village : villageManager.getAllVillages()) {
            for (CustomVillager villager : village.getVillagers()) {
                queue.add(new VillagerQueueEntry(village, villager));
            }
        }
    }

    private void tickVillager(Village village, CustomVillager villager) {
        long now = System.currentTimeMillis();
        long lastUpdate = lastUpdateTimes.getOrDefault(villager.getId(), now - 20000L);
        lastUpdateTimes.put(villager.getId(), now);

        double minutes = (now - lastUpdate) / 60000.0;
        // Clamp decay step size to at most 10 seconds of equivalent minutes to prevent instant starvation after chunk loading or lags
        if (minutes > 10.0 / 60.0) {
            minutes = 10.0 / 60.0;
        }

        if (nutritionService != null) {
            nutritionService.tick(village, villager, minutes);
        } else {
            villager.decayNutrients(minutes);
        }
        villager.decayNeeds(minutes);

        stateEngine.updateVillagerState(villager, village);
        stateEngine.executeStateAction(villager, village);

        VillagerService villagerService = plugin.getVillagerService();
        if (villagerService != null) {
            villagerService.tickProductionAndSchedule(village, villager);
        }

        if (villager.getMorale() > 50) {
            villager.adjustMorale(-0.1);
        }
        if (nutritionService != null && nutritionService.hasLowNutrient(villager)) {
            villager.adjustMorale(-0.05);
        }
    }
}
