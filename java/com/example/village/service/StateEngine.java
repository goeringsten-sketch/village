package com.example.village.service;

import com.example.village.VillagePlugin;
import com.example.village.model.CustomVillager;
import com.example.village.model.VillagerActivity;
import com.example.village.model.VillagerNeed;
import com.example.village.model.VillagerState;
import com.example.village.model.Village;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.*;

import java.util.*;

/**
 * State Machine für Villager-KI.
 * Hunger/Essen läuft über {@link VillagerNutritionService} und villagers.yml.
 */
public class StateEngine {

    private static final double DANGER_RADIUS = 16.0;

    private final VillagePlugin plugin;
    private final VillagerNutritionService nutritionService;
    private final Map<UUID, Long> lastStateUpdateTime = new HashMap<>();
    private final Map<UUID, UUID> npcEntityToVillager = new HashMap<>();

    public StateEngine(VillagePlugin plugin, VillagerNutritionService nutritionService) {
        this.plugin = plugin;
        this.nutritionService = nutritionService;
    }

    public void updateVillagerState(CustomVillager villager, Village village) {
        long now = System.currentTimeMillis();
        long lastUpdate = lastStateUpdateTime.getOrDefault(villager.getId(), 0L);
        if (now - lastUpdate < 1000) return;
        lastStateUpdateTime.put(villager.getId(), now);

        if (isInDanger(villager, village)) {
            if (villager.getCurrentState() != VillagerState.FLEEING) {
                changeState(villager, VillagerState.FLEEING);
            }
            villager.setActivityDecayKey("FLEEING");
            return;
        }
        if (villager.getCurrentState() == VillagerState.FLEEING) {
            changeState(villager, VillagerState.IDLE);
        }
 
        if (nutritionService.isHungerCritical(villager)) {
            if (nutritionService.hasEdibleFood(villager)) {
                changeState(villager, VillagerState.EATING);
                villager.setActivityDecayKey("EAT_FOOD");
            } else {
                villager.adjustMorale(-2);
                if (nutritionService.hasLowNutrient(villager)) {
                    villager.adjustMorale(-1);
                }
            }
            return;
        }
 
        if (villager.getNeedValue(VillagerNeed.HAPPINESS) < 20) {
            changeState(villager, VillagerState.SLEEPING);
            villager.setActivityDecayKey("SLEEP");
            return;
        }

        cycleNormalBehavior(villager, village);
    }

    private boolean isInDanger(CustomVillager villager, Village village) {
        Location npcLocation = getNpcLocation(villager, village);
        if (npcLocation == null) return false;

        World world = npcLocation.getWorld();
        if (world == null) return false;

        for (Entity entity : world.getNearbyEntities(npcLocation, DANGER_RADIUS, DANGER_RADIUS, DANGER_RADIUS)) {
            if (isHostileToVillager(entity, villager)) return true;
        }
        return false;
    }

    private boolean isHostileToVillager(Entity entity, CustomVillager villager) {
        if (entity instanceof Monster) {
            if (entity instanceof Zombie) return true;
            if (entity instanceof Skeleton) return true;
            if (entity instanceof Creeper) return true;
            if (entity instanceof Spider spider) {
                World w = spider.getWorld();
                return w.getTime() >= 13000 || w.getTime() < 1000;
            }
            return true;
        }
        if (entity instanceof Player player) {
            if (!player.hasPermission("village.bypass")) {
                var heldItem = player.getInventory().getItemInMainHand();
                Material mat = heldItem.getType();
                return isMeleeWeapon(mat) || isRangedWeapon(mat);
            }
        }
        return false;
    }

    private boolean isMeleeWeapon(Material mat) {
        return mat == Material.WOODEN_SWORD || mat == Material.STONE_SWORD ||
               mat == Material.IRON_SWORD   || mat == Material.GOLDEN_SWORD ||
               mat == Material.DIAMOND_SWORD || mat == Material.NETHERITE_SWORD ||
               mat == Material.WOODEN_AXE   || mat == Material.STONE_AXE ||
               mat == Material.IRON_AXE     || mat == Material.GOLDEN_AXE ||
               mat == Material.DIAMOND_AXE  || mat == Material.NETHERITE_AXE;
    }

    private boolean isRangedWeapon(Material mat) {
        return mat == Material.BOW || mat == Material.CROSSBOW || mat == Material.TRIDENT;
    }

    private Location getNpcLocation(CustomVillager villager, Village village) {
        if (villager.getWorkLocation() != null) return villager.getWorkLocation();
        if (villager.getHomeLocation() != null) return villager.getHomeLocation();
        if (village != null) return village.getBellLocation();
        return null;
    }

    public void executeStateAction(CustomVillager villager, Village village) {
        switch (villager.getCurrentState()) {
            case WORKING    -> handleWorking(villager, village);
            case SLEEPING   -> handleSleeping(villager);
            case EATING     -> handleEating(villager);
            case HEALING    -> handleHealing(villager);
            case FLEEING    -> handleFleeing(villager, village);
            default         -> {}
        }
    }

    private void handleWorking(CustomVillager villager, Village village) {
        villager.setActivityDecayKey("WORK");
        long delta = villager.getLastProductionDelta();
        if (delta > 5000) {
            double output = calculateWorkOutput(villager);
            villager.addXp(output / 10);
            villager.setLastProductionTime(System.currentTimeMillis());
            villager.adjustMorale(-1);
            nutritionService.applyWorkNutrientCost(villager, village, 0.5 / 60.0);
        }
    }

    private void handleSleeping(CustomVillager villager) {
        villager.setActivityDecayKey("SLEEP");
        long delta = villager.getStateChangeDelta();
        if (delta > 10000) {
            villager.setNeedValue(VillagerNeed.HAPPINESS,
                    Math.min(100, villager.getNeedValue(VillagerNeed.HAPPINESS) + 5));
            if (villager.getNeedValue(VillagerNeed.HAPPINESS) >= 90) {
                changeState(villager, VillagerState.IDLE);
            }
        }
    }
 
    private void handleEating(CustomVillager villager) {
        villager.setActivityDecayKey("EAT_FOOD");
        if (nutritionService.eatBestFromInventory(villager)) {
            villager.adjustMorale(2);
        } else if (nutritionService.isHungerCritical(villager)) {
            villager.adjustMorale(-3);
        }
 
        if (villager.getNeedValue(VillagerNeed.HUNGER) >= 70) {
            changeState(villager, VillagerState.IDLE);
        }
    }
 
    private void handleHealing(CustomVillager villager) {
        villager.setActivityDecayKey("HEALING");
        villager.setNeedValue(VillagerNeed.HAPPINESS,
                Math.min(100, villager.getNeedValue(VillagerNeed.HAPPINESS) + 2));
        if (villager.getNeedValue(VillagerNeed.HAPPINESS) >= 90) {
            changeState(villager, VillagerState.IDLE);
        }
    }

    private void handleFleeing(CustomVillager villager, Village village) {
        long delta = villager.getStateChangeDelta();
        if (delta > 3000) {
            villager.adjustMorale(-5);
        }
    }

    private void cycleNormalBehavior(CustomVillager villager, Village village) {
        long stateTime = villager.getStateChangeDelta();
 
        switch (villager.getCurrentState()) {
            case IDLE -> {
                villager.setActivityDecayKey("IDLE");
                if (stateTime > 30000 + (long)(Math.random() * 30000)) {
                    changeState(villager, VillagerState.WORKING);
                }
            }
            case WORKING -> {
                villager.setActivityDecayKey("WORK");
                if (stateTime > 120000 + (long)(Math.random() * 120000)) {
                    nutritionService.applyWorkNutrientCost(villager, village, 5.0 / 60.0);
                    changeState(villager, VillagerState.IDLE);
                }
            }
            case INTERACTING -> {
                villager.setActivityDecayKey("IDLE");
                if (stateTime > 10000) changeState(villager, VillagerState.IDLE);
            }
            case GOSSIPING -> {
                villager.setActivityDecayKey("IDLE");
                if (stateTime > 20000) changeState(villager, VillagerState.IDLE);
            }
            default -> {
                villager.setActivityDecayKey("IDLE");
                if (stateTime > 60000) changeState(villager, VillagerState.IDLE);
            }
        }
    }

    private double calculateWorkOutput(CustomVillager villager) {
        double baseOutput = 10.0;
        double skillBonus = villager.getSkills().values().stream()
                .mapToDouble(s -> s.getLevel() * 0.5)
                .sum();
        double moraleMod = villager.getMorale() / 100.0;
        double nutritionMod = nutritionService.getProductionMultiplier(villager);
        return baseOutput * (1 + skillBonus / 100.0) * moraleMod * nutritionMod;
    }

    public void registerNpcEntity(UUID entityId, UUID villagerId) {
        npcEntityToVillager.put(entityId, villagerId);
    }

    public void unregisterNpcEntity(UUID entityId) {
        npcEntityToVillager.remove(entityId);
    }

    private void changeState(CustomVillager villager, VillagerState newState) {
        VillagerState oldState = villager.getCurrentState();
        if (oldState != newState) {
            villager.setState(newState);
            if (plugin.getDebugConfigManager() != null) {
                plugin.getDebugConfigManager().debug("villager",
                        "Dorfbewohner " + villager.getName() + " (" + villager.getId() + ") wechselt Zustand von " + oldState + " zu " + newState);
            }
        }
    }

    public void startStateUpdater() {
        // Wird durch VillagerTickService aufgerufen
    }
}
