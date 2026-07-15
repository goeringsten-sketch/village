package com.example.village.service;

import com.example.village.hook.WorldEditHook;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.util.*;

/**
 * Phase-1 Fundament-Stretching: füllt Lücken unter Schematic-Fundamenten bis zum Terrain.
 */
public final class FoundationStretchService {

    public record StretchResult(
            WorldEditHook.SchematicData schematicData,
            List<WorldEditHook.SchematicBlock> foundationPillars,
            int yOffset
    ) {}

    private FoundationStretchService() {}

    public static StretchResult apply(World world,
                                        Location origin,
                                        WorldEditHook.SchematicData data,
                                        Material foundationMaterial,
                                        int maxDepth) {
        if (world == null || origin == null || data == null || data.getBlocks().isEmpty()) {
            return new StretchResult(data, List.of(), 0);
        }

        int minDy = data.getBlocks().stream().mapToInt(WorldEditHook.SchematicBlock::dy).min().orElse(0);
        Map<Long, WorldEditHook.SchematicBlock> footprint = new HashMap<>();
        for (WorldEditHook.SchematicBlock block : data.getBlocks()) {
            if (block.dy() == minDy) {
                footprint.put(pack(block.dx(), block.dz()), block);
            }
        }
        if (footprint.isEmpty()) {
            return new StretchResult(data, List.of(), 0);
        }

        int ox = origin.getBlockX();
        int oy = origin.getBlockY();
        int oz = origin.getBlockZ();

        int minDelta = Integer.MAX_VALUE;
        Map<Long, Integer> terrainYByFoot = new HashMap<>();
        for (Map.Entry<Long, WorldEditHook.SchematicBlock> entry : footprint.entrySet()) {
            int dx = unpackX(entry.getKey());
            int dz = unpackZ(entry.getKey());
            int worldX = ox + dx;
            int worldZ = oz + dz;
            int schematicFootY = oy + minDy;
            int terrainY = findSolidY(world, worldX, schematicFootY, worldZ, maxDepth);
            if (terrainY == Integer.MIN_VALUE) {
                continue;
            }
            int delta = terrainY - schematicFootY;
            terrainYByFoot.put(entry.getKey(), terrainY);
            minDelta = Math.min(minDelta, delta);
        }

        if (minDelta == Integer.MAX_VALUE) {
            return new StretchResult(data, List.of(), 0);
        }

        List<WorldEditHook.SchematicBlock> pillars = new ArrayList<>();
        BlockData pillarData = foundationMaterial.createBlockData();
        for (Map.Entry<Long, Integer> entry : terrainYByFoot.entrySet()) {
            int dx = unpackX(entry.getKey());
            int dz = unpackZ(entry.getKey());
            int terrainY = entry.getValue();
            int schematicFootY = oy + minDy;
            int startY = schematicFootY + minDelta;
            for (int y = startY; y < terrainY; y++) {
                int relY = y - oy;
                pillars.add(new WorldEditHook.SchematicBlock(dx, relY, dz, pillarData));
            }
        }

        List<WorldEditHook.SchematicBlock> shifted = new ArrayList<>(data.getBlocks().size() + pillars.size());
        for (WorldEditHook.SchematicBlock block : data.getBlocks()) {
            shifted.add(new WorldEditHook.SchematicBlock(
                    block.dx(), block.dy() + minDelta, block.dz(), block.blockData()));
        }
        shifted.addAll(pillars);

        WorldEditHook.SchematicData adjusted = new WorldEditHook.SchematicData(
                shifted, data.getWidth(), data.getHeight() + Math.max(0, minDelta), data.getDepth(),
                data.getOriginOffsetX(), data.getOriginOffsetZ());

        return new StretchResult(adjusted, pillars, minDelta);
    }

    private static int findSolidY(World world, int x, int startY, int z, int maxDepth) {
        int minY = Math.max(world.getMinHeight(), startY - maxDepth);
        for (int y = startY; y >= minY; y--) {
            Block block = world.getBlockAt(x, y, z);
            if (block.getType().isSolid() && block.getType() != Material.BARRIER) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    private static long pack(int dx, int dz) {
        return ((long) dx << 32) | (dz & 0xFFFFFFFFL);
    }

    private static int unpackX(long packed) {
        return (int) (packed >> 32);
    }

    private static int unpackZ(long packed) {
        return (int) packed;
    }
}
