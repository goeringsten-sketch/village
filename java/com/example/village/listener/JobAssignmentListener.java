package com.example.village.listener;

import com.example.village.VillagePlugin;
import com.example.village.config.VillageConfigManager;
import com.example.village.model.BuildingDefinition;
import com.example.village.model.BuildingType;
import com.example.village.model.CustomVillager;
import com.example.village.model.Village;
import com.example.village.model.VillageBuilding;
import com.example.village.model.VillagerProfession;
import com.example.village.service.VillageManager;
import com.example.village.service.VillagerService;
import com.example.village.service.WorkstationMatcher;
import com.example.village.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Map;
import java.util.UUID;

/**
 * Handles player clicking on building blocks to assign a job to a villager.
 * When a player has a pending job assignment and clicks on a block that belongs
 * to a building, the villager is assigned the job corresponding to that building type.
 */
public final class JobAssignmentListener implements Listener {

    private final VillagePlugin plugin;
    private final VillageConfigManager configManager;
    private final VillageManager villageManager;
    private final VillagerService villagerService;
    private final GuiClickListener guiClickListener;

    public JobAssignmentListener(VillagePlugin plugin,
                                  VillageConfigManager configManager,
                                  VillageManager villageManager,
                                  VillagerService villagerService,
                                  GuiClickListener guiClickListener) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.villageManager = villageManager;
        this.villagerService = villagerService;
        this.guiClickListener = guiClickListener;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        Map<UUID, GuiClickListener.PendingJobAssignment> pending =
                guiClickListener.getPendingJobAssignments();
        if (!pending.containsKey(uuid)) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        event.setCancelled(true);

        GuiClickListener.PendingJobAssignment assignment = pending.get(uuid);
        Village village = assignment.getVillage();
        CustomVillager villager = assignment.getVillager();

        Location clickedLoc = block.getLocation();

        // Find which building this workstation block belongs to
        VillageBuilding targetBuilding = null;
        String matchedWorkstationKey = null;
        for (VillageBuilding building : village.getBuildings()) {
            if (!building.isCompleted()) continue;
            Location bLoc = building.getLocation();
            if (bLoc == null || bLoc.getWorld() == null) continue;
            if (!bLoc.getWorld().equals(clickedLoc.getWorld())) continue;
            BuildingType bt = configManager.getBuildingType(building.getTypeKey());
            BuildingDefinition def = plugin.getBuildingConfigLoader() != null
                    ? plugin.getBuildingConfigLoader().getDefinition(building.getTypeKey())
                    : null;
            if (def == null) continue;
            String wsKey = WorkstationMatcher.resolveWorkstationKey(
                    building, def, clickedLoc, block.getType(), bt);
            if (wsKey != null) {
                targetBuilding = building;
                matchedWorkstationKey = wsKey;
                break;
            }
        }

        if (targetBuilding == null) {
            MessageUtil.send(player, configManager.getPrefix(),
                    "&cDieser Block ist kein konfigurierter Arbeitsblock eines Gebaeudes.");
            return;
        }

        // Find the profession that matches this building type
        BuildingType buildingType = configManager.getBuildingType(targetBuilding.getTypeKey());
        BuildingDefinition definition = plugin.getBuildingConfigLoader() != null
                ? plugin.getBuildingConfigLoader().getDefinition(targetBuilding.getTypeKey())
                : null;

        // Find a matching profession for this building type
        String matchedProfession = null;
        for (Map.Entry<String, VillagerProfession> entry : configManager.getProfessions().entrySet()) {
            // Match profession key to building type key (e.g., "farmer" building -> "farmer" profession)
            if (entry.getKey().equalsIgnoreCase(targetBuilding.getTypeKey())) {
                matchedProfession = entry.getKey();
                break;
            }
        }

        if (matchedProfession == null) {
            // Try to find by building type category (e.g., "farm" building -> "farmer" profession)
            for (Map.Entry<String, VillagerProfession> entry : configManager.getProfessions().entrySet()) {
                if (targetBuilding.getTypeKey().contains(entry.getKey())
                        || entry.getKey().contains(targetBuilding.getTypeKey())) {
                    matchedProfession = entry.getKey();
                    break;
                }
            }
        }

        if (matchedProfession == null) {
            // Use first available profession as fallback
            if (!configManager.getProfessions().isEmpty()) {
                matchedProfession = configManager.getProfessions().keySet().iterator().next();
            }
        }

        if (matchedProfession == null) {
            MessageUtil.send(player, configManager.getPrefix(),
                    "&cKein passender Beruf fuer dieses Gebaeude gefunden.");
            pending.remove(uuid);
            return;
        }

        boolean success = villagerService.assignJob(village, villager, matchedProfession, targetBuilding.getId());
        pending.remove(uuid);

        if (success) {
            VillagerProfession profession = configManager.getProfession(matchedProfession);
            String profName = profession != null ? profession.getDisplayName() : matchedProfession;
            String buildingName = definition != null ? definition.getName()
                    : (buildingType != null ? buildingType.getDisplayName() : targetBuilding.getTypeKey());
            MessageUtil.send(player, configManager.getPrefix(),
                    "&a" + villager.getName() + " &7arbeitet jetzt als &e" + profName
                            + " &7in &e" + buildingName + "&7 (&f" + matchedWorkstationKey + "&7).");
        } else {
            MessageUtil.send(player, configManager.getPrefix(),
                    "&cJob konnte nicht zugewiesen werden. Ueberprüfe das Level.");
        }
    }

}
