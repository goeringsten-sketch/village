package com.example.village.hook;

import com.example.village.model.Village;
import com.example.village.model.VillageBorder;
import com.example.village.model.VillageBuilding;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

public final class WorldGuardHook {

    private boolean available;

    public void setup(Logger logger) {
        available = Bukkit.getPluginManager().getPlugin("WorldGuard") != null;
        if (available) {
            logger.info("WorldGuard-Integration aktiviert.");
        } else {
            logger.info("WorldGuard nicht gefunden - Gebietsschutz deaktiviert.");
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean createVillageRegion(Village village) {
        if (!isAvailable()) return false;
        return syncVillageRegions(village);
    }

    public boolean createBuildingRegion(Village village, String buildingId,
                                        Location min, Location max) {
        if (!isAvailable()) return false;
        if (village == null || buildingId == null || buildingId.isBlank() || min == null || max == null) {
            return false;
        }

        RegionManager manager = getRegionManager(min.getWorld());
        if (manager == null) return false;

        int minX = Math.min(min.getBlockX(), max.getBlockX());
        int minY = Math.min(min.getBlockY(), max.getBlockY());
        int minZ = Math.min(min.getBlockZ(), max.getBlockZ());
        int maxX = Math.max(min.getBlockX(), max.getBlockX());
        int maxY = Math.max(min.getBlockY(), max.getBlockY());
        int maxZ = Math.max(min.getBlockZ(), max.getBlockZ());

        String regionId = toSafeRegionId("village_building_" + village.getId() + "_" + buildingId);
        ProtectedCuboidRegion region = new ProtectedCuboidRegion(
                regionId,
                BlockVector3.at(minX, minY, minZ),
                BlockVector3.at(maxX, maxY, maxZ)
        );
        applyBuildingAccess(village, null, region);
        manager.addRegion(region);
        return saveManager(manager);
    }

    public boolean createBuildingRegion(Village village, VillageBuilding building,
                                        Location origin,
                                        com.example.village.hook.WorldEditHook.SchematicData schematicData) {
        if (!isAvailable()) return false;
        if (village == null || building == null || origin == null || schematicData == null
                || schematicData.getBlocks().isEmpty()) {
            return false;
        }

        RegionManager manager = getRegionManager(origin.getWorld());
        if (manager == null) return false;

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (com.example.village.hook.WorldEditHook.SchematicBlock block : schematicData.getBlocks()) {
            int worldX = origin.getBlockX() + block.dx();
            int worldY = origin.getBlockY() + block.dy();
            int worldZ = origin.getBlockZ() + block.dz();
            minX = Math.min(minX, worldX);
            minY = Math.min(minY, worldY);
            minZ = Math.min(minZ, worldZ);
            maxX = Math.max(maxX, worldX);
            maxY = Math.max(maxY, worldY);
            maxZ = Math.max(maxZ, worldZ);
        }

        String regionId = buildingRegionId(village, building);
        ProtectedCuboidRegion region = new ProtectedCuboidRegion(
                regionId,
                BlockVector3.at(minX, minY, minZ),
                BlockVector3.at(maxX, maxY, maxZ)
        );
        applyBuildingAccess(village, building, region);
        manager.addRegion(region);
        removeLegacyBuildingRegion(manager, village, building.getId().toString());
        return saveManager(manager);
    }

    public boolean updateBuildingRegionAccess(Village village, VillageBuilding building) {
        if (!isAvailable()) return false;
        if (village == null || building == null) return false;
        RegionManager manager = getRegionManager(village.getWorld());
        if (manager == null) return false;
        ProtectedRegion region = manager.getRegion(buildingRegionId(village, building));
        if (region == null) return false;
        applyBuildingAccess(village, building, region);
        return saveManager(manager);
    }

    public boolean removeBuildingRegion(Village village, VillageBuilding building) {
        if (!isAvailable()) return false;
        if (village == null || building == null) return false;
        RegionManager manager = getRegionManager(village.getWorld());
        if (manager == null) return false;
        manager.removeRegion(buildingRegionId(village, building));
        removeLegacyBuildingRegion(manager, village, building.getId().toString());
        return saveManager(manager);
    }

    /** @deprecated legacy UUID-based id; prefer {@link #removeBuildingRegion(Village, VillageBuilding)} */
    @Deprecated
    public boolean removeBuildingRegion(Village village, String buildingUuid) {
        if (!isAvailable()) return false;
        if (village == null || buildingUuid == null || buildingUuid.isBlank()) return false;
        RegionManager manager = getRegionManager(village.getWorld());
        if (manager == null) return false;
        manager.removeRegion(toSafeRegionId("village_building_" + village.getId() + "_" + buildingUuid));
        return saveManager(manager);
    }

    public boolean removeVillageRegion(Village village) {
        if (!isAvailable()) return false;
        if (village == null) return false;
        village.ensureBorderIds();
        World world = village.getWorld();
        RegionManager manager = getRegionManager(world);
        if (manager == null) return false;

        for (VillageBorder border : village.getBorders()) {
            manager.removeRegion(villageBorderRegionId(village, border.getId()));
        }
        // Gebäude-Regionen (Prefix wie Dorfname, aber nicht nur numerische Border-ID)
        String prefix = villageRegionPrefix(village);
        for (String existingId : new HashSet<>(manager.getRegions().keySet())) {
            if (existingId.startsWith(prefix) && !isVillageBorderRegionId(existingId, prefix)) {
                manager.removeRegion(existingId);
            }
        }
        String legacyPrefix = toSafeRegionId("village_building_" + village.getId() + "_");
        for (String existingId : new HashSet<>(manager.getRegions().keySet())) {
            if (existingId.startsWith(legacyPrefix)) {
                manager.removeRegion(existingId);
            }
        }
        return saveManager(manager);
    }

    public boolean updateVillageRegion(Village village) {
        if (!isAvailable()) return false;
        return syncVillageRegions(village);
    }

    public boolean isProtected(Location location) {
        if (!isAvailable()) return false;
        if (location == null || location.getWorld() == null) return false;
        RegionManager manager = getRegionManager(location.getWorld());
        if (manager == null) return false;
        return manager.getApplicableRegions(BlockVector3.at(
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
        )).size() > 0;
    }

    public boolean syncVillageRegions(Village village) {
        if (!isAvailable()) return false;
        if (village == null) return false;
        village.ensureBorderIds();

        World world = village.getWorld();
        RegionManager manager = getRegionManager(world);
        if (manager == null) return false;

        Set<String> expectedIds = new HashSet<>();
        for (VillageBorder border : village.getBorders()) {
            String regionId = villageBorderRegionId(village, border.getId());
            expectedIds.add(regionId);

            ProtectedPolygonalRegion region = new ProtectedPolygonalRegion(
                    regionId,
                    toBlockVector2Points(border.getBorderPoints()),
                    border.getMinY(),
                    border.getMaxY()
            );
            applyOwners(village, border, region);
            manager.addRegion(region);
        }

        String prefix = villageRegionPrefix(village);
        Set<String> staleRegionIds = new HashSet<>();
        for (String existingId : manager.getRegions().keySet()) {
            if (existingId.startsWith(prefix) && !expectedIds.contains(existingId)
                    && isVillageBorderRegionId(existingId, prefix)) {
                staleRegionIds.add(existingId);
            }
        }
        for (String staleId : staleRegionIds) {
            manager.removeRegion(staleId);
        }

        return saveManager(manager);
    }

    public boolean hasAccessToVillageBorder(Village village, int borderId, UUID playerId) {
        if (!isAvailable() || village == null || playerId == null) return false;
        RegionManager manager = getRegionManager(village.getWorld());
        if (manager == null) return false;
        ProtectedRegion region = manager.getRegion(villageBorderRegionId(village, borderId));
        if (region == null) return false;
        return region.getOwners().contains(playerId) || region.getMembers().contains(playerId);
    }

    public double distanceToRegionBoundary(World world, String regionId, double x, double z) {
        if (!isAvailable() || world == null || regionId == null || regionId.isBlank()) {
            return Double.POSITIVE_INFINITY;
        }

        try {
            com.sk89q.worldguard.WorldGuard worldGuard = WorldGuard.getInstance();
            com.sk89q.worldguard.protection.regions.RegionContainer container = worldGuard.getPlatform().getRegionContainer();
            com.sk89q.worldguard.protection.managers.RegionManager manager = container.get(BukkitAdapter.adapt(world));
            if (manager == null) {
                return Double.POSITIVE_INFINITY;
            }

            com.sk89q.worldguard.protection.regions.ProtectedRegion region = manager.getRegion(regionId);
            if (region == null) {
                return Double.POSITIVE_INFINITY;
            }

            if (region instanceof ProtectedPolygonalRegion polygon) {
                return distanceToPolygon(polygon.getPoints(), x, z);
            }

            com.sk89q.worldedit.math.BlockVector3 min = region.getMinimumPoint();
            com.sk89q.worldedit.math.BlockVector3 max = region.getMaximumPoint();
            return distanceToRectangle(min.x(), min.z(), max.x(), max.z(), x, z);
        } catch (Throwable ignored) {
            return Double.POSITIVE_INFINITY;
        }
    }

    private String villageRegionPrefix(Village village) {
        return toSafeVillageName(village.getName()) + "_";
    }

    private String villageBorderRegionId(Village village, int borderId) {
        return toSafeRegionId(villageRegionPrefix(village) + borderId);
    }

    private List<BlockVector2> toBlockVector2Points(List<int[]> points) {
        java.util.ArrayList<BlockVector2> out = new java.util.ArrayList<>();
        if (points == null) return out;
        for (int[] point : points) {
            if (point == null || point.length < 2) continue;
            out.add(BlockVector2.at(point[0], point[1]));
        }
        return out;
    }

    private void applyOwners(Village village, VillageBorder border, ProtectedRegion region) {
        DefaultDomain owners = new DefaultDomain();
        Set<UUID> borderOwners = border.getOwners();
        if (borderOwners != null) {
            for (UUID owner : borderOwners) {
                owners.addPlayer(owner);
            }
        }
        owners.addPlayer(village.getFounderId());
        region.setOwners(owners);
    }

    /**
     * WorldGuard id: {@code <dorfname>_<gebäudetyp>_<#>} (sanitized).
     */
    public String buildingRegionId(Village village, VillageBuilding building) {
        if (village == null || building == null) {
            return "";
        }
        return toSafeRegionId(toSafeVillageName(village.getName())
                + "_" + toSafeTypeKey(building.getTypeKey())
                + "_" + Math.max(1, building.getTypeOrdinal()));
    }

    private String toSafeTypeKey(String typeKey) {
        if (typeKey == null || typeKey.isBlank()) {
            return "building";
        }
        String s = typeKey.toLowerCase().replaceAll("[^a-z0-9_\\-]", "_");
        s = s.replaceAll("_+", "_");
        s = s.replaceAll("^_+|_+$", "");
        return s.isBlank() ? "building" : s;
    }

    /** Border regions are {@code <dorfname>_<id>} with numeric id only — not building regions. */
    private boolean isVillageBorderRegionId(String regionId, String prefix) {
        if (regionId == null || prefix == null || !regionId.startsWith(prefix)) {
            return false;
        }
        String suffix = regionId.substring(prefix.length());
        return suffix.matches("\\d+");
    }

    private void removeLegacyBuildingRegion(RegionManager manager, Village village, String buildingUuid) {
        if (manager == null || village == null || buildingUuid == null) {
            return;
        }
        manager.removeRegion(toSafeRegionId("village_building_" + village.getId() + "_" + buildingUuid));
    }

    private void applyBuildingAccess(Village village, VillageBuilding building, ProtectedRegion region) {
        DefaultDomain owners = new DefaultDomain();
        DefaultDomain members = new DefaultDomain();

        if (village != null && village.getFounderId() != null) {
            owners.addPlayer(village.getFounderId());
        }

        if (building != null && building.getOwnerId() != null) {
            owners.addPlayer(building.getOwnerId());
        }

        if (village != null) {
            if (building == null || building.isAccessAllMembers()) {
                village.getMembers().keySet().forEach(members::addPlayer);
            } else {
                for (UUID allowed : building.getAccessList()) {
                    members.addPlayer(allowed);
                }
            }
        }

        region.setOwners(owners);
        region.setMembers(members);
    }

    private RegionManager getRegionManager(World world) {
        if (!isAvailable() || world == null) return null;
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        return container.get(BukkitAdapter.adapt(world));
    }

    private boolean saveManager(RegionManager manager) {
        try {
            manager.save();
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String toSafeRegionId(String input) {
        return input.toLowerCase().replaceAll("[^a-z0-9_\\-]", "_");
    }

    private String toSafeVillageName(String name) {
        if (name == null || name.isBlank()) {
            return "dorf";
        }
        String sanitized = name.toLowerCase().replaceAll("[^a-z0-9_\\-]", "_");
        sanitized = sanitized.replaceAll("_+", "_");
        sanitized = sanitized.replaceAll("^_+|_+$", "");
        return sanitized.isBlank() ? "dorf" : sanitized;
    }

    private double distanceToPolygon(List<com.sk89q.worldedit.math.BlockVector2> points, double x, double z) {
        if (points == null || points.size() < 3) {
            return Double.POSITIVE_INFINITY;
        }

        boolean inside = false;
        double minDistance = Double.POSITIVE_INFINITY;
        for (int i = 0, j = points.size() - 1; i < points.size(); j = i++) {
            com.sk89q.worldedit.math.BlockVector2 pi = points.get(i);
            com.sk89q.worldedit.math.BlockVector2 pj = points.get(j);

            double xi = pi.x();
            double zi = pi.z();
            double xj = pj.x();
            double zj = pj.z();

            if ((zi > z) != (zj > z) && x < (xj - xi) * (z - zi) / (zj - zi) + xi) {
                inside = !inside;
            }

            minDistance = Math.min(minDistance, distancePointToSegment(x, z, xi, zi, xj, zj));
        }

        return inside ? 0 : minDistance;
    }

    private double distanceToRectangle(double minX, double minZ, double maxX, double maxZ, double x, double z) {
        double nearestX = Math.max(minX, Math.min(x, maxX));
        double nearestZ = Math.max(minZ, Math.min(z, maxZ));
        double dx = x - nearestX;
        double dz = z - nearestZ;
        return (dx == 0 && dz == 0) ? 0 : Math.sqrt(dx * dx + dz * dz);
    }

    private double distancePointToSegment(double px, double pz, double ax, double az, double bx, double bz) {
        double dx = bx - ax;
        double dz = bz - az;
        double lengthSquared = dx * dx + dz * dz;
        if (lengthSquared == 0) {
            double pointDx = px - ax;
            double pointDz = pz - az;
            return Math.sqrt(pointDx * pointDx + pointDz * pointDz);
        }

        double t = ((px - ax) * dx + (pz - az) * dz) / lengthSquared;
        t = Math.max(0, Math.min(1, t));
        double projX = ax + t * dx;
        double projZ = az + t * dz;
        double distX = px - projX;
        double distZ = pz - projZ;
        return Math.sqrt(distX * distX + distZ * distZ);
    }
}
