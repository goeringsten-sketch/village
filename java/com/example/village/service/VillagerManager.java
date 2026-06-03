package com.example.village.service;

import com.example.village.VillagePlugin;
import com.example.village.hook.CitizensHook;
import com.example.village.model.CustomVillager;
import com.example.village.model.Village;
import com.example.village.model.VillagerJob;
import com.example.village.model.VillagerState;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Zentraler Manager für Villager-Verwaltung und Citizens-Integration.
 */
public class VillagerManager {
    private final VillagePlugin plugin;
    private final Map<UUID, CustomVillager> villagerCache;
    private final CitizensHook citizensHook;
    private final VillageManager villageManager;
    private final StateEngine stateEngine;

    public VillagerManager(VillagePlugin plugin, CitizensHook citizensHook, 
                          VillageManager villageManager, StateEngine stateEngine) {
        this.plugin = plugin;
        this.citizensHook = citizensHook;
        this.villageManager = villageManager;
        this.stateEngine = stateEngine;
        this.villagerCache = new HashMap<>();
    }

    /**
     * Rekrutiert einen existierenden Vanilla-Villager
     */
    public CustomVillager recruitVillager(Player player, Village village, VillagerJob job, 
                                          String villagerName, Location location, double cost) 
            throws RecruitmentException {
        
        // 1. Validierungen
        if (!village.getFounderId().equals(player.getUniqueId()) && !player.isOp()) {
            throw new RecruitmentException("Du hast keine Berechtigung.");
        }

        // 2. Kosten prüfen
        if (village.getPoints() < cost) {
            throw new RecruitmentException("Nicht genug Dorfpunkte. Benötigt: " + cost);
        }

        if (village.getVillagers().size() >= getVillagerLimit(village)) {
            throw new RecruitmentException("Villager-Limit erreicht!");
        }

        // 3. CustomVillager erstellen
        UUID villagerId = UUID.randomUUID();
        CustomVillager villager = new CustomVillager(villagerId, villagerName, job);
        villager.setParentVillageId(village.getId());
        villager.recruit();
        villager.setState(VillagerState.IDLE);

        // 4. Citizens NPC erstellen
        int npcId = citizensHook.spawnNpc(villager, location);
        if (npcId < 0) {
            throw new RecruitmentException("Citizens NPC konnte nicht erstellt werden.");
        }

        villager.setNpcId(npcId);  // NPC-ID speichern

        // 5. In Village registrieren
        village.addVillager(villager);
        villagerCache.put(villagerId, villager);

        // 6. Kosten abziehen
        village.setPoints(village.getPoints() - (int) cost);

        // 7. Event/Nachricht
        plugin.getLogger().info("Villager " + villagerName + " wurde in " + village.getName() + " rekrutiert!");

        return villager;
    }

    /**
     * Erstellt einen Citizens NPC
     */
    private int createCitizensNpc(CustomVillager villager, Location location) {
        return citizensHook.spawnNpc(villager, location);
    }

    /**
     * Verwaltet einen existierenden Villager
     */
    public CustomVillager getVillager(UUID villagerId) {
        return villagerCache.getOrDefault(villagerId, null);
    }

    /**
     * Löscht einen Villager
     */
    public boolean removeVillager(CustomVillager villager) {
        Village village = villageManager.getVillage(villager.getParentVillageId()).orElse(null);
        if (village == null) return false;

        // Use the proper removal method from VillagerService if available
        // For now, handle basic cleanup
        
        // If it's a Citizens NPC, remove it through the hook
        if (villager.getNpcId() >= 0) {
            // Find the village and remove through VillagerService if possible
            // For now, try direct removal through the hook
            citizensHook.removeNpc(villager.getId());
        }

        village.removeVillager(villager.getId());
        villagerCache.remove(villager.getId());
        return true;
    }

    /**
     * Berechnet das Villager-Limit für ein Dorf
     */
    private int getVillagerLimit(Village village) {
        // Base: 10, +1 pro Level, +3 pro Wohngebäude
        int baseLimit = 10;
        int levelBonus = village.getLevel();
        int buildingBonus = (int) village.getBuildings().stream()
                .filter(b -> b.getTypeKey().contains("house"))
                .count() * 3;
        return baseLimit + levelBonus + buildingBonus;
    }

    /**
     * Exception für Rekrutierungs-Fehler
     */
    public static class RecruitmentException extends Exception {
        public RecruitmentException(String message) {
            super(message);
        }
    }

    public void cacheVillager(CustomVillager villager) {
        villagerCache.put(villager.getId(), villager);
    }
}
