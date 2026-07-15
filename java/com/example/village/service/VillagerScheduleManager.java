package com.example.village.service;

import com.example.village.hook.CitizensHook;
import com.example.village.model.CustomVillager;
import com.example.village.model.VillagerActivity;
import com.example.village.model.Village;
import com.example.village.model.VillageBuilding;
import com.example.village.model.VillagerProfession;
import org.bukkit.Location;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Verwaltet Tagesabläufe, Bewegung und Essens-Aktionen (get-food / eat-food).
 */
public final class VillagerScheduleManager {

    private final VillagerService villagerService;
    private final CitizensHook citizensHook;
    private VillagerNutritionService nutritionService;

    private final Map<UUID, ScheduleTracker> trackers = new HashMap<>();

    public VillagerScheduleManager(VillagerService villagerService, CitizensHook citizensHook) {
        this.villagerService = villagerService;
        this.citizensHook = citizensHook;
    }

    public void setNutritionService(VillagerNutritionService nutritionService) {
        this.nutritionService = nutritionService;
    }

    /**
     * Fortschritt im Schedule; wird pro Villager-Tick aufgerufen.
     */
    public void tickSchedule(Village village, CustomVillager villager) {
        if (villager.getProfessionKey() == null || villager.getProfessionKey().isEmpty()
                || "none".equals(villager.getProfessionKey())) {
            return;
        }

        VillagerProfession profession = villagerService.getConfigManager().getProfession(villager.getProfessionKey());
        if (profession == null || profession.getActivitySequence().isEmpty()) {
            return;
        }

        List<VillagerActivity> sequence = profession.getActivitySequence();
        ScheduleTracker tracker = trackers.computeIfAbsent(villager.getId(), id -> new ScheduleTracker());

        if (tracker.activityIndex >= sequence.size()) {
            tracker.activityIndex = 0;
            tracker.ticksInActivity = 0;
            tracker.actionIndex = 0;
            tracker.actionsExecuted = false;
        }

        VillagerActivity activity = sequence.get(tracker.activityIndex);

        if (village.getLevel() < activity.getRequiredVillageLevel()) {
            advanceActivity(village, villager, sequence, tracker);
            return;
        }

        if (activity.getRequiredUpgrade() != null
                && village.getUpgradeLevel(activity.getRequiredUpgrade()) <= 0) {
            advanceActivity(village, villager, sequence, tracker);
            return;
        }

        applyActivityDecayKey(villager, activity);
        executeActivityMovement(village, villager, activity);

        tracker.ticksInActivity += 20;

        if (!tracker.actionsExecuted && tracker.ticksInActivity >= activity.getDelayTicks()) {
            executeActions(village, villager, activity, tracker);
        }

        if (tracker.ticksInActivity >= activity.getDurationTicks()) {
            advanceActivity(village, villager, sequence, tracker);
        }
    }

    private void executeActions(Village village, CustomVillager villager,
                                VillagerActivity activity, ScheduleTracker tracker) {
        List<String> actions = activity.getActions();
        if (actions.isEmpty()) {
            if (activity.getType() == VillagerActivity.ActivityType.GET_FOOD) {
                actions = List.of("get-food");
            } else if (activity.getType() == VillagerActivity.ActivityType.EAT_FOOD) {
                actions = List.of("eat-food");
            }
        }

        if (actions.isEmpty()) {
            tracker.actionsExecuted = true;
            return;
        }

        while (tracker.actionIndex < actions.size()) {
            String action = actions.get(tracker.actionIndex).toLowerCase();
            tracker.actionIndex++;

            switch (action) {
                case "get-food" -> {
                    if (!tryGetFood(village, villager)) {
                        return;
                    }
                }
                case "eat-food" -> {
                    if (!tryEatFood(villager)) {
                        return;
                    }
                }
                default -> {}
            }
        }

        tracker.actionsExecuted = true;
        tracker.actionIndex = 0;
    }

    private boolean tryGetFood(Village village, CustomVillager villager) {
        if (nutritionService == null) {
            return false;
        }
        if (nutritionService.hasEdibleFood(villager)) {
            return true;
        }
        return nutritionService.getFoodFromVillageStorage(village, villager);
    }

    private boolean tryEatFood(CustomVillager villager) {
        if (nutritionService == null) {
            return false;
        }
        if (nutritionService.eatBestFromInventory(villager)) {
            villager.setState(com.example.village.model.VillagerState.EATING);
            return true;
        }
        return false;
    }

    private void applyActivityDecayKey(CustomVillager villager, VillagerActivity activity) {
        String key = switch (activity.getType()) {
            case WORK, PREPARE, GATHER -> "WORK";
            case SLEEP -> "SLEEP";
            case TRAVEL_TO -> "WAKE_UP";
            case GET_FOOD -> "GET_FOOD";
            case EAT_FOOD -> "EAT_FOOD";
            default -> "IDLE";
        };
        villager.setActivityDecayKey(key);
    }

    private void executeActivityMovement(Village village, CustomVillager villager, VillagerActivity activity) {
        boolean needsNpc = switch (activity.getType()) {
            case TRAVEL_TO, WORK, PREPARE, GATHER -> true;
            default -> false;
        };

        if (needsNpc) {
            ensureNpcVillager(village, villager);
            Location target = getActivityLocation(village, villager, activity);
            if (target != null && villager.getNpcId() >= 0) {
                citizensHook.navigateTo(villager.getNpcId(), target);
            }
        } else if (activity.getType() == VillagerActivity.ActivityType.SLEEP) {
            ensureVanillaVillager(village, villager);
        }
    }

    private void advanceActivity(Village village, CustomVillager villager,
                                 List<VillagerActivity> sequence, ScheduleTracker tracker) {
        if (nutritionService != null && tracker.activityIndex < sequence.size()) {
            nutritionService.applyActivityEndNutrientCost(villager, village, sequence.get(tracker.activityIndex));
        }
        tracker.activityIndex = (tracker.activityIndex + 1) % sequence.size();
        tracker.ticksInActivity = 0;
        tracker.actionIndex = 0;
        tracker.actionsExecuted = false;
    }

    public void startNextActivity(Village village, CustomVillager villager) {
        tickSchedule(village, villager);
    }

    public void ensureVanillaVillager(Village village, CustomVillager villager) {
        if (villager.getNpcId() >= 0) {
            Location currentLocation = getVillagerLocation(village, villager);
            citizensHook.removeNpc(villager.getId());
            villager.setNpcId(-1);
            if (currentLocation != null) {
                villagerService.spawnVanillaVillagerAt(villager, currentLocation);
            }
        }
    }

    public void ensureNpcVillager(Village village, CustomVillager villager) {
        if (villager.getNpcId() < 0) {
            Location currentLocation = getVillagerLocation(village, villager);
            removeVanillaVillager(village, villager);
            if (currentLocation != null) {
                int npcId = citizensHook.spawnNpc(villager, currentLocation);
                if (npcId >= 0) {
                    villager.setNpcId(npcId);
                }
            }
        }
    }

    private Location getActivityLocation(Village village, CustomVillager villager, VillagerActivity activity) {
        String locationKey = activity.getLocationKey();
        if (locationKey.isEmpty()) {
            return null;
        }

        VillageBuilding building = village.getBuildings().stream()
                .filter(b -> villager.getAssignedJobBuildingId() != null
                        && villager.getAssignedJobBuildingId().equals(b.getId()))
                .findFirst()
                .orElse(null);

        if (building == null) {
            return null;
        }
        return villagerService.resolveActivityLocation(building, locationKey);
    }

    private Location getVillagerLocation(Village village, CustomVillager villager) {
        if (villager.getNpcId() >= 0 && citizensHook.isAvailable()) {
            return citizensHook.getNpcLocation(villager.getNpcId());
        }
        return villager.getHomeLocation();
    }

    private void removeVanillaVillager(Village village, CustomVillager villager) {
        villagerService.removeVanillaVillagerEntity(village, villager);
    }

    public void resetTracker(UUID villagerId) {
        trackers.remove(villagerId);
    }

    private static final class ScheduleTracker {
        int activityIndex;
        int ticksInActivity;
        int actionIndex;
        boolean actionsExecuted;
    }
}
