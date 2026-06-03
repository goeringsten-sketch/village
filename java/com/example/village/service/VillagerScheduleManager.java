package com.example.village.service;

import com.example.village.hook.CitizensHook;
import com.example.village.model.CustomVillager;
import com.example.village.model.VillagerActivity;
import com.example.village.model.Village;
import com.example.village.model.VillageBuilding;
import com.example.village.model.VillagerProfession;
import org.bukkit.Location;

/**
 * Verwaltet die Bewegung und Aktivitäten von Dorfbewohnern.
 * Kann zwischen Vanilla-Villager und NPC wechseln.
 */
public final class VillagerScheduleManager {

    private final VillagerService villagerService;
    private final CitizensHook citizensHook;

    public VillagerScheduleManager(VillagerService villagerService, CitizensHook citizensHook) {
        this.villagerService = villagerService;
        this.citizensHook = citizensHook;
    }

    /**
     * Startet die nächste Aktivität im Schedule des Villagers.
     */
    public void startNextActivity(Village village, CustomVillager villager) {
        if (villager.getProfessionKey() == null || villager.getProfessionKey().isEmpty()) {
            // Kein Beruf - bleibe Vanilla-Villager
            ensureVanillaVillager(village, villager);
            return;
        }

        // Hole nächste Aktivität aus dem Schedule
        VillagerActivity nextActivity = getNextActivity(villager);
        if (nextActivity == null) {
            ensureVanillaVillager(village, villager);
            return;
        }

        // Bestimme, ob NPC benötigt wird
        boolean needsNpc = needsNpcForActivity(nextActivity);

        if (needsNpc) {
            ensureNpcVillager(village, villager);
            executeNpcActivity(village, villager, nextActivity);
        } else {
            ensureVanillaVillager(village, villager);
            executeVanillaActivity(village, villager, nextActivity);
        }
    }

    /**
     * Stellt sicher, dass der Villager ein Vanilla-Villager ist.
     */
    public void ensureVanillaVillager(Village village, CustomVillager villager) {
        if (villager.getNpcId() >= 0) {
            // Konvertiere NPC zurück zu Vanilla
            Location currentLocation = getVillagerLocation(village, villager);
            citizensHook.removeNpc(villager.getId());
            villager.setNpcId(-1);

            // Spawne Vanilla-Villager an aktueller Position
            if (currentLocation != null) {
                villagerService.spawnVanillaVillagerAt(villager, currentLocation);
            }
        }
    }

    /**
     * Stellt sicher, dass der Villager ein NPC ist.
     */
    public void ensureNpcVillager(Village village, CustomVillager villager) {
        if (villager.getNpcId() < 0) {
            // Konvertiere Vanilla zu NPC
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

    /**
     * Bestimmt, ob eine Aktivität einen NPC erfordert.
     */
    private boolean needsNpcForActivity(VillagerActivity activity) {
        return switch (activity.getType()) {
            case TRAVEL_TO, WORK, PREPARE, GATHER -> true;
            case SLEEP, IDLE, TRADE -> false;
        };
    }

    /**
     * Führt eine Aktivität mit NPC aus.
     */
    private void executeNpcActivity(Village village, CustomVillager villager, VillagerActivity activity) {
        Location targetLocation = getActivityLocation(village, villager, activity);
        if (targetLocation != null) {
            citizensHook.navigateTo(villager.getNpcId(), targetLocation);
        }
    }

    /**
     * Führt eine Aktivität mit Vanilla-Villager aus.
     */
    private void executeVanillaActivity(Village village, CustomVillager villager, VillagerActivity activity) {
        // Für Vanilla-Aktivitäten: Setze Home-Location für Bett-Gehen
        if (activity.getType() == VillagerActivity.ActivityType.SLEEP) {
            Location bedLocation = villager.getHomeLocation();
            if (bedLocation != null) {
                // Vanilla-Villager geht automatisch zum Bett
                // Zusätzliche Logik kann hier hinzugefügt werden
            }
        }
    }

    /**
     * Holt die Location für eine Aktivität.
     */
    private Location getActivityLocation(Village village, CustomVillager villager, VillagerActivity activity) {
        String locationKey = activity.getLocationKey();
        if (locationKey.isEmpty()) {
            return null;
        }

        // Finde das Gebäude des Villagers
        VillageBuilding building = village.getBuildings().stream()
                .filter(b -> villager.getAssignedJobBuildingId() != null &&
                           villager.getAssignedJobBuildingId().equals(b.getId()))
                .findFirst()
                .orElse(null);

        if (building == null) {
            return null;
        }

        return villagerService.resolveActivityLocation(building, locationKey);
    }

    /**
     * Holt die nächste Aktivität aus dem Schedule.
     */
    private VillagerActivity getNextActivity(CustomVillager villager) {
        // Vereinfachte Implementierung - in Produktion würde ein Schedule-Tracker verwendet
        VillagerProfession profession = villagerService.getConfigManager().getProfession(villager.getProfessionKey());
        if (profession == null || profession.getActivitySequence().isEmpty()) {
            return null;
        }
        return profession.getActivitySequence().get(0); // Erste Aktivität als Beispiel
    }

    /**
     * Holt die aktuelle Location des Villagers.
     */
    private Location getVillagerLocation(Village village, CustomVillager villager) {
        if (villager.getNpcId() >= 0 && citizensHook.isAvailable()) {
            // NPC Position
            return citizensHook.getNpcLocation(villager.getNpcId());
        } else {
            // Vanilla-Villager Position suchen
            return findVanillaVillagerLocation(village, villager);
        }
    }

    /**
     * Findet die Position eines Vanilla-Villagers.
     */
    private Location findVanillaVillagerLocation(Village village, CustomVillager villager) {
        // Implementierung ähnlich wie in VillagerService.removeVillager
        return villager.getHomeLocation(); // Fallback
    }

    /**
     * Entfernt einen Vanilla-Villager.
     */
    private void removeVanillaVillager(Village village, CustomVillager villager) {
        // Implementierung ähnlich wie in VillagerService.removeVillager
        villagerService.removeVanillaVillagerEntity(village, villager);
    }
}
