package com.example.village.listener;

import com.example.village.VillagePlugin;
import com.example.village.gui.VillagerMenuGui;
import com.example.village.model.CustomVillager;
import com.example.village.service.VillagerManager;
import com.example.village.service.SkillTreeManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

import java.util.UUID;

/**
 * Listener für Villager-Interaktionen (Linksklick auf NPC).
 */
public class VillagerClickListener implements Listener {
    private final VillagePlugin plugin;
    private final VillagerManager villagerManager;
    private final SkillTreeManager skillTreeManager;

    // Temp: Mapping Citizens NPC UUID -> CustomVillager UUID
    // TODO: Sollte in Citizens persisten (via NPC.data().set())
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

        // Villager aus Cache abrufen
        CustomVillager villager = findVillagerByEntity(event.getRightClicked().getUniqueId());
        if (villager == null) {
            return;
        }

        // Event canceln (Standard-Verhalten unterbinden)
        event.setCancelled(true);

        // Menü öffnen
        openVillagerMenu(player, villager);
    }

    private void openVillagerMenu(Player player, CustomVillager villager) {
        VillagerMenuGui gui = new VillagerMenuGui(villager, player, skillTreeManager);
        gui.open();
    }

    private CustomVillager findVillagerByEntity(UUID entityUuid) {
        // TODO: Bessere Implementierung - Villager sollten in Citizens NPC persisten
        // Für jetzt: Durchsuche alle Villager (ineffizient, aber funktioniert)
        UUID villagerId = npcToVillager.get(entityUuid);
        if (villagerId != null) {
            return villagerManager.getVillager(villagerId);
        }
        return null;
    }

    public void registerVillagerEntity(UUID villagerId, UUID entityUuid) {
        npcToVillager.put(entityUuid, villagerId);
    }

    public void unregisterVillagerEntity(UUID entityUuid) {
        npcToVillager.remove(entityUuid);
    }
}
