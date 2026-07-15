package com.example.village.service;

import com.example.village.VillagePlugin;
import com.example.village.hook.WorldEditHook;
import com.example.village.model.BuildingDefinition;
import com.example.village.model.VillageBuilding;
import org.bukkit.Location;
import org.bukkit.Material;

import java.io.File;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * Ermittelt ob eine Welt-Position innerhalb eines Gebäudes liegt.
 * Berücksichtigt Config-Area, Schematic-Bounding-Box und Fallback-Radius.
 */
public final class BuildingBoundsUtil {

    private static final int SCHEMATIC_FALLBACK_RADIUS = 16;
    private static final int SCHEMATIC_FALLBACK_HEIGHT = 24;

    private static final Set<Material> KNOWN_WORKSTATION_MATERIALS = EnumSet.of(
            Material.CARTOGRAPHY_TABLE, Material.LECTERN, Material.COMPOSTER,
            Material.BLAST_FURNACE, Material.SMOKER, Material.FURNACE,
            Material.SMITHING_TABLE, Material.GRINDSTONE, Material.STONECUTTER,
            Material.LOOM, Material.BREWING_STAND, Material.BARREL,
            Material.CAULDRON, Material.WATER_CAULDRON, Material.LAVA_CAULDRON,
            Material.BELL, Material.BOOKSHELF, Material.ENCHANTING_TABLE,
            Material.ANVIL, Material.CHIPPED_ANVIL, Material.DAMAGED_ANVIL,
            Material.CHEST, Material.TRAPPED_CHEST, Material.BEEHIVE, Material.BEE_NEST
    );

    static {
        for (Material material : Material.values()) {
            if (material.name().endsWith("_BED")) {
                KNOWN_WORKSTATION_MATERIALS.add(material);
            }
        }
    }

    public record RelativeBounds(int minDx, int maxDx, int minDy, int maxDy, int minDz, int maxDz) {
        boolean contains(int dx, int dy, int dz) {
            return dx >= minDx && dx <= maxDx
                    && dy >= minDy && dy <= maxDy
                    && dz >= minDz && dz <= maxDz;
        }
    }

    private BuildingBoundsUtil() {}

    public static boolean isWithinBuilding(VillagePlugin plugin,
                                           VillageBuilding building,
                                           BuildingDefinition def,
                                           Location clickedLoc) {
        if (building == null || def == null || clickedLoc == null) return false;
        Location anchor = building.getLocation();
        if (anchor == null || anchor.getWorld() == null) return false;
        if (!anchor.getWorld().equals(clickedLoc.getWorld())) return false;

        int dx = clickedLoc.getBlockX() - anchor.getBlockX();
        int dy = clickedLoc.getBlockY() - anchor.getBlockY();
        int dz = clickedLoc.getBlockZ() - anchor.getBlockZ();

        if (def.getArea() != null && WorkstationMatcher.isInsideConfiguredArea(building, def, clickedLoc)) {
            return true;
        }

        RelativeBounds schematicBounds = getSchematicBounds(plugin, building, def);
        if (schematicBounds != null && schematicBounds.contains(dx, dy, dz)) {
            return true;
        }

        if (def.isSchematicBased()) {
            return Math.abs(dx) <= SCHEMATIC_FALLBACK_RADIUS
                    && Math.abs(dz) <= SCHEMATIC_FALLBACK_RADIUS
                    && Math.abs(dy) <= SCHEMATIC_FALLBACK_HEIGHT;
        }

        int radius = BlockCheckValidator.radiusFromDefinition(def);
        if (radius <= 0) radius = 8;
        return Math.abs(dx) <= radius && Math.abs(dz) <= radius && Math.abs(dy) <= radius * 2;
    }

    public static RelativeBounds getSchematicBounds(VillagePlugin plugin,
                                                    VillageBuilding building,
                                                    BuildingDefinition def) {
        if (plugin == null || building == null || def == null || !def.isSchematicBased()) {
            return null;
        }

        WorldEditHook worldEditHook = plugin.getWorldEditHook();
        if (worldEditHook == null || !worldEditHook.isAvailable()) {
            return null;
        }

        String schematicName = building.getSchematicName();
        if (schematicName == null || schematicName.isBlank()) {
            schematicName = def.getSchematic();
        }
        if (schematicName == null || schematicName.isBlank()) {
            return null;
        }

        File schematicFile = new File(plugin.getDataFolder(), "schematics/" + schematicName);
        if (!schematicFile.exists()) {
            return null;
        }

        WorldEditHook.SchematicData data = worldEditHook.loadAndRotateSchematicData(
                schematicFile, building.getDirection() != null ? building.getDirection() : "N");
        if (data == null || data.getBlocks().isEmpty()) {
            return null;
        }

        int minDx = Integer.MAX_VALUE, maxDx = Integer.MIN_VALUE;
        int minDy = Integer.MAX_VALUE, maxDy = Integer.MIN_VALUE;
        int minDz = Integer.MAX_VALUE, maxDz = Integer.MIN_VALUE;

        for (WorldEditHook.SchematicBlock block : data.getBlocks()) {
            minDx = Math.min(minDx, block.dx());
            maxDx = Math.max(maxDx, block.dx());
            minDy = Math.min(minDy, block.dy());
            maxDy = Math.max(maxDy, block.dy());
            minDz = Math.min(minDz, block.dz());
            maxDz = Math.max(maxDz, block.dz());
        }

        if (minDx == Integer.MAX_VALUE) {
            return null;
        }

        return new RelativeBounds(minDx, maxDx, minDy, maxDy, minDz, maxDz);
    }

    public static Set<Material> resolveAllowedWorkstationMaterials(VillagePlugin plugin,
                                                                   VillageBuilding building,
                                                                   BuildingDefinition def) {
        Set<Material> allowed = new HashSet<>(def.getPrimaryWorkstationBlocks());
        for (BuildingDefinition.WorkstationSlot ws : def.getAdditionalWorkstations()) {
            allowed.add(ws.material());
        }
        if (!allowed.isEmpty()) {
            return allowed;
        }

        if (!def.isSchematicBased() || plugin == null) {
            return allowed;
        }

        WorldEditHook worldEditHook = plugin.getWorldEditHook();
        if (worldEditHook == null || !worldEditHook.isAvailable()) {
            return allowed;
        }

        String schematicName = building.getSchematicName();
        if (schematicName == null || schematicName.isBlank()) {
            schematicName = def.getSchematic();
        }
        if (schematicName == null || schematicName.isBlank()) {
            return allowed;
        }

        File schematicFile = new File(plugin.getDataFolder(), "schematics/" + schematicName);
        if (!schematicFile.exists()) {
            return allowed;
        }

        WorldEditHook.SchematicData data = worldEditHook.loadAndRotateSchematicData(
                schematicFile, building.getDirection() != null ? building.getDirection() : "N");
        if (data == null) {
            return allowed;
        }

        for (WorldEditHook.SchematicBlock block : data.getBlocks()) {
            Material material = block.blockData().getMaterial();
            if (isKnownWorkstationMaterial(material)) {
                allowed.add(material);
            }
        }

        return allowed;
    }

    public static boolean isKnownWorkstationMaterial(Material material) {
        if (material == null || !material.isBlock()) return false;
        if (KNOWN_WORKSTATION_MATERIALS.contains(material)) return true;
        return material.name().endsWith("_BED");
    }
}
