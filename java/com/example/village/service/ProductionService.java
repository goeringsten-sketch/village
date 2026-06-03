package com.example.village.service;

import com.example.village.VillagePlugin;
import com.example.village.model.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Tick-basierte Produktionsketten für alle Produktionsgebäude.
 * Läuft asynchron; Output-Einlagerung wird synchron ausgeführt.
 */
public final class ProductionService {

    private final VillagePlugin plugin;
    private final VillageManager villageManager;
    private final BuildingConfigLoader configLoader;
    private final BuildingChestManager chestManager;

    private final Map<UUID, List<ActiveJob>> activeJobs = new HashMap<>();
    private BukkitTask tickTask;

    private static final int INTERVAL_TICKS = 100; // 5 s

    public ProductionService(VillagePlugin plugin, VillageManager villageManager,
                             BuildingConfigLoader configLoader, BuildingChestManager chestManager) {
        this.plugin        = plugin;
        this.villageManager = villageManager;
        this.configLoader  = configLoader;
        this.chestManager  = chestManager;
    }

    public void start() {
        tickTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::tick, INTERVAL_TICKS, INTERVAL_TICKS);
        plugin.getLogger().info("[ProductionService] gestartet.");
    }

    public void stop() { if (tickTask != null) { tickTask.cancel(); tickTask = null; } }

    // ── Tick ──────────────────────────────────────────────────────

    private void tick() {
        long now = System.currentTimeMillis();
        for (Village village : villageManager.getAllVillages()) tickVillage(village, now);
    }

    private void tickVillage(Village village, long now) {
        for (VillageBuilding building : village.getBuildings()) {
            if (!building.isCompleted()) continue;
            BuildingDefinition def = configLoader.getDefinition(building.getTypeKey());
            if (def == null || def.getRecipes().isEmpty()) continue;

            List<ActiveJob> jobs = activeJobs.computeIfAbsent(building.getId(), k -> new ArrayList<>());

            // Fertige Jobs auswerten
            jobs.removeIf(job -> {
                if (now < job.finishTime) return false;
                finishJob(job, building, village);
                return true;
            });

            // Neue Jobs starten
            int freeSlots = Math.max(1, def.getVillagerSlots()) - jobs.size();
            if (freeSlots <= 0) continue;

            Map<Material, Integer> pool = chestManager.getPublicPool(village);
            for (ProductionRecipe recipe : def.getRecipes()) {
                if (freeSlots <= 0) break;
                if (recipe.getRequiredBuildingLevel() > building.getLevel()) continue;
                if (!recipe.canProduce(pool)) continue;
                if (!chestManager.consumeFromPublic(village, recipe.getInputs())) continue;
                pool = recipe.consumeInputs(pool);
                jobs.add(new ActiveJob(building.getId(), village.getId(), recipe,
                    now + recipe.getDurationSeconds() * 1000L, building.getLevel()));
                freeSlots--;
            }
        }
    }

    private void finishJob(ActiveJob job, VillageBuilding building, Village village) {
        double bonus = 1.0 + (building.getLevel() - 1) * 0.1;
        Map<Material, Integer> output = new LinkedHashMap<>();
        job.recipe.getOutputs().forEach((m, a) -> output.put(m, (int) Math.ceil(a * bonus)));

        // Sync: in Haupt-Thread einlagern
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!chestManager.deposit(building.getId(), output)) {
                // Fallback: öffentliche Truhen
                for (VillageBuilding b : village.getBuildings()) {
                    if (chestManager.deposit(b.getId(), output)) break;
                }
            }
        });
    }

    // ── Öffentliche API ───────────────────────────────────────────

    public List<ActiveJob> getActiveJobs(UUID buildingId) {
        return Collections.unmodifiableList(activeJobs.getOrDefault(buildingId, Collections.emptyList()));
    }

    public void cancelJobs(UUID buildingId) { activeJobs.remove(buildingId); }

    // ── Nested ────────────────────────────────────────────────────

    public static final class ActiveJob {
        public final UUID buildingId, villageId;
        public final ProductionRecipe recipe;
        public final long finishTime;
        public final int buildingLevel;

        public ActiveJob(UUID bid, UUID vid, ProductionRecipe r, long ft, int lvl) {
            buildingId = bid; villageId = vid; recipe = r; finishTime = ft; buildingLevel = lvl;
        }

        public long getRemainingMs()      { return Math.max(0, finishTime - System.currentTimeMillis()); }
        public int getRemainingSeconds()  { return (int) (getRemainingMs() / 1000); }
    }
}
