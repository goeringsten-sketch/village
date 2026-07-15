package com.example.village.service;

import com.example.village.VillagePlugin;
import com.example.village.config.VillageConfigManager;
import com.example.village.gui.GuiManager;
import com.example.village.model.CustomVillager;
import com.example.village.model.Village;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service zur Verwaltung von Glowing-Effekten für Dorfbewohner.
 * 0 = OFF (kein Effekt)
 * 1 = TEMP (temporär, ~15 Sekunden)
 * 2 = ON (dauerhaft, bis deaktiviert)
 */
public final class VillagerGlowService {

    private final VillagePlugin plugin;
    private final GuiManager guiManager;
    private final VillageManager villageManager;
    private final VillageConfigManager configManager;

    // Track active glow timers per player: UUID -> taskId
    private final Map<UUID, Integer> activeTempGlowTasks = new ConcurrentHashMap<>();
    // Track permanently glowing villagers per player: UUID -> Set<villagerUUID>
    private final Map<UUID, Set<UUID>> permanentGlowingVillagers = new ConcurrentHashMap<>();

    public VillagerGlowService(VillagePlugin plugin, GuiManager guiManager, VillageManager villageManager, VillageConfigManager configManager) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.villageManager = villageManager;
        this.configManager = configManager;
    }

    /**
     * Zeigt alle Dorfbewohner eines Dorfes mit Glowing-Effekt basierend auf dem aktuellen Modus.
     */
    public void showVillagers(Player player, Village village) {
        int mode = guiManager.getVillagerGlowMode(player.getUniqueId());
        
        switch (mode) {
            case 1 -> showVillagersTemporarily(player, village);
            case 2 -> showVillagersPermanently(player, village);
            case 0 -> hideVillagers(player, village);
        }
    }

    /**
     * Zeigt einen einzelnen Dorfbewohner temporär mit Glowing-Effekt an.
     */
    public void showVillagerTemporarily(Player player, CustomVillager villager) {
        Integer oldTaskId = activeTempGlowTasks.get(player.getUniqueId());
        if (oldTaskId != null && oldTaskId > 0) {
            Bukkit.getScheduler().cancelTask(oldTaskId);
        }

        Entity entity = findVillagerEntity(villager);
        if (entity instanceof LivingEntity living) {
            living.setGlowing(true);
        }

        int durationTicks = configManager.getVillagerGlowTempDurationTicks();
        final int[] taskId = new int[1];
        taskId[0] = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Entity targetEntity = findVillagerEntity(villager);
            if (targetEntity instanceof LivingEntity living) {
                living.setGlowing(false);
            }

            Integer currentTaskId = activeTempGlowTasks.get(player.getUniqueId());
            if (currentTaskId != null && currentTaskId == taskId[0]) {
                activeTempGlowTasks.remove(player.getUniqueId());
            }
        }, durationTicks).getTaskId();

        activeTempGlowTasks.put(player.getUniqueId(), taskId[0]);
    }

    /**
     * Zeigt Dorfbewohner temporär mit Glowing (ca. 15 Sekunden oder konfiguriert).
     */
    private void showVillagersTemporarily(Player player, Village village) {
        // Alte temporäre Glow entfernen
        Integer oldTaskId = activeTempGlowTasks.get(player.getUniqueId());
        if (oldTaskId != null && oldTaskId > 0) {
            Bukkit.getScheduler().cancelTask(oldTaskId);
        }

        // Neue Glowing setzen
        for (CustomVillager v : village.getVillagers()) {
            Entity entity = findVillagerEntity(v);
            if (entity instanceof LivingEntity living) {
                living.setGlowing(true);
            }
        }

        // Timer für Glowing-Entfernung starten
        int durationTicks = configManager.getVillagerGlowTempDurationTicks();
        final int[] taskId = new int[1];
        taskId[0] = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (CustomVillager v : village.getVillagers()) {
                Entity entity = findVillagerEntity(v);
                if (entity instanceof LivingEntity living) {
                    living.setGlowing(false);
                }
            }
            Integer currentTaskId = activeTempGlowTasks.get(player.getUniqueId());
            if (currentTaskId != null && currentTaskId == taskId[0]) {
                activeTempGlowTasks.remove(player.getUniqueId());
            }
        }, durationTicks).getTaskId();

        activeTempGlowTasks.put(player.getUniqueId(), taskId[0]);
    }

    /**
     * Zeigt Dorfbewohner dauerhaft mit Glowing (bis deaktiviert).
     */
    private void showVillagersPermanently(Player player, Village village) {
        // Alte temporäre Timer deaktivieren
        Integer oldTaskId = activeTempGlowTasks.get(player.getUniqueId());
        if (oldTaskId != null && oldTaskId > 0) {
            Bukkit.getScheduler().cancelTask(oldTaskId);
            activeTempGlowTasks.remove(player.getUniqueId());
        }

        Set<UUID> glowingSet = permanentGlowingVillagers.computeIfAbsent(
            player.getUniqueId(), k -> ConcurrentHashMap.newKeySet());
        glowingSet.clear();

        // Glowing setzen und tracken
        for (CustomVillager v : village.getVillagers()) {
            Entity entity = findVillagerEntity(v);
            if (entity instanceof LivingEntity living) {
                living.setGlowing(true);
                glowingSet.add(v.getId());
            }
        }
    }

    /**
     * Deaktiviert Glowing-Effekte für einen Spieler.
     */
    private void hideVillagers(Player player, Village village) {
        // Temporäre Timer deaktivieren
        Integer oldTaskId = activeTempGlowTasks.get(player.getUniqueId());
        if (oldTaskId != null && oldTaskId > 0) {
            Bukkit.getScheduler().cancelTask(oldTaskId);
            activeTempGlowTasks.remove(player.getUniqueId());
        }

        // Permanente Glowing entfernen
        Set<UUID> glowingSet = permanentGlowingVillagers.get(player.getUniqueId());
        if (glowingSet != null) {
            for (CustomVillager v : village.getVillagers()) {
                Entity entity = findVillagerEntity(v);
                if (entity instanceof LivingEntity living) {
                    living.setGlowing(false);
                }
            }
            permanentGlowingVillagers.remove(player.getUniqueId());
        }
    }

    /**
     * Ruft den Glowing-Status aktualisiert auf, wenn eine neue Dorf-Session beginnt.
     * Dies wird aufgerufen, wenn der Spieler sein Dorf öffnet.
     */
    public void refreshGlowingForPlayer(Player player, Village village) {
        showVillagers(player, village);
    }

    /**
     * Deaktiviert alle Glowing-Effekte für einen Spieler (z.B. beim Logout).
     */
    public void clearGlowingForPlayer(UUID playerId) {
        // Timer deaktivieren
        Integer taskId = activeTempGlowTasks.remove(playerId);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }

        // Permanente Glowing aus Tracking entfernen
        permanentGlowingVillagers.remove(playerId);
    }

    /**
     * Findet die Entity eines Custom-Villagers basierend auf ID/UUID/Name.
     * Dies ist eine Hilfsmethode — die genaue Implementierung hängt davon ab,
     * wie CustomVillagers getracked werden (Vanilla mit PDC, Citizens via Hook, etc.).
     */
    private Entity findVillagerEntity(CustomVillager villager) {
        // Versuche über Bukkit-Welten nach Vanilla-Villager mit PDC-Tag zu suchen
        java.util.UUID villagerUUID = villager.getId();
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof org.bukkit.entity.Villager v) {
                    var pdc = v.getPersistentDataContainer();
                    String idStr = null;
                    try {
                        idStr = pdc.get(new org.bukkit.NamespacedKey(plugin, "village-villager-id"),
                                org.bukkit.persistence.PersistentDataType.STRING);
                    } catch (Exception ignored) {}
                    if (idStr != null && idStr.equals(villagerUUID.toString())) {
                        return entity;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Gibt den aktuellen Glowing-Modus eines Spielers zurück.
     */
    public int getGlowMode(UUID playerId) {
        return guiManager.getVillagerGlowMode(playerId);
    }
}
