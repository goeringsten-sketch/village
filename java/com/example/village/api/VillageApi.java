package com.example.village.api;

import com.example.village.model.Village;
import com.example.village.service.LevelService;
import com.example.village.service.VillageManager;
import org.bukkit.Location;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Public API for other plugins to interact with the Village system.
 */
public final class VillageApi {

    private final VillageManager villageManager;
    private final LevelService levelService;

    public VillageApi(VillageManager villageManager, LevelService levelService) {
        this.villageManager = villageManager;
        this.levelService = levelService;
    }

    public Optional<Village> getVillage(UUID villageId) {
        return villageManager.getVillage(villageId);
    }

    public Optional<Village> getVillageByName(String name) {
        return villageManager.getVillageByName(name);
    }

    public Optional<Village> getVillageAtLocation(Location location) {
        return villageManager.getVillageAtLocation(location);
    }

    public Optional<Village> getPlayerVillage(UUID playerId) {
        return villageManager.getPlayerVillage(playerId);
    }

    public boolean isInVillage(UUID playerId) {
        return villageManager.isInAnyVillage(playerId);
    }

    public boolean isLocationInVillage(Location location) {
        return villageManager.isLocationInAnyVillage(location);
    }

    public Collection<Village> getAllVillages() {
        return villageManager.getAllVillages();
    }

    public void addVillagePoints(Village village, int points) {
        levelService.addPoints(village, points, "api");
    }

    public int getVillageLevel(Village village) {
        return village.getLevel();
    }

    public int getVillagePoints(Village village) {
        return village.getPoints();
    }
}
