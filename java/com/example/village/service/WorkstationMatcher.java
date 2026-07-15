package com.example.village.service;

import com.example.village.VillagePlugin;
import com.example.village.model.BuildingDefinition;
import com.example.village.model.VillageBuilding;
import org.bukkit.Location;
import org.bukkit.Material;

public final class WorkstationMatcher {

    private WorkstationMatcher() {}

    public static String resolveWorkstationKey(VillageBuilding building,
                                               BuildingDefinition def,
                                               Location clickedLoc,
                                               Material material) {
        return resolveWorkstationKey(null, building, def, clickedLoc, material);
    }

    public static String resolveWorkstationKey(VillagePlugin plugin,
                                               VillageBuilding building,
                                               BuildingDefinition def,
                                               Location clickedLoc,
                                               Material material) {
        if (building == null || def == null || clickedLoc == null || material == null) return null;
        if (building.getLocation() == null || building.getLocation().getWorld() == null) return null;
        if (!building.getLocation().getWorld().equals(clickedLoc.getWorld())) return null;

        var allowed = plugin != null
                ? BuildingBoundsUtil.resolveAllowedWorkstationMaterials(plugin, building, def)
                : new java.util.HashSet<>(def.getPrimaryWorkstationBlocks());
        if (plugin == null) {
            for (BuildingDefinition.WorkstationSlot ws : def.getAdditionalWorkstations()) {
                allowed.add(ws.material());
            }
        }

        if (!allowed.contains(material)) return null;

        boolean inside = plugin != null
                ? BuildingBoundsUtil.isWithinBuilding(plugin, building, def, clickedLoc)
                : isInsideConfiguredArea(building, def, clickedLoc);
        if (!inside) return null;

        Location anchor = building.getLocation();
        if (def.getPrimaryWorkstationBlocks().contains(material)
                && anchor.getBlockX() == clickedLoc.getBlockX()
                && anchor.getBlockY() == clickedLoc.getBlockY()
                && anchor.getBlockZ() == clickedLoc.getBlockZ()) {
            return "workstation_primary";
        }

        return "workstation_" + material.name().toLowerCase();
    }

    public static boolean isInsideConfiguredArea(VillageBuilding building, BuildingDefinition def, Location clickedLoc) {
        Location anchor = building.getLocation();
        if (anchor == null || def.getArea() == null) return false;
        int dx = Math.abs(clickedLoc.getBlockX() - anchor.getBlockX());
        int dz = Math.abs(clickedLoc.getBlockZ() - anchor.getBlockZ());

        return switch (def.getArea().shape()) {
            case "circle" -> {
                int maxR = Math.max(1, def.getArea().maxRadius());
                yield (dx * dx + dz * dz) <= (maxR * maxR);
            }
            case "hollow_structure" -> {
                int r = Math.max(1, Math.max(def.getArea().maxWidth(), def.getArea().maxDepth()) / 2);
                yield dx <= r && dz <= r;
            }
            default -> {
                int halfW = Math.max(1, def.getArea().maxWidth() / 2);
                int halfD = Math.max(1, def.getArea().maxDepth() / 2);
                yield dx <= halfW && dz <= halfD;
            }
        };
    }
}
