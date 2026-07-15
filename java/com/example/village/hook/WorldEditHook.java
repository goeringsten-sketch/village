package com.example.village.hook;

import com.example.village.service.SchematicMetaLoader;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public final class WorldEditHook {

    private boolean available;

    public void setup(Logger logger) {
        available = Bukkit.getPluginManager().getPlugin("WorldEdit") != null;
        if (available) {
            logger.info("WorldEdit-Integration aktiviert.");
        } else {
            logger.info("WorldEdit nicht gefunden - Schematic-Features deaktiviert.");
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * Represents a single non-air block from a loaded schematic with relative offset and block data.
     */
    public record SchematicBlock(int dx, int dy, int dz, BlockData blockData) {}

    /**
     * Data holder for a loaded schematic with block data and dimensions.
     */
    public static class SchematicData {
        private final List<SchematicBlock> blocks;
        private final int width;
        private final int height;
        private final int depth;
        private final int originOffsetX;  // Offset from schematic min to origin point (where player saved from)
        private final int originOffsetZ;

        public SchematicData(List<SchematicBlock> blocks, int width, int height, int depth) {
            this(blocks, width, height, depth, 0, 0);
        }

        public SchematicData(List<SchematicBlock> blocks, int width, int height, int depth, 
                           int originOffsetX, int originOffsetZ) {
            this.blocks = blocks;
            this.width = width;
            this.height = height;
            this.depth = depth;
            this.originOffsetX = originOffsetX;
            this.originOffsetZ = originOffsetZ;
        }

        public List<SchematicBlock> getBlocks() { return blocks; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }
        public int getDepth() { return depth; }
        public int getOriginOffsetX() { return originOffsetX; }
        public int getOriginOffsetZ() { return originOffsetZ; }
    }

    /**
     * Loads a schematic file and rotates it using WorldEdit's native rotation via BlockState transformation.
     * Direction: N=0°, E=90°, S=180°, W=270°
     * Returns null if WorldEdit is not available or the file cannot be loaded.
     */
    public SchematicData loadAndRotateSchematicData(File schematicFile, String direction) {
        if (!isAvailable() || !schematicFile.exists()) {
            return null;
        }

        try {
            com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat format =
                    com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats.findByFile(schematicFile);
            if (format == null) return null;

            try (com.sk89q.worldedit.extent.clipboard.io.ClipboardReader reader =
                         format.getReader(new FileInputStream(schematicFile))) {
                com.sk89q.worldedit.extent.clipboard.Clipboard clipboard = reader.read();

                com.sk89q.worldedit.math.BlockVector3 min = clipboard.getMinimumPoint();
                com.sk89q.worldedit.math.BlockVector3 max = clipboard.getMaximumPoint();
                com.sk89q.worldedit.math.BlockVector3 origin = clipboard.getOrigin();

                List<SchematicBlock> blocks = new ArrayList<>();

                for (int x = min.x(); x <= max.x(); x++) {
                    for (int y = min.y(); y <= max.y(); y++) {
                        for (int z = min.z(); z <= max.z(); z++) {
                            com.sk89q.worldedit.world.block.BlockState state =
                                    clipboard.getBlock(com.sk89q.worldedit.math.BlockVector3.at(x, y, z));
                            BlockData blockData = com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(state);
                            Material mat = blockData.getMaterial();

                            // Skip air blocks
                            if (mat == Material.AIR || mat == Material.CAVE_AIR
                                    || mat == Material.VOID_AIR) {
                                continue;
                            }

                            // Calculate relative coordinates
                            int relX = x - origin.x();
                            int relY = y - origin.y();
                            int relZ = z - origin.z();
                            
                            // Apply coordinate rotation based on direction
                            int[] rotated = rotateCoordinates(relX, relY, relZ, direction);
                            
                            // Apply BlockState rotation (using WorldEdit's BlockState string rotation)
                            BlockData rotatedBlockData = rotateBlockStateViaString(state, direction);
                            
                            blocks.add(new SchematicBlock(rotated[0], rotated[1], rotated[2], rotatedBlockData));
                        }
                    }
                }

                int width = max.x() - min.x() + 1;
                int height = max.y() - min.y() + 1;
                int depth = max.z() - min.z() + 1;

                // Calculate origin offset relative to schematic minimum point
                // This tells us where the player saved the schematic from
                int originOffsetX = origin.x() - min.x();
                int originOffsetZ = origin.z() - min.z();

                return new SchematicData(blocks, width, height, depth, originOffsetX, originOffsetZ);
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("Fehler beim Laden und Rotieren der Schematic: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Rotates a WorldEdit BlockState using BlockData API.
     * Converts coordinates AND rotates block properties using Bukkit's Directional/Rotatable interfaces.
     * Direction: N=0°, E=90°, S=180°, W=270°
     */
    private BlockData rotateBlockStateViaString(com.sk89q.worldedit.world.block.BlockState state, String direction) {
        if (direction == null || direction.equals("N")) {
            return com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(state);
        }

        try {
            // Convert WorldEdit BlockState to Bukkit BlockData
            BlockData blockData = com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(state);
            
            // Rotate Directional blocks (doors, stairs, torches, wall signs, etc.)
            if (blockData instanceof org.bukkit.block.data.Directional directional) {
                org.bukkit.block.BlockFace oldFacing = directional.getFacing();
                org.bukkit.block.BlockFace newFacing = rotateBlockFace(oldFacing, direction);
                directional.setFacing(newFacing);
                return directional;
            }
            
            // Rotate Rotatable blocks (logs, vines, etc.)
            if (blockData instanceof org.bukkit.block.data.Rotatable rotatable) {
                org.bukkit.block.BlockFace oldRotation = rotatable.getRotation();
                org.bukkit.block.BlockFace newRotation = rotateBlockFace(oldRotation, direction);
                rotatable.setRotation(newRotation);
                return rotatable;
            }
            
            // Handle MultipleFacing blocks (glass pane, iron bars, etc.)
            if (blockData instanceof org.bukkit.block.data.MultipleFacing multipleFacing) {
                return rotateMultipleFacingBlock(multipleFacing, direction);
            }
            
            // For other blocks without directional properties, return as-is
            return blockData;
            
        } catch (Exception e) {
            Bukkit.getLogger().warning("Fehler beim Rotieren von BlockData: " + e.getMessage());
            // Return original on error
            return com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(state);
        }
    }

    /**
     * Rotates MultipleFacing blocks (glass pane connections, etc.)
     */
    private org.bukkit.block.data.MultipleFacing rotateMultipleFacingBlock(
            org.bukkit.block.data.MultipleFacing block, String direction) {
        
        java.util.Set<org.bukkit.block.BlockFace> faces = 
                new java.util.HashSet<>(block.getFaces());
        
        java.util.Set<org.bukkit.block.BlockFace> newFaces = new java.util.HashSet<>();
        for (org.bukkit.block.BlockFace face : faces) {
            newFaces.add(rotateBlockFace(face, direction));
        }
        
        // Clear old faces and add rotated ones
        for (org.bukkit.block.BlockFace face : faces) {
            block.setFace(face, false);
        }
        for (org.bukkit.block.BlockFace face : newFaces) {
            block.setFace(face, true);
        }
        
        return block;
    }

    /**
     * Rotates a BlockFace based on the direction.
     * N = 0°, E = 90°, S = 180°, W = 270°
     * 
     * Bukkit's BlockFace coordinates:
     * NORTH = -Z (away), SOUTH = +Z (towards)
     * EAST = +X (right), WEST = -X (left)
     */
    private org.bukkit.block.BlockFace rotateBlockFace(org.bukkit.block.BlockFace face, String direction) {
        return switch (direction) {
            case "E" -> {  // 90° clockwise (looking from above Y+)
                yield switch (face) {
                    case NORTH -> org.bukkit.block.BlockFace.EAST;      // -Z → +X
                    case EAST -> org.bukkit.block.BlockFace.SOUTH;      // +X → +Z
                    case SOUTH -> org.bukkit.block.BlockFace.WEST;      // +Z → -X
                    case WEST -> org.bukkit.block.BlockFace.NORTH;      // -X → -Z
                    case UP, DOWN -> face;
                    default -> face;
                };
            }
            case "S" -> {  // 180°
                yield switch (face) {
                    case NORTH -> org.bukkit.block.BlockFace.SOUTH;
                    case EAST -> org.bukkit.block.BlockFace.WEST;
                    case SOUTH -> org.bukkit.block.BlockFace.NORTH;
                    case WEST -> org.bukkit.block.BlockFace.EAST;
                    case UP, DOWN -> face;
                    default -> face;
                };
            }
            case "W" -> {  // 270° CW (90° CCW)
                yield switch (face) {
                    case NORTH -> org.bukkit.block.BlockFace.WEST;      // -Z → -X
                    case EAST -> org.bukkit.block.BlockFace.NORTH;      // +X → -Z
                    case SOUTH -> org.bukkit.block.BlockFace.EAST;      // +Z → +X
                    case WEST -> org.bukkit.block.BlockFace.SOUTH;      // -X → +Z
                    case UP, DOWN -> face;
                    default -> face;
                };
            }
            default -> face;  // N (no rotation)
        };
    }

    /**
     * Rotates coordinates based on direction.
     * N = 0°, E = 90°, S = 180°, W = 270°
     */
    private int[] rotateCoordinates(int x, int y, int z, String direction) {
        return switch (direction) {
            case "E" -> new int[]{-z, y, x};      // 90° clockwise
            case "S" -> new int[]{-x, y, -z};     // 180°
            case "W" -> new int[]{z, y, -x};      // 270° CW
            default -> new int[]{x, y, z};        // N (no rotation)
        };
    }

    /**
     * Loads a schematic file using the WorldEdit API and returns full block data.
     * Air blocks are skipped - existing world blocks remain unchanged.
     * Returns null if WorldEdit is not available or the file cannot be loaded.
     */
    public SchematicData loadSchematicData(File schematicFile) {
        return loadAndRotateSchematicData(schematicFile, "N");  // Load without rotation
    }

    /**
     * Loads a schematic and returns a map of relative positions to materials.
     * Air blocks are skipped.
     * @deprecated Use {@link #loadSchematicData(File)} instead for full block data.
     */
    @Deprecated
    public Map<int[], Material> loadSchematic(File schematicFile) {
        SchematicData data = loadSchematicData(schematicFile);
        if (data == null) return new HashMap<>();

        Map<int[], Material> result = new HashMap<>();
        for (SchematicBlock block : data.getBlocks()) {
            result.put(new int[]{block.dx(), block.dy(), block.dz()},
                    block.blockData().getMaterial());
        }
        return result;
    }

    /**
     * Validates that the blocks at the given location match the schematic.
     * Air blocks in the schematic are ignored.
     */
    public boolean validateBuilding(World world, Location origin, Map<int[], Material> schematic) {
        for (Map.Entry<int[], Material> entry : schematic.entrySet()) {
            int[] offset = entry.getKey();
            Material expected = entry.getValue();

            if (expected == Material.AIR || expected == Material.CAVE_AIR
                    || expected == Material.VOID_AIR) {
                continue;
            }

            Block block = world.getBlockAt(
                    origin.getBlockX() + offset[0],
                    origin.getBlockY() + offset[1],
                    origin.getBlockZ() + offset[2]
            );

            if (block.getType() != expected) {
                return false;
            }
        }
        return true;
    }

    /**
     * Validates building using SchematicData (full block data).
     * Air blocks are already excluded from SchematicData.
     */
    public boolean validateBuilding(World world, Location origin, SchematicData schematicData) {
        for (SchematicBlock sb : schematicData.getBlocks()) {
            Block block = world.getBlockAt(
                    origin.getBlockX() + sb.dx(),
                    origin.getBlockY() + sb.dy(),
                    origin.getBlockZ() + sb.dz()
            );
            if (block.getType() != sb.blockData().getMaterial()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Hybrid-Validierung: STRUCTURE-Blöcke müssen exakt passen; DECORATION darf leer oder Whitelist sein.
     */
    public boolean validateHybridBuilding(World world, Location origin, SchematicData schematicData,
                                          SchematicMetaLoader.SchematicMeta meta) {
        for (SchematicBlock sb : schematicData.getBlocks()) {
            SchematicMetaLoader.BlockRole role = meta.roleFor(sb.dx(), sb.dy(), sb.dz());
            Block block = world.getBlockAt(
                    origin.getBlockX() + sb.dx(),
                    origin.getBlockY() + sb.dy(),
                    origin.getBlockZ() + sb.dz()
            );
            Material expected = sb.blockData().getMaterial();
            Material actual = block.getType();

            if (role == SchematicMetaLoader.BlockRole.STRUCTURE) {
                if (actual != expected) {
                    return false;
                }
                continue;
            }

            if (actual == expected) {
                continue;
            }
            if (actual.isAir()) {
                continue;
            }
            if (!meta.decorationWhitelist().isEmpty() && meta.decorationWhitelist().contains(actual)) {
                continue;
            }
            return false;
        }
        return true;
    }

    /**
     * Gets a preview of block positions from a schematic for display.
     */
    public Map<Location, Material> getPreviewBlocks(World world, Location origin,
                                                     Map<int[], Material> schematic) {
        Map<Location, Material> preview = new HashMap<>();
        for (Map.Entry<int[], Material> entry : schematic.entrySet()) {
            int[] offset = entry.getKey();
            Material mat = entry.getValue();
            if (mat == Material.AIR || mat == Material.CAVE_AIR || mat == Material.VOID_AIR) {
                continue;
            }
            Location loc = origin.clone().add(offset[0], offset[1], offset[2]);
            preview.put(loc, mat);
        }
        return preview;
    }

    /**
     * Saves a cuboid region between pos1 and pos2 relative to an origin location as a Sponge schematic.
     * Returns true on success, false on failure.
     */
    public boolean saveSchematic(Location pos1, Location pos2, Location origin, File file) {
        if (!isAvailable()) return false;
        try {
            com.sk89q.worldedit.world.World weWorld = com.sk89q.worldedit.bukkit.BukkitAdapter.adapt(pos1.getWorld());
            com.sk89q.worldedit.math.BlockVector3 p1 = com.sk89q.worldedit.math.BlockVector3.at(pos1.getBlockX(), pos1.getBlockY(), pos1.getBlockZ());
            com.sk89q.worldedit.math.BlockVector3 p2 = com.sk89q.worldedit.math.BlockVector3.at(pos2.getBlockX(), pos2.getBlockY(), pos2.getBlockZ());
            
            com.sk89q.worldedit.regions.CuboidRegion region = new com.sk89q.worldedit.regions.CuboidRegion(weWorld, p1, p2);
            
            com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard clipboard = new com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard(region);
            clipboard.setOrigin(com.sk89q.worldedit.math.BlockVector3.at(origin.getBlockX(), origin.getBlockY(), origin.getBlockZ()));
            
            com.sk89q.worldedit.function.operation.ForwardExtentCopy forwardExtentCopy = 
                new com.sk89q.worldedit.function.operation.ForwardExtentCopy(
                    weWorld, region, clipboard, region.getMinimumPoint()
                );
            com.sk89q.worldedit.function.operation.Operations.complete(forwardExtentCopy);
            
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(file)) {
                try (com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter writer = 
                     com.sk89q.worldedit.extent.clipboard.io.BuiltInClipboardFormat.SPONGE_SCHEMATIC.getWriter(fos)) {
                    writer.write(clipboard);
                }
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}

