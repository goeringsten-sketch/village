package com.example.village.service;

import com.example.village.VillagePlugin;
import com.example.village.config.VillageConfigManager;
import com.example.village.model.Village;
import com.example.village.model.VillageBorder;
import com.example.village.util.MessageUtil;
import com.example.village.util.BorderGeometryUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;

public final class BorderService {

    private final VillagePlugin plugin;
    private final VillageConfigManager configManager;
    private final VillageManager villageManager;
    private final BorderValidationService validationService;

    private final Map<UUID, BorderWalkSession> walkSessions = new HashMap<>();
    // ConcurrentHashMap: read from async chat thread (ChatInputListener), written from main thread
    private final Map<UUID, CoordSession> coordSessions = new ConcurrentHashMap<>();
    // Tracks borders awaiting chat confirmation (ConcurrentHashMap for async chat thread safety)
    private final Map<UUID, PendingBorderConfirmation> pendingBorderConfirmations = new ConcurrentHashMap<>();
    private final Map<UUID, PendingBorderActionConfirmation> pendingBorderActionConfirmations = new ConcurrentHashMap<>();
    private final Map<UUID, PendingBorderDeletion> pendingBorderDeletions = new ConcurrentHashMap<>();
    private final Map<UUID, PendingBorderSelection> pendingBorderSelections = new ConcurrentHashMap<>();
    private final Map<UUID, BorderUndoState> undoStates = new ConcurrentHashMap<>();

    public BorderService(VillagePlugin plugin, VillageConfigManager configManager,
                         VillageManager villageManager, BorderValidationService validationService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.villageManager = villageManager;
        this.validationService = validationService;
    }

    // --- Coordinate-based selection ---

    public void startCoordSelection(Player player, Village village) {
        pendingBorderConfirmations.remove(player.getUniqueId());
        coordSessions.put(player.getUniqueId(), new CoordSession(village));
    }

    public void cancelWalkSelection(UUID playerId) {
        BorderWalkSession session = walkSessions.remove(playerId);
        if (session == null) return;
        // try to restore fake blocks for the player
        org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(playerId);
        if (p != null && p.isOnline()) {
            session.clearFakeBlocks(p);
            MessageUtil.send(p, configManager.getPrefix(), "&eGrenzziehung abgebrochen.");
        }
    }

    public boolean isInCoordSession(UUID playerId) {
        return coordSessions.containsKey(playerId);
    }

    public CoordSession getCoordSession(UUID playerId) {
        return coordSessions.get(playerId);
    }

    public boolean handleCoordInput(Player player, String input) {
        CoordSession session = coordSessions.get(player.getUniqueId());
        if (session == null) return false;

        String[] parts = input.trim().split("\\s+");
        if (parts.length != 2) return false;

        try {
            int x = Integer.parseInt(parts[0]);
            int z = Integer.parseInt(parts[1]);

            if (session.getPoint1() == null) {
                session.setPoint1(new int[]{x, z});
                return true;
            } else {
                session.setPoint2(new int[]{x, z});
                return true;
            }
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public VillageBorder finishCoordSelection(Player player) {
        // Session erst holen (ohne remove), damit wir lastError setzen können
        CoordSession session = coordSessions.get(player.getUniqueId());
        if (session == null || session.getPoint1() == null || session.getPoint2() == null) {
            coordSessions.remove(player.getUniqueId());
            return null;
        }

        int[] p1 = session.getPoint1();
        int[] p2 = session.getPoint2();

        int minX = Math.min(p1[0], p2[0]);
        int maxX = Math.max(p1[0], p2[0]);
        int minZ = Math.min(p1[1], p2[1]);
        int maxZ = Math.max(p1[1], p2[1]);

        List<int[]> points = new ArrayList<>();
        points.add(new int[]{minX, minZ});
        points.add(new int[]{maxX, minZ});
        points.add(new int[]{maxX, maxZ});
        points.add(new int[]{minX, maxZ});

        Location bell = session.getVillage().getBellLocation();
        int heightAbove = configManager.getHeightAbove();
        int heightBelow = configManager.getHeightBelow();

        VillageBorder border = new VillageBorder(points,
                bell.getBlockY() - heightBelow, bell.getBlockY() + heightAbove);

        Village village = session.getVillage();
        int maxArea = village.getMaxArea(
                configManager.getMaxArea(), configManager.getUpgradeAreaPerLevel());

        Geometry existing = BorderGeometryUtil.unionBorders(village.getBorders());
        Geometry candidate = BorderGeometryUtil.buildPolygon(border);
        if (candidate == null || candidate.isEmpty()) {
            return null;
        }
        if (existing != null && !existing.isEmpty()) {
            Geometry overlap = candidate.intersection(existing);
            if (overlap != null && overlap.getArea() > 0.0) {
                Geometry outside = BorderGeometryUtil.difference(candidate, existing);
                Polygon outsidePoly = BorderGeometryUtil.largestPolygon(outside);
                if (outsidePoly == null || outsidePoly.isEmpty()) {
                    session.setLastError("&cUngültige Grenzauswahl: Der gewählte Bereich liegt vollständig innerhalb des bestehenden Gebiets.");
                    coordSessions.remove(player.getUniqueId());
                    return null;
                }
                java.util.List<int[]> boundary = BorderGeometryUtil.extractBoundaryLoop(outsidePoly);
                if (boundary == null || boundary.size() < 3) {
                    session.setLastError("&cUngültige Grenzauswahl: Die Grenzlinie konnte nicht berechnet werden. Wähle einen größeren Bereich.");
                    coordSessions.remove(player.getUniqueId());
                    return null;
                }
                border = new VillageBorder(boundary, bell.getBlockY() - heightBelow, bell.getBlockY() + heightAbove);
                // After adjusting, ensure we don't create interior holes when combined with existing borders.
                java.util.List<com.example.village.model.VillageBorder> temp = new java.util.ArrayList<>();
                temp.addAll(village.getBorders());
                temp.add(border);
                Geometry combined = BorderGeometryUtil.unionBorders(temp);
                Polygon largest = BorderGeometryUtil.largestPolygon(combined);
                if (largest != null && largest.getNumInteriorRing() > 0) {
                    // Would create an interior hole -> reject
                    session.setLastError("&cUngültige Grenzauswahl: Diese Grenze würde das Dorfzentrum oder eine bestehende Fläche vollständig einschließen (Loch). Wähle einen Bereich, der außen beginnt.");
                    coordSessions.remove(player.getUniqueId());
                    return null;
                }
            }
        } else {
            // Check for holes even when no overlap with existing: ensure candidate itself doesn't introduce holes when added
            java.util.List<com.example.village.model.VillageBorder> temp = new java.util.ArrayList<>();
            temp.addAll(village.getBorders());
            temp.add(border);
            Geometry combined = BorderGeometryUtil.unionBorders(temp);
            Polygon largest = BorderGeometryUtil.largestPolygon(combined);
            if (largest != null && largest.getNumInteriorRing() > 0) {
                session.setLastError("&cUngültige Grenzauswahl: Diese Grenze würde das Dorfzentrum vollständig einschließen. Wähle einen Bereich, der das Zentrum nicht umringt.");
                coordSessions.remove(player.getUniqueId());
                return null;
            }
        }

        // Check total area (existing + new)
        int totalArea = village.getTotalArea() + border.calculateArea();
        if (totalArea > maxArea) {
            session.setLastError("&cUngültige Grenzauswahl: Die Gesamtfläche überschreitet das erlaubte Maximum von &e" + maxArea + " &cBlöcken.");
            coordSessions.remove(player.getUniqueId());
            return null;
        }

        // Validate that the new border connects to existing territory
        if (!isConnectedToExistingTerritory(village, border)) {
            session.setLastError("&cUngültige Grenzauswahl: Die neue Grenze ist nicht mit dem bestehenden Dorfgebiet verbunden.");
            coordSessions.remove(player.getUniqueId());
            return null;
        }

        coordSessions.remove(player.getUniqueId());
        return border;
    }

    public void cancelCoordSelection(UUID playerId) {
        coordSessions.remove(playerId);
    }

    // --- Walk-based selection ---

    public void startWalkSelection(Player player, Village village) {
        pendingBorderConfirmations.remove(player.getUniqueId());
        walkSessions.put(player.getUniqueId(), new BorderWalkSession(village, player.getLocation()));
    }

    public void startBorderEnlargeWalk(Player player, Village village, int borderId) {
        pendingBorderConfirmations.remove(player.getUniqueId());
        walkSessions.put(player.getUniqueId(), new BorderWalkSession(village, player.getLocation(), BorderWalkAction.ENLARGE, borderId));
    }

    public void startBorderSplitWalk(Player player, Village village, int borderId) {
        pendingBorderConfirmations.remove(player.getUniqueId());
        walkSessions.put(player.getUniqueId(), new BorderWalkSession(village, player.getLocation(), BorderWalkAction.SPLIT, borderId));
    }

    public void startBorderShrinkWalk(Player player, Village village, int borderId) {
        pendingBorderConfirmations.remove(player.getUniqueId());
        walkSessions.put(player.getUniqueId(), new BorderWalkSession(village, player.getLocation(), BorderWalkAction.SHRINK, borderId));
    }

    public void startWalkSelectionWithButtons(Player player, Village village) {
        startWalkSelection(player, village);
        // clickable: cancel or restart
        MessageUtil.sendTwoRunCommands(player, configManager.getPrefix(), "&7Grenzziehung gestartet:",
                "[ABBRECHEN]", "/village bordercancel",
                "[VON VORN BEGINNEN]", "/village borderrestart");
    }

    public void startCoordSelectionWithButtons(Player player, Village village) {
        startCoordSelection(player, village);
        MessageUtil.sendTwoRunCommands(player, configManager.getPrefix(), "&7Koordinaten-Auswahl gestartet:",
                "[ABBRECHEN]", "/village bordercancel",
                "[VON VORN BEGINNEN]", "/village borderrestart");
    }

    public boolean isInWalkSession(UUID playerId) {
        return walkSessions.containsKey(playerId);
    }

    public BorderWalkSession getWalkSession(UUID playerId) {
        return walkSessions.get(playerId);
    }

    public void endWalkSession(UUID playerId) {
        walkSessions.remove(playerId);
    }

    /**
     * Walk step result codes.
     * INSIDE_BORDER: player is still inside existing border, no point recorded.
     * RECORDED: a new point was recorded.
     * FINISH: selection should finish now (player touched existing territory/border).
     * PAUSED_DUPLICATE: player stepped on an already visited block; recording paused until back at last point.
     * RESUMED: player returned to last point; recording resumed.
     * ALREADY_VISITED: no-op (e.g. same block move).
     */
    public enum WalkStepResult {
        INSIDE_BORDER, RECORDED, FINISH, PAUSED_DUPLICATE, RESUMED, ALREADY_VISITED
    }

    public enum BorderWalkAction {
        NORMAL,
        ENLARGE,
        SPLIT,
        SHRINK
    }

    public WalkStepResult handleWalkStep(Player player, Location newLocation) {
        BorderWalkSession session = walkSessions.get(player.getUniqueId());
        if (session == null) return WalkStepResult.ALREADY_VISITED;

        int x = newLocation.getBlockX();
        int z = newLocation.getBlockZ();

        Village village = session.getVillage();
        boolean debug = configManager.isWalkBorderDebug();

        // Walk selection only starts recording after the player leaves the existing territory.
        // While inside any border (and on any border edge), allow movement silently.
        if (session.getPoints().isEmpty()) {
            VillageBorder target = session.getTargetBorderId() == null ? null : village.getBorderById(session.getTargetBorderId());
            if (target != null) {
                if (target.contains(x, z) || target.isOnBorder(x, z)) {
                    return WalkStepResult.INSIDE_BORDER;
                }
            } else if (village.containsLocation(x, z)) {
                // Still inside or on the border - allow movement but don't record
                return WalkStepResult.INSIDE_BORDER;
            }

            // Player is outside the target/previous border - start recording
            session.addPoint(x, z);
            session.setStartAttach(findAdjacentBorderBlock(village, x, z));
            if (debug) {
                int[] sa = session.getStartAttach();
                plugin.getLogger().info("[WalkBorder] start-record player=" + player.getName()
                        + " first=(" + x + "," + z + ")"
                        + " startAttach=" + (sa == null ? "null" : ("(" + sa[0] + "," + sa[1] + ")")));
            }
            return WalkStepResult.RECORDED;
        }

        // If recording is paused due to a duplicate step, resume only when the player is back at the last point.
        if (session.isPausedDuplicate()) {
            int[] last = session.getLastPoint();
            if (last != null && last[0] == x && last[1] == z) {
                session.setPausedDuplicate(false);
                if (debug) {
                    plugin.getLogger().info("[WalkBorder] resume-at-last player=" + player.getName()
                            + " at=(" + x + "," + z + ")");
                }
                return WalkStepResult.RESUMED;
            }
            return WalkStepResult.ALREADY_VISITED;
        }

        // If the player is performing an interior cut (split/shrink), only finish when a completed path ends at the selected border.
        if (session.getAction() == BorderWalkAction.SPLIT || session.getAction() == BorderWalkAction.SHRINK) {
            VillageBorder target = session.getTargetBorderId() == null ? null : village.getBorderById(session.getTargetBorderId());
            if (target != null) {
                if (target.contains(x, z) && !target.isOnBorder(x, z)) {
                    session.setEnteredTarget(true);
                }
                if (target.isOnBorder(x, z) && session.hasEnteredTarget()) {
                    session.setEndAttach(new int[]{x, z});
                    if (debug) {
                        int[] ea = session.getEndAttach();
                        plugin.getLogger().info("[WalkBorder] finished-split/shrink player=" + player.getName()
                                + " at=(" + x + "," + z + ")"
                                + " endAttach=" + (ea == null ? "null" : ("(" + ea[0] + "," + ea[1] + ")")));
                    }
                    return WalkStepResult.FINISH;
                }
            }
        }

        // If the player touches existing territory/border again, finish automatically.
        // For SPLIT and SHRINK, we bypass this because they are walking inside the target border.
        if (session.getAction() != BorderWalkAction.SPLIT && session.getAction() != BorderWalkAction.SHRINK) {
            if (village.containsLocation(x, z)) {
                session.setEndAttach(village.isOnAnyBorder(x, z) ? new int[]{x, z} : findAdjacentBorderBlock(village, x, z));
                if (debug) {
                    int[] ea = session.getEndAttach();
                    plugin.getLogger().info("[WalkBorder] touched-existing player=" + player.getName()
                            + " at=(" + x + "," + z + ")"
                            + " onBorder=" + village.isOnAnyBorder(x, z)
                            + " endAttach=" + (ea == null ? "null" : ("(" + ea[0] + "," + ea[1] + ")")));
                }
                return WalkStepResult.FINISH;
            }
        }

        // Prevent drawing over the same block twice.
        if (session.hasVisited(x, z)) {
            session.setPausedDuplicate(true);
            if (debug) {
                int[] last = session.getLastPoint();
                plugin.getLogger().info("[WalkBorder] duplicate-step player=" + player.getName()
                        + " at=(" + x + "," + z + ")"
                        + " last=" + (last == null ? "null" : ("(" + last[0] + "," + last[1] + ")")));
            }
            return WalkStepResult.PAUSED_DUPLICATE;
        }

        session.addPoint(x, z);
        return WalkStepResult.RECORDED;
    }

    /**
     * Checks if the player is adjacent to the existing village border (for right-click finish).
     */
    public boolean isAdjacentToExistingBorder(Player player) {
        BorderWalkSession session = walkSessions.get(player.getUniqueId());
        if (session == null || session.getPoints().size() < 4) return false;

        int[] lastPoint = session.getLastPoint();
        Village village = session.getVillage();

        // Check if any of the 4 cardinal neighbors of the last walked point is on any existing border
        return village.isOnAnyBorder(lastPoint[0] + 1, lastPoint[1])
                || village.isOnAnyBorder(lastPoint[0] - 1, lastPoint[1])
                || village.isOnAnyBorder(lastPoint[0], lastPoint[1] + 1)
                || village.isOnAnyBorder(lastPoint[0], lastPoint[1] - 1);
    }

    public VillageBorder finishWalkSelection(Player player) {
        BorderWalkSession session = walkSessions.remove(player.getUniqueId());
        if (session == null || session.getPoints().size() < 4) {
            return null;
        }

        List<int[]> rawPoints = new ArrayList<>(session.getPoints());
        Location bell = session.getVillage().getBellLocation();
        int heightAbove = configManager.getHeightAbove();
        int heightBelow = configManager.getHeightBelow();

        // Close the walk path by attaching to the existing border without overlaps.
        Village village = session.getVillage();
        boolean debug = configManager.isWalkBorderDebug();
        int[] firstPoint = rawPoints.get(0);
        int[] lastPoint = rawPoints.get(rawPoints.size() - 1);
        int[] startAttach = session.getStartAttach();
        int[] endAttach = session.getEndAttach();
        if (startAttach == null || endAttach == null) {
            // Fallback: try to infer from first/last point adjacency
            startAttach = findAdjacentBorderBlock(village, firstPoint[0], firstPoint[1]);
            endAttach = findAdjacentBorderBlock(village, lastPoint[0], lastPoint[1]);
        }
        if (startAttach == null || endAttach == null) {
            plugin.getLogger().warning("[Village] Could not close walk border: no attachment to existing border found.");
            return null;
        }

        // The user expects NO overlap: start/end stay 1 block outside the old border.
        // We close along the "offset ring": blocks that are outside the existing territory but adjacent to any existing border block.
        List<int[]> points = new ArrayList<>(rawPoints);

        VillageBorder attachedBorder = findAttachedBorder(village, endAttach);
        Set<Long> offsetRing = buildOffsetRingSet(village);

        if (!offsetRing.contains(packCoords(firstPoint[0], firstPoint[1]))
                || !offsetRing.contains(packCoords(lastPoint[0], lastPoint[1]))) {
            if (debug) {
                plugin.getLogger().info("[WalkBorder] close-fail reason=endpoints-not-on-offset-ring"
                        + " first=(" + firstPoint[0] + "," + firstPoint[1] + ")"
                        + " last=(" + lastPoint[0] + "," + lastPoint[1] + ")");
            }
            // Fallback: if endpoints are not on the offset ring, we cannot guarantee no overlap.
            return null;
        }

        ClosingPathChoice choice = chooseClosingPathOnOffsetRing(offsetRing, lastPoint, firstPoint, attachedBorder, points, 1000);
        if (choice == null || choice.path == null) {
            plugin.getLogger().warning("[Village] Could not close walk border: offset-ring path not found within 1000 steps.");
            if (debug) {
                plugin.getLogger().info("[WalkBorder] close-fail reason=offset-path-null"
                        + " start=(" + firstPoint[0] + "," + firstPoint[1] + ")"
                        + " end=(" + lastPoint[0] + "," + lastPoint[1] + ")");
            }
            return null;
        }
        if (debug) {
            plugin.getLogger().info("[WalkBorder] close-offset chosen direction=" + choice.direction
                    + " length=" + choice.path.size()
                    + " enclosesOld=" + choice.enclosesOldBorder);
        }

        // Append offset-ring path end->start, skipping first element (lastPoint already in points).
        for (int i = 1; i < choice.path.size(); i++) {
            points.add(choice.path.get(i));
        }

        VillageBorder border = new VillageBorder(points,
                bell.getBlockY() - heightBelow, bell.getBlockY() + heightAbove);

        // --- Thin-line validation: remove inner-facing border blocks ---
        List<int[]> thinPoints = ensureThinBorderLine(border);
        if (thinPoints.size() < 3) {
            return null;
        }
        border.setPoints(thinPoints);

        Geometry existing = BorderGeometryUtil.unionBorders(village.getBorders());
        Geometry candidate = BorderGeometryUtil.buildPolygon(border);
        if (candidate == null || candidate.isEmpty()) {
            return null;
        }
        if (existing != null && !existing.isEmpty()) {
            Geometry overlap = candidate.intersection(existing);
            if (overlap != null && overlap.getArea() > 0.0) {
                return null;
            }
        }

        int maxArea = village.getMaxArea(
                configManager.getMaxArea(), configManager.getUpgradeAreaPerLevel());

        // Check total area (existing + new)
        int totalArea = village.getTotalArea() + border.calculateArea();
        if (totalArea > maxArea) {
            return null;
        }

        // Walk-based selection inherently connects via adjacency check,
        // but validate anyway for safety
        if (!isConnectedToExistingTerritory(village, border)) {
            return null;
        }

        return border;
    }

    public java.util.List<VillageBorder> finishSpecialWalkSelection(BorderWalkSession session) {
        if (session == null || session.getPoints().size() < 4) {
            return null;
        }
        BorderWalkAction action = session.getAction();
        VillageBorder target = session.getTargetBorderId() == null ? null : session.getVillage().getBorderById(session.getTargetBorderId());
        if (target == null) return null;

        if (action == BorderWalkAction.ENLARGE) {
            VillageBorder newBorder = buildWalkBorder(session);
            if (newBorder == null) return null;
            VillageBorder fused = fuseBorders(session.getVillage(), target, newBorder);
            if (fused == null) return null;
            return java.util.List.of(fused);
        }

        if (action == BorderWalkAction.SPLIT) {
            return splitBorder(session.getVillage(), target, session.getPoints());
        }

        if (action == BorderWalkAction.SHRINK) {
            return shrinkBorder(session.getVillage(), target, session.getPoints());
        }

        return null;
    }

    private VillageBorder buildWalkBorder(BorderWalkSession session) {
        List<int[]> rawPoints = new ArrayList<>(session.getPoints());
        Location bell = session.getVillage().getBellLocation();
        int heightAbove = configManager.getHeightAbove();
        int heightBelow = configManager.getHeightBelow();

        Village village = session.getVillage();
        boolean debug = configManager.isWalkBorderDebug();
        int[] firstPoint = rawPoints.get(0);
        int[] lastPoint = rawPoints.get(rawPoints.size() - 1);
        int[] startAttach = session.getStartAttach();
        int[] endAttach = session.getEndAttach();
        if (startAttach == null || endAttach == null) {
            startAttach = findAdjacentBorderBlock(village, firstPoint[0], firstPoint[1]);
            endAttach = findAdjacentBorderBlock(village, lastPoint[0], lastPoint[1]);
        }
        if (startAttach == null || endAttach == null) {
            plugin.getLogger().warning("[Village] Could not close walk border: no attachment to existing border found.");
            return null;
        }

        List<int[]> points = new ArrayList<>(rawPoints);
        VillageBorder attachedBorder = findAttachedBorder(village, endAttach);
        Set<Long> offsetRing = buildOffsetRingSet(village);

        if (!offsetRing.contains(packCoords(firstPoint[0], firstPoint[1]))
                || !offsetRing.contains(packCoords(lastPoint[0], lastPoint[1]))) {
            if (debug) {
                plugin.getLogger().info("[WalkBorder] close-fail reason=endpoints-not-on-offset-ring"
                        + " first=(" + firstPoint[0] + "," + firstPoint[1] + ")"
                        + " last=(" + lastPoint[0] + "," + lastPoint[1] + ")");
            }
            return null;
        }

        ClosingPathChoice choice = chooseClosingPathOnOffsetRing(offsetRing, lastPoint, firstPoint, attachedBorder, points, 1000);
        if (choice == null || choice.path == null) {
            plugin.getLogger().warning("[Village] Could not close walk border: offset-ring path not found within 1000 steps.");
            if (debug) {
                plugin.getLogger().info("[WalkBorder] close-fail reason=offset-path-null"
                        + " start=(" + firstPoint[0] + "," + firstPoint[1] + ")"
                        + " end=(" + lastPoint[0] + "," + lastPoint[1] + ")");
            }
            return null;
        }
        if (debug) {
            plugin.getLogger().info("[WalkBorder] close-offset chosen direction=" + choice.direction
                    + " length=" + choice.path.size()
                    + " enclosesOld=" + choice.enclosesOldBorder);
        }

        for (int i = 1; i < choice.path.size(); i++) {
            points.add(choice.path.get(i));
        }

        VillageBorder border = new VillageBorder(points,
                bell.getBlockY() - heightBelow, bell.getBlockY() + heightAbove);

        List<int[]> thinPoints = ensureThinBorderLine(border);
        if (thinPoints.size() < 3) {
            return null;
        }
        border.setPoints(thinPoints);

        Geometry existing = BorderGeometryUtil.unionBorders(village.getBorders());
        Geometry candidate = BorderGeometryUtil.buildPolygon(border);
        if (candidate == null || candidate.isEmpty()) {
            return null;
        }
        if (existing != null && !existing.isEmpty()) {
            Geometry overlap = candidate.intersection(existing);
            if (overlap != null && overlap.getArea() > 0.0) {
                return null;
            }
        }

        int maxArea = village.getMaxArea(
                configManager.getMaxArea(), configManager.getUpgradeAreaPerLevel());

        int totalArea = village.getTotalArea() + border.calculateArea();
        if (totalArea > maxArea) {
            return null;
        }

        if (!isConnectedToExistingTerritory(village, border)) {
            return null;
        }

        return border;
    }

    private VillageBorder fuseBorders(Village village, VillageBorder target, VillageBorder newBorder) {
        Geometry fused = BorderGeometryUtil.unionBorders(java.util.List.of(target, newBorder));
        if (fused == null || fused.isEmpty()) {
            return null;
        }
        Polygon polygon = BorderGeometryUtil.largestPolygon(fused);
        if (polygon == null) {
            return null;
        }
        List<int[]> boundary = BorderGeometryUtil.extractBoundaryLoop(polygon);
        if (boundary == null || boundary.isEmpty()) {
            return null;
        }
        VillageBorder fusedBorder = new VillageBorder(boundary,
                target.getMinY(), target.getMaxY());
        fusedBorder.setId(target.getId());
        fusedBorder.setOwners(new java.util.HashSet<>(target.getOwners()));
        return fusedBorder;
    }

    private java.util.List<VillageBorder> splitBorder(Village village, VillageBorder target, List<int[]> walkPoints) {
        LineString cutter = buildLineFromPoints(walkPoints);
        if (cutter == null) return null;
        Geometry cutArea = cutter.buffer(0.5, 1);
        if (cutArea == null || cutArea.isEmpty()) return null;

        Geometry remaining = BorderGeometryUtil.difference(BorderGeometryUtil.buildPolygon(target), cutArea);
        if (remaining == null || remaining.isEmpty()) return null;

        java.util.List<VillageBorder> result = new java.util.ArrayList<>();
        for (int i = 0; i < remaining.getNumGeometries(); i++) {
            Geometry piece = remaining.getGeometryN(i);
            if (!(piece instanceof Polygon) || piece.isEmpty()) continue;
            Polygon polygon = (Polygon) piece;
            List<int[]> boundary = BorderGeometryUtil.extractBoundaryLoop(polygon);
            if (boundary == null || boundary.isEmpty()) continue;
            VillageBorder pieceBorder = new VillageBorder(boundary, target.getMinY(), target.getMaxY());
            pieceBorder.setOwners(new java.util.HashSet<>(target.getOwners()));
            result.add(pieceBorder);
        }

        return result.size() >= 2 ? result : null;
    }

    private java.util.List<VillageBorder> shrinkBorder(Village village, VillageBorder target, List<int[]> walkPoints) {
        LineString cutter = buildLineFromPoints(walkPoints);
        if (cutter == null) return null;
        Geometry cutArea = cutter.buffer(0.5, 1);
        if (cutArea == null || cutArea.isEmpty()) return null;

        Geometry remaining = BorderGeometryUtil.difference(BorderGeometryUtil.buildPolygon(target), cutArea);
        if (remaining == null || remaining.isEmpty()) return null;

        java.util.List<Polygon> pieces = new java.util.ArrayList<>();
        for (int i = 0; i < remaining.getNumGeometries(); i++) {
            Geometry piece = remaining.getGeometryN(i);
            if (!(piece instanceof Polygon) || piece.isEmpty()) continue;
            pieces.add((Polygon) piece);
        }
        if (pieces.isEmpty()) return null;

        Polygon keep = pieces.get(0);
        double maxArea = keep.getArea();
        for (int i = 1; i < pieces.size(); i++) {
            if (pieces.get(i).getArea() > maxArea) {
                keep = pieces.get(i);
                maxArea = keep.getArea();
            }
        }

        List<int[]> boundary = BorderGeometryUtil.extractBoundaryLoop(keep);
        if (boundary == null || boundary.isEmpty()) return null;
        VillageBorder remainingBorder = new VillageBorder(boundary, target.getMinY(), target.getMaxY());
        remainingBorder.setOwners(new java.util.HashSet<>(target.getOwners()));
        return java.util.List.of(remainingBorder);
    }

    private LineString buildLineFromPoints(List<int[]> points) {
        if (points == null || points.size() < 2) return null;
        
        Coordinate[] coords = new Coordinate[points.size() + 2];
        
        // Calculate start extension
        int[] p0 = points.get(0);
        int[] p1 = points.get(1);
        double dxStart = p0[0] - p1[0];
        double dzStart = p0[1] - p1[1];
        double lenStart = Math.max(0.001, Math.sqrt(dxStart * dxStart + dzStart * dzStart));
        coords[0] = new Coordinate(p0[0] + (dxStart / lenStart) * 2.0, p0[1] + (dzStart / lenStart) * 2.0);
        
        // Fill original coordinates
        for (int i = 0; i < points.size(); i++) {
            int[] pt = points.get(i);
            coords[i + 1] = new Coordinate(pt[0], pt[1]);
        }
        
        // Calculate end extension
        int[] pn = points.get(points.size() - 1);
        int[] pnMinus1 = points.get(points.size() - 2);
        double dxEnd = pn[0] - pnMinus1[0];
        double dzEnd = pn[1] - pnMinus1[1];
        double lenEnd = Math.max(0.001, Math.sqrt(dxEnd * dxEnd + dzEnd * dzEnd));
        coords[coords.length - 1] = new Coordinate(pn[0] + (dxEnd / lenEnd) * 2.0, pn[1] + (dzEnd / lenEnd) * 2.0);
        
        return new org.locationtech.jts.geom.GeometryFactory().createLineString(coords);
    }

    private static final class ClosingPathChoice {
        private final List<int[]> path;
        private final String direction;
        private final boolean enclosesOldBorder;

        private ClosingPathChoice(List<int[]> path, String direction, boolean enclosesOldBorder) {
            this.path = path;
            this.direction = direction;
            this.enclosesOldBorder = enclosesOldBorder;
        }
    }

    private ClosingPathChoice chooseClosingPathOnOffsetRing(Set<Long> offsetRing,
                                                           int[] endOutside,
                                                           int[] startOutside,
                                                           VillageBorder oldBorderToAvoidEnclosing,
                                                           List<int[]> currentPoints,
                                                           int maxSteps) {
        // Use BFS instead of deterministic walking. The offset ring can have corners/branches,
        // and "follow next neighbor" will often dead-end.
        int[] prefDir = computePreferredDirection(endOutside, startOutside);
        int[][] preferredOrder = buildDirOrder(prefDir, false);
        int[][] oppositeOrder = buildDirOrder(prefDir, true);

        List<int[]> preferred = bfsPathOnRing(offsetRing, endOutside, startOutside, preferredOrder, maxSteps);
        List<int[]> opposite = bfsPathOnRing(offsetRing, endOutside, startOutside, oppositeOrder, maxSteps);

        boolean debug = configManager.isWalkBorderDebug();
        if (debug) {
            plugin.getLogger().info("[WalkBorder] close-offset-candidates preferred=" + (preferred == null ? "null" : preferred.size())
                    + " opposite=" + (opposite == null ? "null" : opposite.size()));
        }

        ClosingPathChoice preferredChoice = preferred == null ? null : evaluateChoice("preferred", preferred, oldBorderToAvoidEnclosing, currentPoints);
        ClosingPathChoice oppositeChoice = opposite == null ? null : evaluateChoice("opposite", opposite, oldBorderToAvoidEnclosing, currentPoints);

        if (preferredChoice == null && oppositeChoice == null) return null;
        if (preferredChoice != null && oppositeChoice == null) return preferredChoice;
        if (preferredChoice == null) return oppositeChoice;

        if (preferredChoice.enclosesOldBorder && !oppositeChoice.enclosesOldBorder) return oppositeChoice;
        if (!preferredChoice.enclosesOldBorder && oppositeChoice.enclosesOldBorder) return preferredChoice;

        return preferredChoice.path.size() <= oppositeChoice.path.size() ? preferredChoice : oppositeChoice;
    }

    private int[] computePreferredDirection(int[] from, int[] to) {
        int dx = Integer.compare(to[0], from[0]);
        int dz = Integer.compare(to[1], from[1]);
        if (Math.abs(to[0] - from[0]) >= Math.abs(to[1] - from[1])) {
            return new int[]{dx, 0};
        }
        return new int[]{0, dz};
    }

    private int[][] buildDirOrder(int[] preferredCardinal, boolean reverse) {
        // 8-neighbor movement on ring to handle corner connections.
        int[][] base = new int[][]{
                {1, 0}, {-1, 0}, {0, 1}, {0, -1},
                {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
        };

        // Simple ordering: preferred cardinal first, then its diagonals, then the rest.
        List<int[]> ordered = new ArrayList<>(8);
        if (preferredCardinal != null) {
            int px = preferredCardinal[0];
            int pz = preferredCardinal[1];
            if (!(px == 0 && pz == 0)) {
                ordered.add(new int[]{px, pz});
                // Favor diagonals that include the preferred axis.
                if (px != 0) {
                    ordered.add(new int[]{px, 1});
                    ordered.add(new int[]{px, -1});
                } else if (pz != 0) {
                    ordered.add(new int[]{1, pz});
                    ordered.add(new int[]{-1, pz});
                }
            }
        }
        for (int[] d : base) {
            boolean exists = false;
            for (int[] o : ordered) {
                if (o[0] == d[0] && o[1] == d[1]) {
                    exists = true;
                    break;
                }
            }
            if (!exists) ordered.add(d);
        }

        if (reverse) {
            // crude "other direction": flip the order except keep it deterministic
            List<int[]> rev = new ArrayList<>(ordered.size());
            for (int i = ordered.size() - 1; i >= 0; i--) rev.add(ordered.get(i));
            ordered = rev;
        }

        int[][] out = new int[ordered.size()][2];
        for (int i = 0; i < ordered.size(); i++) out[i] = ordered.get(i);
        return out;
    }

    private List<int[]> bfsPathOnRing(Set<Long> ringSet,
                                     int[] start,
                                     int[] goal,
                                     int[][] dirOrder,
                                     int maxSteps) {
        long startKey = packCoords(start[0], start[1]);
        long goalKey = packCoords(goal[0], goal[1]);
        if (!ringSet.contains(startKey) || !ringSet.contains(goalKey)) return null;

        ArrayDeque<Long> q = new ArrayDeque<>();
        Map<Long, Long> parent = new HashMap<>();
        q.add(startKey);
        parent.put(startKey, startKey);

        int expansions = 0;
        while (!q.isEmpty()) {
            long cur = q.poll();
            if (cur == goalKey) break;
            if (expansions++ > maxSteps) return null;

            int cx = (int) (cur >> 32);
            int cz = (int) cur;

            for (int[] d : dirOrder) {
                int nx = cx + d[0];
                int nz = cz + d[1];
                long nk = packCoords(nx, nz);
                if (!ringSet.contains(nk)) continue;
                if (parent.containsKey(nk)) continue;
                parent.put(nk, cur);
                q.add(nk);
            }
        }

        if (!parent.containsKey(goalKey)) return null;

        // Reconstruct path.
        List<int[]> path = new ArrayList<>();
        long cur = goalKey;
        int safety = 0;
        while (true) {
            path.add(new int[]{(int) (cur >> 32), (int) cur});
            long p = parent.get(cur);
            if (cur == p) break;
            cur = p;
            if (safety++ > maxSteps + 5) return null;
        }
        // reverse
        List<int[]> out = new ArrayList<>(path.size());
        for (int i = path.size() - 1; i >= 0; i--) out.add(path.get(i));
        return out;
    }

    private ClosingPathChoice evaluateChoice(String direction, List<int[]> path, VillageBorder oldBorder, List<int[]> currentPoints) {
        List<int[]> tmp = new ArrayList<>(currentPoints);
        for (int i = 1; i < path.size(); i++) {
            tmp.add(path.get(i));
        }
        VillageBorder candidate = new VillageBorder(tmp, 0, 256);
        boolean encloses = isOldBorderFullyInside(candidate, oldBorder);
        return new ClosingPathChoice(path, direction, encloses);
    }

    private boolean isOldBorderFullyInside(VillageBorder candidate, VillageBorder oldBorder) {
        for (int[] p : oldBorder.getEdgeBlocks()) {
            if (!candidate.contains(p[0], p[1]) && !candidate.isOnBorder(p[0], p[1])) {
                return false;
            }
        }
        return true;
    }

    private VillageBorder findAttachedBorder(Village village, int[] endAttach) {
        for (VillageBorder b : village.getBorders()) {
            if (b.isOnBorder(endAttach[0], endAttach[1])) return b;
        }
        return village.getBorder();
    }

    public Set<UUID> getConfirmingPlayersForVillage(UUID villageId) {
        Set<UUID> set = new HashSet<>();
        for (Map.Entry<UUID, PendingBorderConfirmation> entry : pendingBorderConfirmations.entrySet()) {
            if (entry.getValue().getVillage().getId().equals(villageId)) {
                set.add(entry.getKey());
            }
        }
        for (Map.Entry<UUID, PendingBorderActionConfirmation> entry : pendingBorderActionConfirmations.entrySet()) {
            if (entry.getValue().getVillage().getId().equals(villageId)) {
                set.add(entry.getKey());
            }
        }
        return set;
    }

    public static class BorderUndoState {
        private final Village village;
        private final List<VillageBorder> oldBorders;

        public BorderUndoState(Village village, List<VillageBorder> oldBorders) {
            this.village = village;
            this.oldBorders = new ArrayList<>();
            for (VillageBorder b : oldBorders) {
                VillageBorder copy = new VillageBorder(new ArrayList<>(b.getBorderPoints()), b.getMinY(), b.getMaxY());
                copy.setId(b.getId());
                copy.setOwners(new HashSet<>(b.getOwners()));
                this.oldBorders.add(copy);
            }
        }

        public Village getVillage() { return village; }
        public List<VillageBorder> getOldBorders() { return oldBorders; }
    }

    public void setUndoState(UUID playerId, Village village) {
        undoStates.put(playerId, new BorderUndoState(village, village.getBorders()));
    }

    public BorderUndoState getAndRemoveUndoState(UUID playerId) {
        return undoStates.remove(playerId);
    }

    private Set<Long> buildBorderSet(VillageBorder border) {
        Set<Long> set = new HashSet<>();
        for (int[] p : border.getEdgeBlocks()) {
            set.add(packCoords(p[0], p[1]));
        }
        return set;
    }

    private static final class NeighborPair {
        private final int[] preferred;
        private final int[] opposite;

        private NeighborPair(int[] preferred, int[] opposite) {
            this.preferred = preferred;
            this.opposite = opposite;
        }
    }

    private NeighborPair findTwoNeighbors(Set<Long> ringSet, int ex, int ez, int[] target) {
        List<int[]> neighbors = new ArrayList<>(4);
        int[][] dirs = new int[][]{{1,0},{-1,0},{0,1},{0,-1}};
        for (int[] d : dirs) {
            int nx = ex + d[0];
            int nz = ez + d[1];
            if (ringSet.contains(packCoords(nx, nz))) neighbors.add(new int[]{nx, nz});
        }
        if (neighbors.isEmpty()) return null;

        int dx = Integer.compare(target[0], ex);
        int dz = Integer.compare(target[1], ez);
        int[] preferredDir;
        if (Math.abs(target[0] - ex) >= Math.abs(target[1] - ez)) {
            preferredDir = new int[]{dx, 0};
        } else {
            preferredDir = new int[]{0, dz};
        }

        int[] preferred = pickNeighborByDirection(neighbors, ex, ez, preferredDir);
        int[] opposite = null;
        for (int[] n : neighbors) {
            if (preferred == null || n[0] != preferred[0] || n[1] != preferred[1]) {
                opposite = n;
                break;
            }
        }
        return new NeighborPair(preferred, opposite);
    }

    private boolean isCardinalAdjacent(int[] a, int[] b) {
        if (a == null || b == null) return false;
        int dx = Math.abs(a[0] - b[0]);
        int dz = Math.abs(a[1] - b[1]);
        return (dx + dz) == 1;
    }

    private int[] findAdjacentBorderBlock(Village village, int x, int z) {
        if (village.isOnAnyBorder(x, z)) return new int[]{x, z};
        if (village.isOnAnyBorder(x + 1, z)) return new int[]{x + 1, z};
        if (village.isOnAnyBorder(x - 1, z)) return new int[]{x - 1, z};
        if (village.isOnAnyBorder(x, z + 1)) return new int[]{x, z + 1};
        if (village.isOnAnyBorder(x, z - 1)) return new int[]{x, z - 1};
        return null;
    }

    private Set<Long> buildOffsetRingSet(Village village) {
        // Outside blocks that are cardinal-neighbor to ANY existing border edge block, but not inside the village.
        Set<Long> ring = new HashSet<>();
        for (VillageBorder b : village.getBorders()) {
            for (int[] edge : b.getEdgeBlocks()) {
                int x = edge[0];
                int z = edge[1];
                // 8-neighbor offsets improves connectivity around corners.
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) continue;
                        addOffsetIfOutside(village, ring, x + dx, z + dz);
                    }
                }
            }
        }
        return ring;
    }

    private void addOffsetIfOutside(Village village, Set<Long> ring, int x, int z) {
        if (!village.containsLocation(x, z)) {
            ring.add(packCoords(x, z));
        }
    }

    private int[] pickNeighborByDirection(List<int[]> neighbors, int ex, int ez, int[] dir) {
        if (dir == null) return neighbors.get(0);
        for (int[] n : neighbors) {
            int ndx = Integer.compare(n[0], ex);
            int ndz = Integer.compare(n[1], ez);
            if (ndx == dir[0] && ndz == dir[1]) {
                return n;
            }
        }
        return neighbors.get(0);
    }

    // walkRingLoop removed: replaced by bfsPathOnRing

    /**
     * Ensures border is always a thin single-block line.
     * Removes any border blocks that face inward when two parallel lines run next to each other.
     * Only the outermost blocks (facing away from the filled area) remain.
     */
    private List<int[]> ensureThinBorderLine(VillageBorder border) {
        List<int[]> points = border.getBorderPoints();
        if (points.size() < 3) return new ArrayList<>(points);

        // Build a set of all border points for fast lookup
        Set<Long> borderSet = new HashSet<>();
        for (int[] p : points) {
            borderSet.add(packCoords(p[0], p[1]));
        }

        // Compute the interior of the polygon formed by the border
        // A point is interior if it's contained by the border AND all 4 cardinal neighbors are also in the border
        // For thin-line: remove border blocks where all 4 cardinal neighbors are also border blocks
        // (these are interior to the border line itself, forming thick lines)
        List<int[]> thinPoints = new ArrayList<>();
        for (int[] p : points) {
            int x = p[0];
            int z = p[1];
            // Check if this block has all 4 cardinal neighbors in the border set
            boolean n = borderSet.contains(packCoords(x, z - 1));
            boolean s = borderSet.contains(packCoords(x, z + 1));
            boolean e = borderSet.contains(packCoords(x + 1, z));
            boolean w = borderSet.contains(packCoords(x - 1, z));

            // If all 4 neighbors are border blocks, this is an interior block of a thick line - remove it
            // Also check the 2x2 diagonal pattern: if a block has both orthogonal neighbors
            // forming a filled 2x2 square, the inner one should be removed
            if (n && s && e && w) {
                // Interior block - skip
                continue;
            }

            // Check for 2x2 filled squares (diagonal parallel lines)
            // If this block and 3 neighbors form a 2x2 square, keep only the outermost two
            boolean ne = borderSet.contains(packCoords(x + 1, z - 1));
            boolean nw = borderSet.contains(packCoords(x - 1, z - 1));
            boolean se = borderSet.contains(packCoords(x + 1, z + 1));
            boolean sw = borderSet.contains(packCoords(x - 1, z + 1));

            // A 2x2 filled square at NE means: this, N, E, NE are all border.
            // We keep this block only if it's on the outer edge (not fully surrounded on that corner side)
            boolean inFilledSquare = false;
            if (n && e && ne) inFilledSquare = true;
            if (n && w && nw) inFilledSquare = true;
            if (s && e && se) inFilledSquare = true;
            if (s && w && sw) inFilledSquare = true;

            if (inFilledSquare) {
                // Count total cardinal neighbors - if this block has 3+ cardinal neighbors,
                // it's more interior and should be removed
                int cardinalCount = (n ? 1 : 0) + (s ? 1 : 0) + (e ? 1 : 0) + (w ? 1 : 0);
                if (cardinalCount >= 3) {
                    continue; // Too connected - interior block
                }
            }

            thinPoints.add(p);
        }

        return thinPoints;
    }

    private static long packCoords(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    /**
     * Checks if a new border region has at least one point adjacent to (or touching)
     * the existing village territory. This ensures a direct connection.
     */
    private boolean isConnectedToExistingTerritory(Village village, VillageBorder newBorder) {
        Geometry existing = BorderGeometryUtil.unionBorders(village.getBorders());
        Geometry candidate = BorderGeometryUtil.buildPolygon(newBorder);

        // Accept cases where the new outer border fully encloses or touches existing territory.
        // This is important for rectangular selections that surround the initial village border
        // and create a new outer region with an inner hole.
        if (existing != null && !existing.isEmpty() && candidate != null && !candidate.isEmpty()) {
            if (candidate.intersects(existing)) {
                return true;
            }
        }

        List<int[]> edgeBlocks = newBorder.getEdgeBlocks();
        for (int[] edge : edgeBlocks) {
            int x = edge[0];
            int z = edge[1];
            // Check if this edge block or any cardinal neighbor is in existing territory
            if (village.containsLocation(x, z)
                    || village.isOnAnyBorder(x, z)
                    || village.containsLocation(x + 1, z)
                    || village.containsLocation(x - 1, z)
                    || village.containsLocation(x, z + 1)
                    || village.containsLocation(x, z - 1)) {
                return true;
            }
        }
        return false;
    }

    public void cancelWalkSelection(Player player) {
        BorderWalkSession session = walkSessions.remove(player.getUniqueId());
        if (session != null) {
            session.clearFakeBlocks(player);
        }
    }

    /**
     * Returns the remaining available blocks for border expansion.
     */
    public int getRemainingArea(Village village) {
        int maxArea = village.getMaxArea(
                configManager.getMaxArea(), configManager.getUpgradeAreaPerLevel());
        return maxArea - village.getTotalArea();
    }

    // --- Pending border confirmation ---

    public void addPendingBorderConfirmation(UUID playerId, Village village, VillageBorder border) {
        pendingBorderConfirmations.put(playerId, new PendingBorderConfirmation(village, border));
    }

    public PendingBorderConfirmation getPendingBorderConfirmation(UUID playerId) {
        return pendingBorderConfirmations.get(playerId);
    }

    public PendingBorderConfirmation removePendingBorderConfirmation(UUID playerId) {
        return pendingBorderConfirmations.remove(playerId);
    }

    public boolean hasPendingBorderConfirmation(UUID playerId) {
        return pendingBorderConfirmations.containsKey(playerId);
    }

    public void addPendingBorderActionConfirmation(UUID playerId, Village village, BorderWalkAction action, Integer targetBorderId, List<VillageBorder> borders) {
        pendingBorderActionConfirmations.put(playerId, new PendingBorderActionConfirmation(village, action, targetBorderId, borders));
    }

    public PendingBorderActionConfirmation getPendingBorderActionConfirmation(UUID playerId) {
        return pendingBorderActionConfirmations.get(playerId);
    }

    public PendingBorderActionConfirmation removePendingBorderActionConfirmation(UUID playerId) {
        return pendingBorderActionConfirmations.remove(playerId);
    }

    public boolean hasPendingBorderActionConfirmation(UUID playerId) {
        return pendingBorderActionConfirmations.containsKey(playerId);
    }

    public void setPendingBorderDeletion(UUID playerId, PendingBorderDeletion pending) {
        if (pending == null) {
            pendingBorderDeletions.remove(playerId);
        } else {
            pendingBorderDeletions.put(playerId, pending);
        }
    }

    public PendingBorderDeletion getPendingBorderDeletion(UUID playerId) {
        return pendingBorderDeletions.get(playerId);
    }

    public PendingBorderDeletion removePendingBorderDeletion(UUID playerId) {
        return pendingBorderDeletions.remove(playerId);
    }

    public void startBorderDeleteSelection(UUID playerId, Village village) {
        pendingBorderSelections.put(playerId, new PendingBorderSelection(village, BorderSelectionMode.DELETE));
    }

    public void startBorderFusionSelection(UUID playerId, Village village) {
        pendingBorderSelections.put(playerId, new PendingBorderSelection(village, BorderSelectionMode.FUSION));
    }

    public PendingBorderSelection getPendingBorderSelection(UUID playerId) {
        return pendingBorderSelections.get(playerId);
    }

    public PendingBorderSelection removePendingBorderSelection(UUID playerId) {
        return pendingBorderSelections.remove(playerId);
    }

    public static final class PendingBorderSelection {
        private final Village village;
        private final BorderSelectionMode mode;
        private final java.util.LinkedHashSet<Integer> selectedBorderIds = new java.util.LinkedHashSet<>();

        public PendingBorderSelection(Village village, BorderSelectionMode mode) {
            this.village = village;
            this.mode = mode;
        }

        public Village getVillage() { return village; }
        public BorderSelectionMode getMode() { return mode; }
        public java.util.LinkedHashSet<Integer> getSelectedBorderIds() { return selectedBorderIds; }
    }

    public enum BorderSelectionMode {
        DELETE,
        FUSION,
        SPLIT,
        ENLARGE,
        SHRINK
    }

    public void startBorderSplitSelection(UUID playerId, Village village) {
        pendingBorderSelections.put(playerId, new PendingBorderSelection(village, BorderSelectionMode.SPLIT));
    }

    public void startBorderEnlargeSelection(UUID playerId, Village village) {
        pendingBorderSelections.put(playerId, new PendingBorderSelection(village, BorderSelectionMode.ENLARGE));
    }

    public void startBorderShrinkSelection(UUID playerId, Village village) {
        pendingBorderSelections.put(playerId, new PendingBorderSelection(village, BorderSelectionMode.SHRINK));
    }

    public static final class PendingBorderDeletion {
        private final Village village;
        private final List<Integer> borderIdsToDelete;
        private final List<UUID> buildingIdsToDelete;

        public PendingBorderDeletion(Village village, List<Integer> borderIdsToDelete, List<UUID> buildingIdsToDelete) {
            this.village = village;
            this.borderIdsToDelete = borderIdsToDelete;
            this.buildingIdsToDelete = buildingIdsToDelete;
        }

        public Village getVillage() { return village; }
        public List<Integer> getBorderIdsToDelete() { return borderIdsToDelete; }
        public List<UUID> getBuildingIdsToDelete() { return buildingIdsToDelete; }
    }

    // --- Inner session classes ---

    public static final class PendingBorderConfirmation {
        private final Village village;
        private final List<VillageBorder> borders;
        private final BorderWalkAction action;
        private final Integer targetBorderId;

        public PendingBorderConfirmation(Village village, VillageBorder border) {
            this(village, BorderWalkAction.NORMAL, null, java.util.List.of(border));
        }

        public PendingBorderConfirmation(Village village, BorderWalkAction action, Integer targetBorderId, List<VillageBorder> borders) {
            this.village = village;
            this.action = action;
            this.targetBorderId = targetBorderId;
            this.borders = borders == null ? java.util.List.of() : new java.util.ArrayList<>(borders);
        }

        public Village getVillage() { return village; }
        public List<VillageBorder> getBorders() { return borders; }
        public BorderWalkAction getAction() { return action; }
        public Integer getTargetBorderId() { return targetBorderId; }
        public VillageBorder getBorder() { return borders.isEmpty() ? null : borders.get(0); }
    }

    public static final class PendingBorderActionConfirmation {
        private final Village village;
        private final BorderWalkAction action;
        private final Integer targetBorderId;
        private final List<VillageBorder> borders;

        public PendingBorderActionConfirmation(Village village, BorderWalkAction action, Integer targetBorderId, List<VillageBorder> borders) {
            this.village = village;
            this.action = action;
            this.targetBorderId = targetBorderId;
            this.borders = borders == null ? java.util.List.of() : new java.util.ArrayList<>(borders);
        }

        public Village getVillage() { return village; }
        public BorderWalkAction getAction() { return action; }
        public Integer getTargetBorderId() { return targetBorderId; }
        public List<VillageBorder> getBorders() { return borders; }
    }

    public static final class CoordSession {
        private final Village village;
        private int[] point1;
        private int[] point2;
        /** Letzte Fehlerursache aus finishCoordSelection – null wenn kein Fehler. */
        private String lastError;

        public CoordSession(Village village) {
            this.village = village;
        }

        public Village getVillage() { return village; }
        public int[] getPoint1() { return point1; }
        public void setPoint1(int[] point1) { this.point1 = point1; }
        public int[] getPoint2() { return point2; }
        public void setPoint2(int[] point2) { this.point2 = point2; }
        public String getLastError() { return lastError; }
        public void setLastError(String lastError) { this.lastError = lastError; }
    }

    public static final class BorderWalkSession {
        private final Village village;
        private final Location startLocation;
        private final BorderWalkAction action;
        private final Integer targetBorderId;
        private final List<int[]> points = new ArrayList<>();
        private final Set<Long> visitedBlocks = new HashSet<>();
        private final List<Location> fakeBlockLocations = new ArrayList<>();
        private boolean closed;
        private boolean pausedDuplicate;
        private boolean enteredTarget;
        private int[] startAttach;
        private int[] endAttach;

        public BorderWalkSession(Village village, Location startLocation) {
            this(village, startLocation, BorderWalkAction.NORMAL, null);
        }

        public BorderWalkSession(Village village, Location startLocation, BorderWalkAction action, Integer targetBorderId) {
            this.village = village;
            this.startLocation = startLocation;
            this.action = action;
            this.targetBorderId = targetBorderId;
        }

        public Village getVillage() { return village; }
        public Location getStartLocation() { return startLocation; }
        public BorderWalkAction getAction() { return action; }
        public Integer getTargetBorderId() { return targetBorderId; }
        public boolean hasEnteredTarget() { return enteredTarget; }
        public void setEnteredTarget(boolean enteredTarget) { this.enteredTarget = enteredTarget; }
        public List<int[]> getPoints() { return points; }
        public boolean isClosed() { return closed; }
        public void setClosed(boolean closed) { this.closed = closed; }
        public boolean isPausedDuplicate() { return pausedDuplicate; }
        public void setPausedDuplicate(boolean pausedDuplicate) { this.pausedDuplicate = pausedDuplicate; }
        public int[] getStartAttach() { return startAttach; }
        public void setStartAttach(int[] startAttach) { this.startAttach = startAttach; }
        public int[] getEndAttach() { return endAttach; }
        public void setEndAttach(int[] endAttach) { this.endAttach = endAttach; }

        public void addPoint(int x, int z) {
            points.add(new int[]{x, z});
            visitedBlocks.add(packCoords(x, z));
        }

        public boolean hasVisited(int x, int z) {
            return visitedBlocks.contains(packCoords(x, z));
        }

        public int[] getLastPoint() {
            return points.isEmpty() ? null : points.get(points.size() - 1);
        }

        public List<Location> getFakeBlockLocations() {
            return fakeBlockLocations;
        }

        public void addFakeBlock(Location loc) {
            fakeBlockLocations.add(loc);
        }

        /**
         * Sends a client-side fake block at the given walk position.
         */
        public void sendFakeBlock(Player player, int x, int z, Material material) {
            World world = player.getWorld();
            int y = world.getHighestBlockYAt(x, z);
            Location loc = new Location(world, x, y, z);
            BlockData data = material.createBlockData();
            player.sendBlockChange(loc, data);
            fakeBlockLocations.add(loc);
        }

        /**
         * Renders only the OUTER boundary of the walked blocks.
         * This keeps the visualization thin (like an outline).
         *
         * If {@code highlightLast} is true, the last point is highlighted with GOLD_ORE.
         */
        public void renderOutline(Player player, Material outlineMaterial, boolean highlightLast) {
            if (player == null || !player.isOnline()) return;

            // Clear previously shown fake blocks (only for this walk session)
            clearFakeBlocks(player);

            if (visitedBlocks.isEmpty()) return;

            World world = player.getWorld();
            BlockData outlineData = outlineMaterial.createBlockData();

            for (long key : visitedBlocks) {
                int x = (int) (key >> 32);
                int z = (int) key;

                // A block is on the outline if at least one cardinal neighbor is NOT part of the walked set
                if (visitedBlocks.contains(packCoords(x + 1, z))
                        && visitedBlocks.contains(packCoords(x - 1, z))
                        && visitedBlocks.contains(packCoords(x, z + 1))
                        && visitedBlocks.contains(packCoords(x, z - 1))) {
                    continue;
                }

                int y = world.getHighestBlockYAt(x, z);
                Location loc = new Location(world, x, y, z);
                player.sendBlockChange(loc, outlineData);
                fakeBlockLocations.add(loc);
            }

            if (highlightLast) {
                int[] last = getLastPoint();
                if (last != null) {
                    int lx = last[0];
                    int lz = last[1];
                    int y = world.getHighestBlockYAt(lx, lz);
                    Location loc = new Location(world, lx, y, lz);
                    player.sendBlockChange(loc, Material.GOLD_ORE.createBlockData());
                    fakeBlockLocations.add(loc);
                }
            }
        }

        /**
         * Restores all fake blocks to their real block data for the player.
         */
        public void clearFakeBlocks(Player player) {
            if (!player.isOnline()) return;
            World world = player.getWorld();
            for (Location loc : fakeBlockLocations) {
                if (loc.getWorld() != null && loc.getWorld().equals(world)) {
                    player.sendBlockChange(loc, world.getBlockAt(loc).getBlockData());
                }
            }
            fakeBlockLocations.clear();
        }

        private long packCoords(int x, int z) {
            return ((long) x << 32) | (z & 0xFFFFFFFFL);
        }
    }
}
