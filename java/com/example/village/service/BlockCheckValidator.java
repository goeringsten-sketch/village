package com.example.village.service;

import com.example.village.model.BuildingDefinition;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Prüft Gebäude im BLOCK_CHECK-Modus (kein Schematic).
 *
 * Wird von BuildingService aufgerufen wenn validation_mode: block_check.
 * Für Pfade übernimmt PathService die Validierung (Walk-Session).
 *
 * Prüft:
 * 1. required_blocks: bestimmte Blöcke müssen mindestens N-mal im Bereich vorkommen
 * 2. required_block_percentage: X% der Oberfläche muss aus einem bestimmten Block bestehen
 * 3. hollow_structure: Hohlraum mit Skelett- und Dach-Validierung (für Baracke/Wohnhaus)
 */
public final class BlockCheckValidator {

    private static final int MAX_ROOF_SCAN_HEIGHT = 6;
    private static final double MIN_WALL_COVERAGE = 0.75;

    private BlockCheckValidator() {}

    public record ValidationResult(boolean valid, String failReason) {
        public static ValidationResult ok()             { return new ValidationResult(true, null); }
        public static ValidationResult fail(String msg) { return new ValidationResult(false, msg); }
    }

    /**
     * Validiert ein Gebäude anhand seiner BuildingDefinition im registrierten Bereich.
     *
     * @param def      BuildingDefinition (enthält required_blocks, required_block_percentage, area)
     * @param origin   Ankerpunkt / WB-Position des Gebäudes
     * @param radius   Suchradius in Blöcken (basierend auf area.maxRadius oder max(width,depth)/2)
     */
    public static ValidationResult validate(BuildingDefinition def, Location origin, int radius) {
        World world = origin.getWorld();
        if (world == null) return ValidationResult.fail("Welt nicht geladen.");

        int ox = origin.getBlockX(), oy = origin.getBlockY(), oz = origin.getBlockZ();

        if ("hollow_structure".equals(def.getArea() != null ? def.getArea().shape() : null)) {
            return validateHollow(def, world, ox, oy, oz, radius);
        }

        Map<Material, Integer> counts = countBlocksInRadius(world, ox, oy, oz, radius);

        for (Map.Entry<Material, Integer> req : def.getRequiredBlocks().entrySet()) {
            int found = counts.getOrDefault(req.getKey(), 0);
            if (found < req.getValue()) {
                return ValidationResult.fail(
                    "Zu wenig §e" + req.getKey().name() + "§c: §e" + found
                    + "§c gefunden, §e" + req.getValue() + "§c benötigt.");
            }
        }

        if (def.getRequiredBlockForPercentage() != null && def.getRequiredBlockPercentage() > 0) {
            int totalSurface = 0, matching = 0;
            for (int x = ox - radius; x <= ox + radius; x++) {
                for (int z = oz - radius; z <= oz + radius; z++) {
                    int sy = world.getHighestBlockYAt(x, z);
                    Block surface = world.getBlockAt(x, sy, z);
                    if (surface.getLightFromSky() > 0 || sy == world.getHighestBlockYAt(x, z)) {
                        totalSurface++;
                        if (surface.getType() == def.getRequiredBlockForPercentage()) matching++;
                    }
                }
            }
            if (totalSurface > 0) {
                int pct = (int) ((matching * 100.0) / totalSurface);
                if (pct < def.getRequiredBlockPercentage()) {
                    return ValidationResult.fail(
                        "Zu wenig §e" + def.getRequiredBlockForPercentage().name()
                        + "§c auf der Oberfläche: §e" + pct + "%§c (benötigt §e"
                        + def.getRequiredBlockPercentage() + "%§c).");
                }
            }
        }

        ValidationResult workstationResult = validateWorkstationBlocks(def, world, ox, oy, oz, radius);
        if (!workstationResult.valid()) {
            return workstationResult;
        }

        return ValidationResult.ok();
    }

    /**
     * Prüft einen zusammenhängenden Hohlraum inkl. Skelett (Wände/Boden) und Dach über WBs/Betten.
     */
    private static ValidationResult validateHollow(BuildingDefinition def, World world,
                                                    int ox, int oy, int oz, int radius) {
        int minVol = def.getArea() != null ? def.getArea().minInteriorVolume() : 8;

        Set<String> interior = floodFillInterior(world, ox, oy, oz, radius);
        if (interior.isEmpty()) {
            return ValidationResult.fail("Kein Innenraum am Ankerpunkt gefunden. Baue einen geschlossenen Raum.");
        }
        if (interior.size() < minVol) {
            return ValidationResult.fail("Innenraum zu klein: §e" + interior.size()
                + "§c Luftblöcke (min §e" + minVol + "§c).");
        }

        ValidationResult skeleton = validateSkeleton(world, interior);
        if (!skeleton.valid()) return skeleton;

        ValidationResult roof = validateRoofCoverage(def, world, ox, oy, oz, radius);
        if (!roof.valid()) return roof;

        return ValidationResult.ok();
    }

    /** Flood-Fill: zusammenhängender Luft/Hohlraum ab Anker (oder angrenzender Luft). */
    static Set<String> floodFillInterior(World world, int ox, int oy, int oz, int radius) {
        Set<String> interior = new HashSet<>();
        ArrayDeque<long[]> queue = new ArrayDeque<>();

        int minY = Math.max(world.getMinHeight(), oy - 1);
        int maxY = Math.min(world.getMaxHeight() - 1, oy + radius * 2);

        if (isInteriorAir(world, ox, oy, oz)) {
            queue.add(new long[]{ox, oy, oz});
        } else {
            for (int[] offset : new int[][]{{0, 1, 0}, {0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}}) {
                int nx = ox + offset[0], ny = oy + offset[1], nz = oz + offset[2];
                if (Math.abs(nx - ox) > radius || Math.abs(nz - oz) > radius) continue;
                if (ny < minY || ny > maxY) continue;
                if (isInteriorAir(world, nx, ny, nz)) {
                    queue.add(new long[]{nx, ny, nz});
                }
            }
        }

        while (!queue.isEmpty()) {
            long[] pos = queue.poll();
            String key = blockKey((int) pos[0], (int) pos[1], (int) pos[2]);
            if (!interior.add(key)) continue;

            for (int[] offset : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
                int nx = (int) pos[0] + offset[0];
                int ny = (int) pos[1] + offset[1];
                int nz = (int) pos[2] + offset[2];
                if (Math.abs(nx - ox) > radius || Math.abs(nz - oz) > radius) continue;
                if (ny < minY || ny > maxY) continue;
                if (isInteriorAir(world, nx, ny, nz) && !interior.contains(blockKey(nx, ny, nz))) {
                    queue.add(new long[]{nx, ny, nz});
                }
            }
        }

        return interior;
    }

    /** Prüft Boden und Wand-Umschließung des Innenraums. */
    static ValidationResult validateSkeleton(World world, Set<String> interior) {
        int missingFloor = 0;
        int boundaryFaces = 0;
        int solidBoundaryFaces = 0;

        for (String key : interior) {
            int[] pos = parseBlockKey(key);
            int x = pos[0], y = pos[1], z = pos[2];

            Block below = world.getBlockAt(x, y - 1, z);
            if (!isStructuralSolid(below.getType())) {
                missingFloor++;
            }

            for (int[] offset : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}}) {
                String neighborKey = blockKey(x + offset[0], y + offset[1], z + offset[2]);
                if (!interior.contains(neighborKey)) {
                    boundaryFaces++;
                    Material neighbor = world.getBlockAt(x + offset[0], y + offset[1], z + offset[2]).getType();
                    if (isStructuralSolid(neighbor)) {
                        solidBoundaryFaces++;
                    }
                }
            }
        }

        if (missingFloor > interior.size() * 0.15) {
            return ValidationResult.fail("Der Innenraum hat keinen durchgehenden Boden (§e"
                + missingFloor + "§c offene Stellen).");
        }

        if (boundaryFaces == 0) {
            return ValidationResult.fail("Keine Wände um den Innenraum erkannt.");
        }

        double coverage = (double) solidBoundaryFaces / boundaryFaces;
        if (coverage < MIN_WALL_COVERAGE) {
            int pct = (int) (coverage * 100);
            int required = (int) (MIN_WALL_COVERAGE * 100);
            return ValidationResult.fail("Wände unvollständig: §e" + pct
                + "%§c Umschließung (min §e" + required + "%§c). Türen/Fenster sind erlaubt.");
        }

        return ValidationResult.ok();
    }

    /** Prüft Regenschutz über Betten und primären Workstation-Blöcken im Gebäudebereich. */
    static ValidationResult validateRoofCoverage(BuildingDefinition def, World world,
                                                  int ox, int oy, int oz, int radius) {
        Set<Material> protectedMaterials = new HashSet<>(def.getPrimaryWorkstationBlocks());
        protectedMaterials.add(Material.WHITE_BED);
        for (Material m : Material.values()) {
            if (m.name().endsWith("_BED")) protectedMaterials.add(m);
        }

        int minY = Math.max(world.getMinHeight(), oy - radius);
        int maxY = Math.min(world.getMaxHeight() - 1, oy + radius);

        for (int x = ox - radius; x <= ox + radius; x++) {
            for (int z = oz - radius; z <= oz + radius; z++) {
                if (!isInsideConfiguredArea(def, ox, oz, x, z, radius)) continue;
                for (int y = minY; y <= maxY; y++) {
                    Material type = world.getBlockAt(x, y, z).getType();
                    if (!protectedMaterials.contains(type)) continue;
                    if (!hasRoofCover(world, x, y, z)) {
                        String label = type.name().endsWith("_BED") ? "Bett" : "Arbeitsblock";
                        return ValidationResult.fail("Kein Dach über dem §e" + label
                            + "§c bei §e" + x + ", " + y + ", " + z + "§c. Baue einen Regenschutz darüber.");
                    }
                }
            }
        }

        return ValidationResult.ok();
    }

    public static boolean hasRoofCover(World world, int x, int y, int z) {
        Block block = world.getBlockAt(x, y, z);
        if (block.getLightFromSky() == 0) {
            return true;
        }
        for (int dy = 1; dy <= MAX_ROOF_SCAN_HEIGHT; dy++) {
            if (isStructuralSolid(world.getBlockAt(x, y + dy, z).getType())) {
                return true;
            }
        }
        return false;
    }

    static boolean isInteriorAir(World world, int x, int y, int z) {
        Material type = world.getBlockAt(x, y, z).getType();
        return type == Material.AIR || type == Material.CAVE_AIR || type == Material.VOID_AIR;
    }

    public static boolean isStructuralSolid(Material material) {
        if (material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR) {
            return false;
        }
        if (Tag.FENCE_GATES.isTagged(material) || Tag.DOORS.isTagged(material) || Tag.TRAPDOORS.isTagged(material)) {
            return true;
        }
        return material.isSolid();
    }

    public static String blockKey(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    public static int[] parseBlockKey(String key) {
        String[] parts = key.split(",");
        return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2])};
    }

    private static Map<Material, Integer> countBlocksInRadius(World w, int ox, int oy, int oz, int r) {
        Map<Material, Integer> counts = new EnumMap<>(Material.class);
        for (int x = ox - r; x <= ox + r; x++)
            for (int y = Math.max(w.getMinHeight(), oy - r); y <= Math.min(w.getMaxHeight() - 1, oy + r * 2); y++)
                for (int z = oz - r; z <= oz + r; z++) {
                    Material m = w.getBlockAt(x, y, z).getType();
                    if (m != Material.AIR && m != Material.CAVE_AIR)
                        counts.merge(m, 1, Integer::sum);
                }
        return counts;
    }

    private static ValidationResult validateWorkstationBlocks(BuildingDefinition def, World world,
                                                              int ox, int oy, int oz, int radius) {
        if (def.getPrimaryWorkstationBlocks().isEmpty() && def.getAdditionalWorkstations().isEmpty()) {
            return ValidationResult.ok();
        }

        if (!def.getPrimaryWorkstationBlocks().isEmpty()) {
            Material originType = world.getBlockAt(ox, oy, oz).getType();
            if (!def.getPrimaryWorkstationBlocks().contains(originType)) {
                return ValidationResult.fail("Der gewählte Bauort muss einen der angegebenen Workstation-Blöcke auf der Ankerposition enthalten: "
                    + def.getPrimaryWorkstationBlocks().stream().map(Material::name).reduce((a, b) -> a + ", " + b).orElse("?") + ".");
            }
        }

        if (!def.getAdditionalWorkstations().isEmpty()) {
            for (BuildingDefinition.WorkstationSlot slot : def.getAdditionalWorkstations()) {
                int count = countBlocksInArea(world, ox, oy, oz, radius, slot.material(), def);
                if (count < slot.countMin()) {
                    return ValidationResult.fail("Zu wenig §e" + slot.label() + "§c: §e" + count
                        + "§c gefunden, mindestens §e" + slot.countMin() + "§c erforderlich.");
                }
                if (count > slot.countMax()) {
                    return ValidationResult.fail("Zu viele §e" + slot.label() + "§c: §e" + count
                        + "§c gefunden, maximal §e" + slot.countMax() + "§c erlaubt.");
                }
            }
        }

        return ValidationResult.ok();
    }

    private static int countBlocksInArea(World world, int ox, int oy, int oz, int radius,
                                         Material material, BuildingDefinition def) {
        int minY = Math.max(world.getMinHeight(), oy - radius);
        int maxY = Math.min(world.getMaxHeight() - 1, oy + radius);
        int count = 0;

        for (int x = ox - radius; x <= ox + radius; x++) {
            for (int z = oz - radius; z <= oz + radius; z++) {
                if (!isInsideConfiguredArea(def, ox, oz, x, z, radius)) continue;
                for (int y = minY; y <= maxY; y++) {
                    if (world.getBlockAt(x, y, z).getType() == material) {
                        count++;
                    }
                }
            }
        }

        return count;
    }

    private static boolean isInsideConfiguredArea(BuildingDefinition def, int ox, int oz,
                                                  int x, int z, int radius) {
        if (def.getArea() == null) return true;
        int dx = Math.abs(x - ox);
        int dz = Math.abs(z - oz);
        return switch (def.getArea().shape()) {
            case "circle" -> {
                int maxR = Math.max(1, def.getArea().maxRadius());
                yield (dx * dx + dz * dz) <= (maxR * maxR);
            }
            case "hollow_structure" -> {
                int r = Math.max(1, radius);
                yield dx <= r && dz <= r;
            }
            default -> {
                int halfW = Math.max(1, def.getArea().maxWidth() / 2);
                int halfD = Math.max(1, def.getArea().maxDepth() / 2);
                yield dx <= halfW && dz <= halfD;
            }
        };
    }

    public static int radiusFromDefinition(BuildingDefinition def) {
        if (def.getArea() == null) return 8;
        return switch (def.getArea().shape()) {
            case "circle" -> def.getArea().maxRadius();
            case "rectangle", "hollow_structure" ->
                Math.max(def.getArea().maxWidth(), def.getArea().maxDepth()) / 2;
            default -> 8;
        };
    }

    public static void sendResult(Player player, ValidationResult result, BuildingDefinition def) {
        if (result.valid()) {
            player.sendMessage("§a✓ §e" + def.getName() + " §avalidiert und abgeschlossen!");
        } else {
            player.sendMessage("§c✗ Validierung fehlgeschlagen: " + result.failReason());
            player.sendMessage("§7Behebe das Problem und versuche es erneut.");
        }
    }
}
