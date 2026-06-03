package com.example.village.service;

import com.example.village.VillagePlugin;
import com.example.village.model.CustomVillager;
import com.example.village.model.VillagerNeed;
import com.example.village.model.Village;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collection;
import java.util.UUID;

/**
 * Optimierter Tick-Service für Villager.
 * Chunk-basierte Aktivierung + Batch-Updates für Performance.
 */
public class VillagerTickService {
    private final VillagePlugin plugin;
    private final StateEngine stateEngine;
    private final VillageManager villageManager;
    private int tickTaskId = -1;
    private static final int UPDATE_INTERVAL = 20;  // 1 Sekunde (20 Ticks)

    public VillagerTickService(VillagePlugin plugin, StateEngine stateEngine, VillageManager villageManager) {
        this.plugin = plugin;
        this.stateEngine = stateEngine;
        this.villageManager = villageManager;
    }

    public void start() {
        tickTaskId = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(
            plugin,
            this::updateAllVillagers,
            UPDATE_INTERVAL,
            UPDATE_INTERVAL
        );
    }

    public void stop() {
        if (tickTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(tickTaskId);
            tickTaskId = -1;
        }
    }

    private void updateAllVillagers() {
        // Batch-Update für alle Dörfer
        for (Village village : villageManager.getAllVillages()) {
            updateVillageVillagers(village);
        }
    }

    private void updateVillageVillagers(Village village) {
        // Die aktualisierten Villager holen (aus neuer Villager-Verwaltung)
        Collection<CustomVillager> villagers = village.getVillagers();

        for (CustomVillager villager : villagers) {
            // 1. Bedürfnisse senken
            villager.decayNeeds(0.01);  // 1 Sekunde = ~1/20 Minute

            // 2. State aktualisieren
            stateEngine.updateVillagerState(villager, village);

            // 3. State-Aktion ausführen
            stateEngine.executeStateAction(villager, village);

            // 4. Morale-Drift
            if (villager.getMorale() > 50) {
                villager.adjustMorale(-0.1);  // Langsam sinken
            }
        }
    }

    /**
     * Chunk-basierte Aktivierung: Nur aktive Chunks updaten
     */
    public void enableChunkOptimization() {
        // TODO: Integration mit ChunkLoadEvent
    }
}
