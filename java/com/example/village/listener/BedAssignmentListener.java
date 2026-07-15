package com.example.village.listener;

import com.example.village.VillagePlugin;
import com.example.village.config.VillageConfigManager;
import com.example.village.gui.GuiManager;
import com.example.village.model.BuildingDefinition;
import com.example.village.model.CustomVillager;
import com.example.village.model.Village;
import com.example.village.model.VillageBuilding;
import com.example.village.service.VillageManager;
import com.example.village.service.VillagerService;
import com.example.village.service.WorkstationMatcher;
import com.example.village.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Map;
import java.util.UUID;

public final class BedAssignmentListener implements Listener {

    private final VillagePlugin plugin;
    private final VillageConfigManager configManager;
    private final VillageManager villageManager;
    private final VillagerService villagerService;
    private final GuiClickListener guiClickListener;
    private final GuiManager guiManager;

    public BedAssignmentListener(VillagePlugin plugin,
                                 VillageConfigManager configManager,
                                 VillageManager villageManager,
                                 VillagerService villagerService,
                                 GuiClickListener guiClickListener,
                                 GuiManager guiManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.villageManager = villageManager;
        this.villagerService = villagerService;
        this.guiClickListener = guiClickListener;
        this.guiManager = guiManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Map<UUID, GuiClickListener.PendingBedAssignment> pending = guiClickListener.getPendingBedAssignments();
        GuiClickListener.PendingBedAssignment assignment = pending.get(uuid);
        if (assignment == null) return;

        Block block = event.getClickedBlock();
        if (block == null) return;
        Material type = block.getType();
        if (!type.name().endsWith("_BED")) {
            event.setCancelled(true);
            MessageUtil.sendClickableCommand(player, configManager.getPrefix(),
                    configManager.message("villager-bed-wrong-place"), "/v abort");
            return;
        }

        event.setCancelled(true);

        Village village = assignment.getVillage();
        CustomVillager villager = assignment.getVillager();

        VillageBuilding targetBuilding = null;
        for (VillageBuilding building : village.getBuildings()) {
            if (!building.isCompleted()) continue;
            BuildingDefinition def = plugin.getBuildingConfigLoader() != null
                    ? plugin.getBuildingConfigLoader().getDefinition(building.getTypeKey())
                    : null;
            if (def == null || def.getVillagerSlots() <= 0) continue;
            if (!WorkstationMatcher.isInsideConfiguredArea(building, def, block.getLocation())) continue;
            targetBuilding = building;
            break;
        }

        if (targetBuilding == null) {
            MessageUtil.sendClickableCommand(player, configManager.getPrefix(),
                    configManager.message("villager-bed-not-house"), "/v abort");
            return;
        }

        int capacity = villagerService.getVillagerCapacityForBuilding(targetBuilding);
        if (capacity <= 0) {
            MessageUtil.sendClickableCommand(player, configManager.getPrefix(),
                    configManager.message("villager-bed-no-slots"), "/v abort");
            return;
        }

        VillageBuilding assignedBuilding = targetBuilding;
        long assignedCount = village.getVillagers().stream()
                .filter(v -> assignedBuilding.getId().equals(v.getAssignedBedBuildingId())
                        && !v.getId().equals(villager.getId()))
                .count();
        if (assignedCount >= capacity) {
            MessageUtil.sendClickableCommand(player, configManager.getPrefix(),
                    configManager.message("villager-bed-house-full"), "/v abort");
            return;
        }

        villager.setAssignedBedBuildingId(targetBuilding.getId());
        villageManager.saveVillage(village);
        pending.remove(uuid);

        MessageUtil.send(player, configManager.getPrefix(), configManager.message("villager-bed-assigned"));
        guiManager.openVillagerConfigGui(player, village, villager);
    }
}
