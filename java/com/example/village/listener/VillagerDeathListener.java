package com.example.village.listener;

import com.example.village.VillagePlugin;
import com.example.village.hook.CitizensHook;
import com.example.village.model.CustomVillager;
import com.example.village.model.Village;
import com.example.village.service.VillageManager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;
import java.util.UUID;

public final class VillagerDeathListener implements Listener {

    private final VillagePlugin plugin;
    private final VillageManager villageManager;
    private final CitizensHook citizensHook;

    public VillagerDeathListener(VillagePlugin plugin, VillageManager villageManager, CitizensHook citizensHook) {
        this.plugin = plugin;
        this.villageManager = villageManager;
        this.citizensHook = citizensHook;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Villager)) return;

        UUID villagerId = getVillagerIdFromEntity(entity);
        if (villagerId == null) return;

        // Find village containing this villager
        Optional<Village> villageOpt = villageManager.getAllVillages().stream()
                .filter(v -> v.getVillagers().stream().anyMatch(cv -> cv.getId().equals(villagerId)))
                .findFirst();
        
        if (!villageOpt.isPresent()) return;

        Village village = villageOpt.get();
        CustomVillager villager = village.getVillagers().stream()
                .filter(cv -> cv.getId().equals(villagerId))
                .findFirst().orElse(null);
        
        if (villager == null) return;

        // Remove villager from village
        village.removeVillager(villagerId);
        village.setLastDeadVillager(villager);
        villageManager.saveVillage(village);

        // Also remove NPC if Citizens is available
        if (citizensHook.isAvailable() && villager.getNpcId() > 0) {
            citizensHook.removeNpc(villager.getNpcId());
        }

        plugin.getLogger().info("Dorfbewohner " + villager.getName() + " ist gestorben.");
    }

    private UUID getVillagerIdFromEntity(LivingEntity entity) {
        try {
            String idString = entity.getPersistentDataContainer().get(
                    new org.bukkit.NamespacedKey(plugin, "village-villager-id"),
                    PersistentDataType.STRING);
            if (idString != null) {
                return UUID.fromString(idString);
            }
        } catch (Exception e) {
            // Not a village villager
        }
        return null;
    }
}
