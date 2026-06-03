package com.example.village.model;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class VillageBorder {

    private int id; // stable per-village id
    private final List<int[]> borderPoints;
    private final Set<UUID> owners = new HashSet<>();
    private int minY;
    private int maxY;
    private int minX;
    private int maxX;
    private int minZ;
    private int maxZ;

    public VillageBorder() {
        this.id = 0;
        this.borderPoints = new ArrayList<>();
        this.minY = 0;
        this.maxY = 256;
    }

    public VillageBorder(List<int[]> points, int minY, int maxY) {
        this.id = 0;
        this.borderPoints = new ArrayList<>(points);
        this.minY = minY;
        this.maxY = maxY;
        recalculateBounds();
    }

    public static VillageBorder createSquare(Location center, int sideLength, int heightAbove, int heightBelow) {
        int halfSide = sideLength / 2;
        int cx = center.getBlockX();
        int cz = center.getBlockZ();

        List<int[]> points = new ArrayList<>();
        points.add(new int[]{cx - halfSide, cz - halfSide});
        points.add(new int[]{cx + halfSide, cz - halfSide});
        points.add(new int[]{cx + halfSide, cz + halfSide});
        points.add(new int[]{cx - halfSide, cz + halfSide});

        int bellY = center.getBlockY();
        return new VillageBorder(points, bellY - heightBelow, bellY + heightAbove);
    }

    public void addPoint(int x, int z) {
        borderPoints.add(new int[]{x, z});
        recalculateBounds();
    }

    public void setPoints(List<int[]> points) {
        borderPoints.clear();
        borderPoints.addAll(points);
        recalculateBounds();
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public Set<UUID> getOwners() { return owners; }
    public void setOwners(Set<UUID> owners) {
        this.owners.clear();
        if (owners != null) this.owners.addAll(owners);
    }
    public boolean isOwner(UUID playerId) { return owners.contains(playerId); }

    private void recalculateBounds() {
        if (borderPoints.isEmpty()) return;
        minX = Integer.MAX_VALUE;
        maxX = Integer.MIN_VALUE;
        minZ = Integer.MAX_VALUE;
        maxZ = Integer.MIN_VALUE;
        for (int[] point : borderPoints) {
            minX = Math.min(minX, point[0]);
            maxX = Math.max(maxX, point[0]);
            minZ = Math.min(minZ, point[1]);
            maxZ = Math.max(maxZ, point[1]);
        }
    }

    public boolean contains(Location location) {
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        if (y < minY || y > maxY) return false;
        if (x < minX || x > maxX || z < minZ || z > maxZ) return false;

        return isInsidePolygon(x, z);
    }

    public boolean contains(int x, int z) {
        if (x < minX || x > maxX || z < minZ || z > maxZ) return false;
        return isInsidePolygon(x, z);
    }

    private boolean isInsidePolygon(int x, int z) {
        if (borderPoints.size() < 3) return false;

        boolean inside = false;
        int n = borderPoints.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            int xi = borderPoints.get(i)[0];
            int zi = borderPoints.get(i)[1];
            int xj = borderPoints.get(j)[0];
            int zj = borderPoints.get(j)[1];

            if ((zi > z) != (zj > z) &&
                    x < (xj - xi) * (z - zi) / (double) (zj - zi) + xi) {
                inside = !inside;
            }
        }
        return inside;
    }

    public boolean isOnBorder(int x, int z) {
        if (borderPoints.size() < 2) return false;
        int n = borderPoints.size();
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            int x1 = borderPoints.get(i)[0];
            int z1 = borderPoints.get(i)[1];
            int x2 = borderPoints.get(j)[0];
            int z2 = borderPoints.get(j)[1];

            if (isOnSegment(x, z, x1, z1, x2, z2)) return true;
        }
        return false;
    }

    public double distanceToBoundary(int x, int z) {
        if (contains(x, z) || isOnBorder(x, z)) {
            return 0.0;
        }
        double best = Double.POSITIVE_INFINITY;
        int n = borderPoints.size();
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            int x1 = borderPoints.get(i)[0];
            int z1 = borderPoints.get(i)[1];
            int x2 = borderPoints.get(j)[0];
            int z2 = borderPoints.get(j)[1];
            best = Math.min(best, distancePointToSegment(x, z, x1, z1, x2, z2));
        }
        return best;
    }

    private double distancePointToSegment(int px, int pz, int x1, int z1, int x2, int z2) {
        double dx = x2 - x1;
        double dz = z2 - z1;
        double lengthSquared = dx * dx + dz * dz;
        if (lengthSquared == 0) {
            double deltaX = px - x1;
            double deltaZ = pz - z1;
            return Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        }

        double t = ((px - x1) * dx + (pz - z1) * dz) / lengthSquared;
        t = Math.max(0, Math.min(1, t));
        double projX = x1 + t * dx;
        double projZ = z1 + t * dz;
        double deltaX = px - projX;
        double deltaZ = pz - projZ;
        return Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
    }

    private boolean isOnSegment(int px, int pz, int x1, int z1, int x2, int z2) {
        int cross = (px - x1) * (z2 - z1) - (pz - z1) * (x2 - x1);
        if (cross != 0) return false;
        return Math.min(x1, x2) <= px && px <= Math.max(x1, x2) &&
               Math.min(z1, z2) <= pz && pz <= Math.max(z1, z2);
    }

    public int calculateArea() {
        if (borderPoints.size() < 3) return 0;
        double area = 0;
        int n = borderPoints.size();
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            area += (double) borderPoints.get(i)[0] * borderPoints.get(j)[1];
            area -= (double) borderPoints.get(j)[0] * borderPoints.get(i)[1];
        }
        return (int) Math.abs(area / 2.0);
    }

    public List<int[]> getEdgeBlocks() {
        List<int[]> edges = new ArrayList<>();
        if (borderPoints.size() < 2) return edges;

        int n = borderPoints.size();
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            int x1 = borderPoints.get(i)[0];
            int z1 = borderPoints.get(i)[1];
            int x2 = borderPoints.get(j)[0];
            int z2 = borderPoints.get(j)[1];
            addLineBlocks(edges, x1, z1, x2, z2);
        }
        return fillDiagonalGaps(edges);
    }

    private List<int[]> fillDiagonalGaps(List<int[]> rawEdges) {
        if (rawEdges.isEmpty()) {
            return rawEdges;
        }
        // De-duplicate while keeping order, so diagonal-gap detection is stable.
        List<int[]> orderedUnique = new ArrayList<>();
        Set<Long> baseSeen = new HashSet<>();
        for (int[] p : rawEdges) {
            long k = (((long) p[0]) << 32) | (p[1] & 0xFFFFFFFFL);
            if (baseSeen.add(k)) {
                orderedUnique.add(p);
            }
        }
        if (orderedUnique.size() < 2) return orderedUnique;

        List<int[]> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();

        // Check gaps between consecutive points (including wrap-around).
        int n = orderedUnique.size();
        for (int i = 0; i < n; i++) {
            int[] prev = orderedUnique.get(i);
            int[] cur = orderedUnique.get((i + 1) % n);

            addUniqueEdge(result, seen, prev[0], prev[1]);

            int dx = cur[0] - prev[0];
            int dz = cur[1] - prev[1];
            if (Math.abs(dx) == 1 && Math.abs(dz) == 1) {
                // Diagonal adjacency causes visible holes. Ensure BOTH orthogonal connectors exist.
                // To avoid "thick" borders, only add connectors that are missing.
                long c1 = (((long) prev[0]) << 32) | (cur[1] & 0xFFFFFFFFL);
                long c2 = (((long) cur[0]) << 32) | (prev[1] & 0xFFFFFFFFL);
                if (!seen.contains(c1)) addUniqueEdge(result, seen, prev[0], cur[1]);
                if (!seen.contains(c2)) addUniqueEdge(result, seen, cur[0], prev[1]);
            }
        }

        return result;
    }

    private void addUniqueEdge(List<int[]> result, Set<Long> seen, int x, int z) {
        long key = (((long) x) << 32) | (z & 0xFFFFFFFFL);
        if (seen.add(key)) {
            result.add(new int[]{x, z});
        }
    }

    private void addLineBlocks(List<int[]> result, int x1, int z1, int x2, int z2) {
        int dx = Math.abs(x2 - x1);
        int dz = Math.abs(z2 - z1);
        int sx = x1 < x2 ? 1 : -1;
        int sz = z1 < z2 ? 1 : -1;
        int err = dx - dz;

        while (true) {
            result.add(new int[]{x1, z1});
            if (x1 == x2 && z1 == z2) break;
            int e2 = 2 * err;
            if (e2 > -dz) { err -= dz; x1 += sx; }
            if (e2 < dx) { err += dx; z1 += sz; }
        }
    }

    public List<int[]> getBorderPoints() {
        return Collections.unmodifiableList(borderPoints);
    }

    public int getMinY() { return minY; }
    public void setMinY(int minY) { this.minY = minY; }
    public int getMaxY() { return maxY; }
    public void setMaxY(int maxY) { this.maxY = maxY; }
    public int getMinX() { return minX; }
    public int getMaxX() { return maxX; }
    public int getMinZ() { return minZ; }
    public int getMaxZ() { return maxZ; }
}
