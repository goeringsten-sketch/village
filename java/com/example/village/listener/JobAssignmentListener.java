package com.example.village.listener;

import com.example.village.VillagePlugin;
import com.example.village.config.VillageConfigManager;
import com.example.village.model.BuildingDefinition;

import com.example.village.model.CustomVillager;
import com.example.village.model.Village;
import com.example.village.model.VillageBuilding;
import com.example.village.model.VillagerJob;
import com.example.village.model.VillagerProfession;
import com.example.village.service.VillageManager;
import com.example.village.service.VillagerService;
import com.example.village.service.WorkstationMatcher;
import com.example.village.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
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

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        Map<UUID, GuiClickListener.PendingJobAssignment> pending =
                guiClickListener.getPendingJobAssignments();
        if (!pending.containsKey(uuid)) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && isPassThroughBlock(block.getType())) return;

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
            BuildingDefinition def = plugin.getBuildingConfigLoader() != null
                    ? plugin.getBuildingConfigLoader().getDefinition(building.getTypeKey())
                    : null;
            if (def == null) continue;
            String wsKey = WorkstationMatcher.resolveWorkstationKey(
                    plugin, building, def, clickedLoc, block.getType());
            if (wsKey != null) {
                targetBuilding = building;
                matchedWorkstationKey = wsKey;
                break;
            }
        }

        if (targetBuilding == null) {
            MessageUtil.sendClickableCommand(player, configManager.getPrefix(),
                    configManager.message("villager-job-wrong-block"), "/v abort");
            return;
        }

        BuildingDefinition definition = plugin.getBuildingConfigLoader() != null
                ? plugin.getBuildingConfigLoader().getDefinition(targetBuilding.getTypeKey())
                : null;

        String matchedProfession = resolveProfessionKey(targetBuilding.getTypeKey(), matchedWorkstationKey, definition);

        if (matchedProfession == null) {
            MessageUtil.send(player, configManager.getPrefix(),
                    configManager.text("messages.job-no-match",
                            "&cKein passender Beruf fuer dieses Gebaeude gefunden."));
            pending.remove(uuid);
            return;
        }

        boolean success = villagerService.assignJob(village, villager, matchedProfession, targetBuilding.getId());
        pending.remove(uuid);

        if (success) {
            VillagerProfession profession = configManager.getProfession(matchedProfession);
            String profName = profession != null ? profession.getDisplayName() : matchedProfession;
            String buildingName = definition != null ? definition.getName() : targetBuilding.getTypeKey();
            MessageUtil.send(player, configManager.getPrefix(),
                    "&a" + villager.getName() + " &7arbeitet jetzt als &e" + profName
                            + " &7in &e" + buildingName + "&7 (&f" + matchedWorkstationKey + "&7).");
        } else {
            MessageUtil.send(player, configManager.getPrefix(),
                    configManager.text("messages.job-assign-failed",
                            "&cJob konnte nicht zugewiesen werden. Ueberprüfe das Level."));
        }
    }

    private String resolveProfessionKey(String buildingTypeKey, String workstationKey, BuildingDefinition definition) {
        java.util.LinkedHashSet<String> candidates = new java.util.LinkedHashSet<>();
        addCandidate(candidates, buildingTypeKey);
        addCandidate(candidates, workstationKey);
        if (definition != null) {
            addCandidate(candidates, definition.getId());
            addCandidate(candidates, definition.getName());
        }

        for (String candidate : candidates) {
            for (VillagerJob job : VillagerJob.selectableJobs()) {
                if (job.matches(candidate) && configManager.getProfession(job.getProfessionKey()) != null) {
                    return job.getProfessionKey();
                }
            }
        }

        for (String candidate : candidates) {
            if (configManager.getProfession(candidate) != null) {
                return candidate;
            }
        }

        return null;
    }

    private static void addCandidate(java.util.Set<String> candidates, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        candidates.add(value.toLowerCase(java.util.Locale.ROOT));
    }

    private static boolean isPassThroughBlock(Material material) {
        return Tag.DOORS.isTagged(material)
                || Tag.TRAPDOORS.isTagged(material)
                || Tag.FENCE_GATES.isTagged(material);
    }

}
