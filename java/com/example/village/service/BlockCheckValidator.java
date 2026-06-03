package com.example.village.service;

import com.example.village.model.BuildingDefinition;
import org.bukkit.Location;
import org.bukkit.Material;
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
 * 3. hollow_structure: ein Hohlraum-Mindestvolumen (für Baracke/Wohnhaus)
 */
public final class BlockCheckValidator {

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

        // Hohle Struktur prüfen (für Baracke/Wohnhaus)
        if ("hollow_structure".equals(def.getArea() != null ? def.getArea().shape() : null)) {
            return validateHollow(def, world, ox, oy, oz, radius);
        }

        // Block-Count-Check
        Map<Material, Integer> counts = countBlocksInRadius(world, ox, oy, oz, radius);

        for (Map.Entry<Material, Integer> req : def.getRequiredBlocks().entrySet()) {
            int found = counts.getOrDefault(req.getKey(), 0);
            if (found < req.getValue()) {
                return ValidationResult.fail(
                    "Zu wenig §e" + req.getKey().name() + "§c: §e" + found
                    + "§c gefunden, §e" + req.getValue() + "§c benötigt.");
            }
        }

        // Prozentualer Block-Check (Oberflächenblöcke)
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

    /** Prüft ob ein Hohlraum (Baracke/Wohnhaus) das Mindestvolumen erreicht. */
    private static ValidationResult validateHollow(BuildingDefinition def, World world,
                                                    int ox, int oy, int oz, int radius) {
        int minVol = def.getArea() != null ? def.getArea().minInteriorVolume() : 8;
        int airCount = 0;

        for (int x = ox - radius; x <= ox + radius; x++)
            for (int y = oy; y <= oy + radius * 2; y++)
                for (int z = oz - radius; z <= oz + radius; z++)
                    if (world.getBlockAt(x, y, z).getType() == Material.AIR
                     || world.getBlockAt(x, y, z).getType() == Material.CAVE_AIR)
                        airCount++;

        if (airCount < minVol)
            return ValidationResult.fail("Innenraum zu klein: §e" + airCount
                + "§c Luftblöcke (min §e" + minVol + "§c).");

        return ValidationResult.ok();
    }

    /** Gibt eine Map Material → Anzahl für alle Blöcke in einem Quader zurück. */
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

    /** Berechnet den Suchradius aus der AreaConfig (Fallback: 8). */
    public static int radiusFromDefinition(BuildingDefinition def) {
        if (def.getArea() == null) return 8;
        return switch (def.getArea().shape()) {
            case "circle" -> def.getArea().maxRadius();
            case "rectangle", "hollow_structure" ->
                Math.max(def.getArea().maxWidth(), def.getArea().maxDepth()) / 2;
            default -> 8;
        };
    }

    /** Sendet dem Spieler eine verständliche Feedback-Nachricht. */
    public static void sendResult(Player player, ValidationResult result, BuildingDefinition def) {
        if (result.valid()) {
            player.sendMessage("§a✓ §e" + def.getName() + " §avalidiert und abgeschlossen!");
        } else {
            player.sendMessage("§c✗ Validierung fehlgeschlagen: " + result.failReason());
            player.sendMessage("§7Behebe das Problem und versuche es erneut.");
        }
    }
}
