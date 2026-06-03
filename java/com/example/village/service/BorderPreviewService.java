package com.example.village.service;

import com.example.village.VillagePlugin;
import com.example.village.config.VillageConfigManager;
import com.example.village.model.VillageBorder;
import com.example.village.model.VillageBuilding;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Handles client-side (fake) border preview display.
 * Uses Player.sendBlockChange() to show border blocks without actually placing them.
 */
public final class BorderPreviewService {

    private final VillagePlugin plugin;
    private final VillageConfigManager configManager;
    private final BuildingService buildingService;
    private final Map<UUID, List<Location>> activePreviewBlocks = new HashMap<>();
    private final Map<UUID, List<Location>> activeBuildingPreviewBlocks = new HashMap<>();
    private final Map<UUID, Integer> previewTasks = new HashMap<>();

    public BorderPreviewService(VillagePlugin plugin, VillageConfigManager configManager, BuildingService buildingService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.buildingService = buildingService;
    }

    public void showPreview(Player player, VillageBorder border, World world) {
        clearVillageBorderPreview(player);

        Material previewMaterial = configManager.getPreviewBlock();
        Material buildingPreviewMaterial = configManager.getBuildingPreviewBorderBlock();
        Material buildingFinishedMaterial = configManager.getBuildingBorderBlock();
        BlockData previewData = previewMaterial.createBlockData();
        BlockData buildingPreviewData = buildingPreviewMaterial.createBlockData();
        BlockData buildingFinishedData = buildingFinishedMaterial.createBlockData();
        java.util.List<Location> shownBlocks = new java.util.ArrayList<>();

        renderBorderBlocks(player, world, previewData, border, shownBlocks);
        activePreviewBlocks.put(player.getUniqueId(), shownBlocks);

        java.util.List<Location> buildingShownBlocks = new java.util.ArrayList<>();
        renderBuildingBorders(player, world, buildingPreviewData, buildingFinishedData, buildingShownBlocks);
        activeBuildingPreviewBlocks.put(player.getUniqueId(), buildingShownBlocks);

        int durationTicks = configManager.getPreviewDuration() * 20;
        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> clearPreview(player), durationTicks).getTaskId();
        previewTasks.put(player.getUniqueId(), taskId);
    }

    private void clearVillageBorderPreview(Player player) {
        UUID uuid = player.getUniqueId();
        Integer taskId = previewTasks.remove(uuid);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }

        clearPreviewBlocks(player, activePreviewBlocks.remove(uuid));
        clearPreviewBlocks(player, activeBuildingPreviewBlocks.remove(uuid));
    }

    public void showPreviewWithMaterial(Player player, VillageBorder border, World world, Material material) {
        clearPreview(player);

        BlockData previewData = material.createBlockData();
        java.util.List<Location> shownBlocks = new java.util.ArrayList<>();

        renderBorderBlocks(player, world, previewData, border, shownBlocks);
        activePreviewBlocks.put(player.getUniqueId(), shownBlocks);

        int durationTicks = configManager.getPreviewDuration() * 20;
        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> clearPreview(player), durationTicks).getTaskId();
        previewTasks.put(player.getUniqueId(), taskId);
    }

    public void showPreviewMultiple(Player player, List<VillageBorder> borders, World world) {
        clearVillageBorderPreview(player);

        Material previewMaterial = configManager.getPreviewBlock();
        Material buildingPreviewMaterial = configManager.getBuildingPreviewBorderBlock();
        Material buildingFinishedMaterial = configManager.getBuildingBorderBlock();
        BlockData previewData = previewMaterial.createBlockData();
        BlockData buildingPreviewData = buildingPreviewMaterial.createBlockData();
        BlockData buildingFinishedData = buildingFinishedMaterial.createBlockData();
        java.util.List<Location> shownBlocks = new java.util.ArrayList<>();

        for (VillageBorder border : borders) {
            renderBorderBlocks(player, world, previewData, border, shownBlocks);
        }

        activePreviewBlocks.put(player.getUniqueId(), shownBlocks);

        java.util.List<Location> buildingShownBlocks = new java.util.ArrayList<>();
        renderBuildingBorders(player, world, buildingPreviewData, buildingFinishedData, buildingShownBlocks);
        activeBuildingPreviewBlocks.put(player.getUniqueId(), buildingShownBlocks);

        int durationTicks = configManager.getPreviewDuration() * 20;
        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> clearPreview(player), durationTicks).getTaskId();
        previewTasks.put(player.getUniqueId(), taskId);
    }

    public void showPreviewMultiple(Player player, List<VillageBorder> borders, List<VillageBorder> selectedBorders, World world) {
        clearVillageBorderPreview(player);

        Material previewMaterial = configManager.getPreviewBlock();
        Material selectedMaterial = configManager.getPreviewSelectionBlock();
        Material buildingPreviewMaterial = configManager.getBuildingPreviewBorderBlock();
        Material buildingFinishedMaterial = configManager.getBuildingBorderBlock();
        BlockData previewData = previewMaterial.createBlockData();
        BlockData selectedData = selectedMaterial.createBlockData();
        BlockData buildingPreviewData = buildingPreviewMaterial.createBlockData();
        BlockData buildingFinishedData = buildingFinishedMaterial.createBlockData();
        java.util.List<Location> shownBlocks = new java.util.ArrayList<>();
        Set<Integer> selectedIds = new HashSet<>();

        if (selectedBorders != null) {
            for (VillageBorder selected : selectedBorders) {
                selectedIds.add(selected.getId());
            }
        }

        for (VillageBorder border : borders) {
            BlockData material = selectedIds.contains(border.getId()) ? selectedData : previewData;
            renderBorderBlocks(player, world, material, border, shownBlocks);
        }

        activePreviewBlocks.put(player.getUniqueId(), shownBlocks);

        java.util.List<Location> buildingShownBlocks = new java.util.ArrayList<>();
        renderBuildingBorders(player, world, buildingPreviewData, buildingFinishedData, buildingShownBlocks);
        activeBuildingPreviewBlocks.put(player.getUniqueId(), buildingShownBlocks);

        int durationTicks = configManager.getPreviewDuration() * 20;
        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> clearPreview(player), durationTicks).getTaskId();
        previewTasks.put(player.getUniqueId(), taskId);
    }

    public void showPreviewMultipleForSeconds(Player player, List<VillageBorder> borders, World world, int seconds) {
        clearVillageBorderPreview(player);

        Material previewMaterial = configManager.getPreviewBlock();
        Material buildingPreviewMaterial = configManager.getBuildingPreviewBorderBlock();
        Material buildingFinishedMaterial = configManager.getBuildingBorderBlock();
        BlockData previewData = previewMaterial.createBlockData();
        BlockData buildingPreviewData = buildingPreviewMaterial.createBlockData();
        BlockData buildingFinishedData = buildingFinishedMaterial.createBlockData();
        java.util.List<Location> shownBlocks = new java.util.ArrayList<>();

        for (VillageBorder border : borders) {
            renderBorderBlocks(player, world, previewData, border, shownBlocks);
        }

        activePreviewBlocks.put(player.getUniqueId(), shownBlocks);

        java.util.List<Location> buildingShownBlocks = new java.util.ArrayList<>();
        renderBuildingBorders(player, world, buildingPreviewData, buildingFinishedData, buildingShownBlocks);
        activeBuildingPreviewBlocks.put(player.getUniqueId(), buildingShownBlocks);

        int durationTicks = Math.max(1, seconds) * 20;
        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> clearPreview(player), durationTicks).getTaskId();
        previewTasks.put(player.getUniqueId(), taskId);
    }

    public boolean hasActivePreview(Player player) {
        return activePreviewBlocks.containsKey(player.getUniqueId())
                || activeBuildingPreviewBlocks.containsKey(player.getUniqueId());
    }

    public void clearPreview(Player player) {
        UUID uuid = player.getUniqueId();

        Integer taskId = previewTasks.remove(uuid);
        if (taskId != null) {
            Bukkit.getScheduler().cancelTask(taskId);
        }

        clearPreviewBlocks(player, activePreviewBlocks.remove(uuid));
        clearPreviewBlocks(player, activeBuildingPreviewBlocks.remove(uuid));
    }

    public void showPermanentPreview(Player player, List<VillageBorder> borders, World world) {
        clearVillageBorderPreview(player);

        Material previewMaterial = configManager.getPreviewBlock();
        Material buildingPreviewMaterial = configManager.getBuildingPreviewBorderBlock();
        Material buildingFinishedMaterial = configManager.getBuildingBorderBlock();
        BlockData previewData = previewMaterial.createBlockData();
        BlockData buildingPreviewData = buildingPreviewMaterial.createBlockData();
        BlockData buildingFinishedData = buildingFinishedMaterial.createBlockData();
        java.util.List<Location> shownBlocks = new java.util.ArrayList<>();

        for (VillageBorder border : borders) {
            renderBorderBlocks(player, world, previewData, border, shownBlocks);
        }

        activePreviewBlocks.put(player.getUniqueId(), shownBlocks);

        java.util.List<Location> buildingShownBlocks = new java.util.ArrayList<>();
        renderBuildingBorders(player, world, buildingPreviewData, buildingFinishedData, buildingShownBlocks);
        activeBuildingPreviewBlocks.put(player.getUniqueId(), buildingShownBlocks);
    }

    public void clearAllPreviews() {
        Set<UUID> uuids = new HashSet<>();
        uuids.addAll(activePreviewBlocks.keySet());
        uuids.addAll(activeBuildingPreviewBlocks.keySet());

        for (UUID uuid : uuids) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                clearPreview(player);
            }
        }

        activePreviewBlocks.clear();
        activeBuildingPreviewBlocks.clear();

        for (int taskId : previewTasks.values()) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        previewTasks.clear();
    }

    private void renderBorderBlocks(Player player, World world, BlockData material, VillageBorder border, List<Location> shownBlocks) {
        for (int[] edge : border.getEdgeBlocks()) {
            int x = edge[0];
            int z = edge[1];
            int y = world.getHighestBlockYAt(x, z);
            Location loc = new Location(world, x, y, z);

            if (isUnderOpenSky(world, x, y, z)) {
                player.sendBlockChange(loc, material);
                shownBlocks.add(loc);
            }
        }
    }

    private void renderBuildingBorders(Player player, World world, BlockData previewData, BlockData completedData, List<Location> shownBlocks) {
        var villageOpt = plugin.getVillageManager().getPlayerVillage(player.getUniqueId());
        if (villageOpt.isEmpty()) {
            return;
        }

        for (VillageBuilding building : villageOpt.get().getBuildings()) {
            if (building.getLocation() == null || building.getLocation().getWorld() == null
                    || !building.getLocation().getWorld().equals(world)) {
                continue;
            }

            VillageBorder border = buildingService.getPreviewBorderForBuilding(building);
            if (border == null) {
                continue;
            }

            renderBorderBlocks(player, world, building.isCompleted() ? completedData : previewData, border, shownBlocks);
        }
    }

    private void clearPreviewBlocks(Player player, List<Location> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return;
        }

        if (!player.isOnline()) {
            return;
        }

        World world = player.getWorld();
        for (Location loc : blocks) {
            if (loc != null && loc.getWorld() != null && loc.getWorld().equals(world)) {
                player.sendBlockChange(loc, world.getBlockAt(loc).getBlockData());
            }
        }
    }

    private boolean isUnderOpenSky(World world, int x, int y, int z) {
        int highest = world.getHighestBlockYAt(x, z);
        return y >= highest;
    }
}
