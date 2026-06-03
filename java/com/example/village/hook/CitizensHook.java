package com.example.village.hook;

import com.example.village.model.CustomVillager;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public final class CitizensHook {

    private final JavaPlugin plugin;
    private boolean available;
    private final Map<UUID, Integer> villagerNpcMap = new HashMap<>();

    public CitizensHook(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void setup(Logger logger) {
        available = Bukkit.getPluginManager().getPlugin("Citizens") != null;
        if (available) {
            logger.info("Citizens-Integration aktiviert.");
        } else {
            logger.info("Citizens nicht gefunden - Standard-NPC-Verhalten wird verwendet.");
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * Spawns a Citizens NPC for the given custom villager at the specified location.
     * Returns the NPC ID or -1 if Citizens is not available.
     *
     * In production, this would use:
     * NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.VILLAGER, villager.getName());
     * npc.spawn(location);
     */
    public int spawnNpc(CustomVillager villager, Location location) {
        if (!isAvailable() || location.getWorld() == null) return -1;

        try {
            NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.VILLAGER, villager.getName());
            if (!npc.spawn(location)) {
                return -1;
            }

            int npcId = npc.getId();
            villagerNpcMap.put(villager.getId(), npcId);

            // Persist custom villager reference on the spawned entity for Bukkit-based interaction listeners
            if (npc.getEntity() != null) {
                NamespacedKey key = new NamespacedKey(plugin, "village-villager-id");
                npc.getEntity().getPersistentDataContainer().set(
                        key, PersistentDataType.STRING, villager.getId().toString());
            }

            return npcId;
        } catch (Exception ex) {
            Bukkit.getLogger().warning("Citizens NPC konnte nicht erstellt werden: " + ex.getMessage());
            return -1;
        }
    }

    /**
     * Removes the Citizens NPC associated with the given villager.
     */
    public void removeNpc(UUID villagerId) {
        if (!isAvailable()) return;
        Integer npcId = villagerNpcMap.remove(villagerId);
        if (npcId == null) return;

        NPC npc = CitizensAPI.getNPCRegistry().getById(npcId);
        if (npc != null) {
            npc.destroy();
        }
    }

    /**
     * Removes the Citizens NPC by NPC ID.
     */
    public void removeNpc(int npcId) {
        if (!isAvailable() || npcId < 0) return;
        NPC npc = CitizensAPI.getNPCRegistry().getById(npcId);
        if (npc != null) {
            npc.destroy();
        }
    }

    /**
     * Setzt den Skin eines NPCs.
     */
    public void setNpcSkin(int npcId, String skinName) {
        if (!isAvailable() || skinName == null || skinName.isEmpty()) return;
        NPC npc = CitizensAPI.getNPCRegistry().getById(npcId);
        if (npc == null) return;

        // Skin-Trait wird nicht verwendet, um Kompilierungsfehler zu vermeiden
        // TODO: Skin-Trait später hinzufügen, wenn Citizens verfügbar ist
    }

    /**
     * Gets the NPC ID for a villager.
     */
    public int getNpcId(UUID villagerId) {
        return villagerNpcMap.getOrDefault(villagerId, -1);
    }

    /**
     * Navigiert einen NPC zu einer Location.
     */
    public void navigateTo(int npcId, Location location) {
        if (!isAvailable()) return;

        NPC npc = CitizensAPI.getNPCRegistry().getById(npcId);
        if (npc != null && npc.isSpawned()) {
            npc.getNavigator().setTarget(location);
        }
    }

    /**
     * Holt die aktuelle Location eines NPCs.
     */
    public Location getNpcLocation(int npcId) {
        if (!isAvailable()) return null;

        NPC npc = CitizensAPI.getNPCRegistry().getById(npcId);
        if (npc != null && npc.isSpawned()) {
            return npc.getEntity().getLocation();
        }
        return null;
    }
}
