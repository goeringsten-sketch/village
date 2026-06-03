package com.example.village.listener;

import com.example.village.VillagePlugin;
import com.example.village.config.VillageConfigManager;
import com.example.village.gui.GuiManager;
import com.example.village.model.BuildingType;
import com.example.village.model.CustomVillager;
import com.example.village.model.Village;
import com.example.village.model.VillageBuilding;
import com.example.village.model.VillagerProfession;
import com.example.village.service.VillageManager;
import com.example.village.service.VillagerService;
import com.example.village.util.MessageUtil;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

/**
 * Handles player interaction with village villager entities.
 * Shift+Right-click opens the villager config menu.
 * Normal right-click opens the profession menu (if villager has a job).
 * Also handles pending job assignment clicks on building blocks.
 */
public final class VillagerInteractListener implements Listener {

    private final VillagePlugin plugin;
    private final VillageConfigManager configManager;
    private final VillageManager villageManager;
    private final VillagerService villagerService;
    private final GuiManager guiManager;
    private final GuiClickListener guiClickListener;

    public VillagerInteractListener(VillagePlugin plugin,
                                     VillageConfigManager configManager,
                                     VillageManager villageManager,
                                     VillagerService villagerService,
                                     GuiManager guiManager,
                                     GuiClickListener guiClickListener) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.villageManager = villageManager;
        this.villagerService = villagerService;
        this.guiManager = guiManager;
        this.guiClickListener = guiClickListener;
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        Entity entity = event.getRightClicked();

        if (!(entity instanceof Villager villagerEntity)) return;

        // Check if this is a village villager by looking for our custom PersistentData
        NamespacedKey key = new NamespacedKey(plugin, "village-villager-id");
        String villagerIdStr = villagerEntity.getPersistentDataContainer()
                .get(key, PersistentDataType.STRING);
        // If this is NOT a custom village villager (vanilla), allow inviting by right-click
        if (villagerIdStr == null) {
            // Find village at villager location
            java.util.Optional<Village> villageOpt = villageManager.getVillageAtLocation(villagerEntity.getLocation());
            if (villageOpt.isEmpty()) return;
            Village village = villageOpt.get();
            // Only allow invites from players who can manage the village (founder/elder) or admins
            if (!village.isMember(player.getUniqueId()) || (!villageManager.canManageVillage(village, player.getUniqueId()) && !player.hasPermission("village.admin"))) {
                return;
            }

            // Attempt to convert this vanilla villager into a village villager
            com.example.village.model.CustomVillager created = villagerService.spawnVillager(village, villagerEntity.getLocation());
            if (created != null) {
                MessageUtil.send(player, configManager.getPrefix(), "&aDorfbewohner eingeladen und ins Dorf integriert: &e" + created.getName());
            } else {
                MessageUtil.send(player, configManager.getPrefix(), "&cKonnte Villager nicht einladen (evtl. kein freies Bett oder Dorf voll).");
            }
            event.setCancelled(true);
            return;
        }

        UUID villagerId;
        try {
            villagerId = UUID.fromString(villagerIdStr);
        } catch (IllegalArgumentException e) {
            return;
        }

        // Find the village and villager
        Village village = null;
        CustomVillager customVillager = null;
        for (Village v : villageManager.getAllVillages()) {
            for (CustomVillager cv : v.getVillagers()) {
                if (cv.getId().equals(villagerId)) {
                    village = v;
                    customVillager = cv;
                    break;
                }
            }
            if (village != null) break;
        }

        if (village == null || customVillager == null) return;

        event.setCancelled(true);

        if (player.isSneaking()) {
            // Shift+Right-click -> open config menu
            guiManager.openVillagerConfigGui(player, village, customVillager);
        } else {
            // Normal right-click -> open detail/profession menu
            VillagerProfession profession = configManager.getProfession(customVillager.getProfessionKey());
            if (profession != null && !"none".equals(customVillager.getProfessionKey())) {
                guiManager.openVillagerDetailGui(player, village, customVillager);
            } else {
                // No profession - just show info
                guiManager.openVillagerDetailGui(player, village, customVillager);
            }
        }
    }
}
