package com.example.village.listener;

import com.example.village.VillagePlugin;
import com.example.village.gui.VillagerMenuGui;
import com.example.village.model.CustomVillager;
import com.example.village.service.VillagerManager;
import com.example.village.service.SkillTreeManager;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * Listener für Villager-Interaktionen (Linksklick auf NPC).
 */
public class VillagerClickListener implements Listener {
    private final VillagePlugin plugin;
    private final VillagerManager villagerManager;
    private final SkillTreeManager skillTreeManager;

    // Temp: Mapping Citizens NPC UUID -> CustomVillager UUID (Fallback)
    private final java.util.Map<UUID, UUID> npcToVillager = new java.util.HashMap<>();

    public VillagerClickListener(VillagePlugin plugin, VillagerManager villagerManager, 
                                 SkillTreeManager skillTreeManager) {
        this.plugin = plugin;
        this.villagerManager = villagerManager;
        this.skillTreeManager = skillTreeManager;
    }

    @EventHandler
    public void onVillagerClick(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        Player player = event.getPlayer();

        // Nur für Villager-Entities
        if (!(entity instanceof org.bukkit.entity.Villager)) {
            return;
        }

        // Villager abrufen
        CustomVillager villager = findVillagerByEntity(entity);
        if (villager == null) {
            return;
        }

        // Event canceln (Standard-Verhalten unterbinden)
        event.setCancelled(true);

        // Menü öffnen
        openVillagerMenu(player, villager);
    }

    private void openVillagerMenu(Player player, CustomVillager villager) {
        if (plugin.getVillagerGuiClickListener() != null) {
            plugin.getVillagerGuiClickListener().trackVillagerMenu(player, villager);
        }
        VillagerMenuGui gui = new VillagerMenuGui(villager, player, skillTreeManager,
                plugin.getVillagerNutritionService());
        gui.open();
    }

    private CustomVillager findVillagerByEntity(Entity entity) {
        // 1. Aus Citizens NPC-Daten abrufen
        if (plugin.getCitizensHook().isAvailable() && net.citizensnpcs.api.CitizensAPI.getNPCRegistry().isNPC(entity)) {
            net.citizensnpcs.api.npc.NPC npc = net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getNPC(entity);
            if (npc != null && npc.data().has("village-villager-id")) {
                String idStr = npc.data().get("village-villager-id");
                if (idStr != null) {
                    try {
                        UUID villagerId = UUID.fromString(idStr);
                        return villagerManager.getVillager(villagerId);
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }

        // 2. Aus Bukkit PersistentDataContainer abrufen
        if (entity instanceof org.bukkit.entity.Villager villagerEntity) {
            NamespacedKey key = new NamespacedKey(plugin, "village-villager-id");
            String idStr = villagerEntity.getPersistentDataContainer().get(key, PersistentDataType.STRING);
            if (idStr != null) {
                try {
                    UUID villagerId = UUID.fromString(idStr);
                    return villagerManager.getVillager(villagerId);
                } catch (IllegalArgumentException ignored) {}
            }
        }

        // 3. Fallback aus temporärer Map
        UUID villagerId = npcToVillager.get(entity.getUniqueId());
        if (villagerId != null) {
            return villagerManager.getVillager(villagerId);
        }
        return null;
    }

    @Deprecated
    private CustomVillager findVillagerByEntity(UUID entityUuid) {
        if (plugin.getCitizensHook().isAvailable()) {
            net.citizensnpcs.api.npc.NPC npc = net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getByUniqueId(entityUuid);
            if (npc != null && npc.data().has("village-villager-id")) {
                String idStr = npc.data().get("village-villager-id");
                if (idStr != null) {
                    try {
                        UUID villagerId = UUID.fromString(idStr);
                        return villagerManager.getVillager(villagerId);
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        }
        UUID villagerId = npcToVillager.get(entityUuid);
        if (villagerId != null) {
            return villagerManager.getVillager(villagerId);
        }
        return null;
    }

    public void registerVillagerEntity(UUID villagerId, UUID entityUuid) {
        npcToVillager.put(entityUuid, villagerId);
        if (plugin.getCitizensHook().isAvailable()) {
            net.citizensnpcs.api.npc.NPC npc = net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getByUniqueId(entityUuid);
            if (npc == null) {
                org.bukkit.entity.Entity entity = Bukkit.getEntity(entityUuid);
                if (entity != null) {
                    npc = net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getNPC(entity);
                }
            }
            if (npc != null) {
                npc.data().setPersistent("village-villager-id", villagerId.toString());
            }
        }
    }

    public void unregisterVillagerEntity(UUID entityUuid) {
        npcToVillager.remove(entityUuid);
        if (plugin.getCitizensHook().isAvailable()) {
            net.citizensnpcs.api.npc.NPC npc = net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getByUniqueId(entityUuid);
            if (npc == null) {
                org.bukkit.entity.Entity entity = Bukkit.getEntity(entityUuid);
                if (entity != null) {
                    npc = net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getNPC(entity);
                }
            }
            if (npc != null) {
                npc.data().remove("village-villager-id");
            }
        }
    }
}
