package com.example.village.hook;


import com.example.village.model.Village;
import com.example.village.model.VillageBuilding;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.lang.reflect.Method;
import java.util.logging.Logger;

public final class BlueMapHook {

    private boolean available;
    private Logger logger;

    public void setup(Logger logger) {
        this.logger = logger;
        available = Bukkit.getPluginManager().getPlugin("BlueMap") != null;
        if (available) {
            logger.info("BlueMap-Integration aktiviert.");
        } else {
            logger.info("BlueMap nicht gefunden - Marker deaktiviert.");
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public void syncBuildingMarker(Village village, VillageBuilding building, String displayName,
                                   WorldEditHook.SchematicData schematicData) {
        if (!available || village == null || building == null || building.getLocation() == null) return;
        try {
            Object blueMapApi = getBlueMapApi();
            if (blueMapApi == null) return;

            String label = (displayName != null && !displayName.isBlank()) ? displayName : building.getTypeKey();
            Location center = calculateCenter(building, schematicData);
            String markerSetId = "village_buildings_" + village.getId();
            String markerId = "building_" + building.getId();

            for (Object map : (Iterable<?>) invoke(blueMapApi, "getMaps")) {
                Object markerSets = invoke(map, "getMarkerSets");
                Object markerSet = invoke(markerSets, "get", markerSetId);
                if (markerSet == null) {
                    Class<?> markerSetClass = Class.forName("de.bluecolored.bluemap.api.markers.MarkerSet");
                    Object builder = invokeStatic(markerSetClass, "builder");
                    invoke(builder, "label", village.getName() + " Gebäude");
                    markerSet = invoke(builder, "build");
                    invoke(markerSets, "put", markerSetId, markerSet);
                }

                Class<?> poiMarkerClass = Class.forName("de.bluecolored.bluemap.api.markers.POIMarker");
                Object poiBuilder = invokeStatic(poiMarkerClass, "builder");
                invoke(poiBuilder, "label", label);
                invoke(poiBuilder, "position", (double) center.getX(), (double) center.getY(), (double) center.getZ());
                Object marker = invoke(poiBuilder, "build");

                Object markers = invoke(markerSet, "getMarkers");
                invoke(markers, "put", markerId, marker);
            }
        } catch (Throwable ex) {
            if (logger != null) {
                logger.warning("BlueMap Marker-Update fehlgeschlagen: " + ex.getMessage());
            }
        }
    }

    public void removeBuildingMarker(Village village, VillageBuilding building) {
        if (!available || village == null || building == null) return;
        try {
            Object blueMapApi = getBlueMapApi();
            if (blueMapApi == null) return;
            String markerSetId = "village_buildings_" + village.getId();
            String markerId = "building_" + building.getId();

            for (Object map : (Iterable<?>) invoke(blueMapApi, "getMaps")) {
                Object markerSets = invoke(map, "getMarkerSets");
                Object markerSet = invoke(markerSets, "get", markerSetId);
                if (markerSet == null) continue;
                Object markers = invoke(markerSet, "getMarkers");
                invoke(markers, "remove", markerId);
            }
        } catch (Throwable ex) {
            if (logger != null) {
                logger.warning("BlueMap Marker-Entfernung fehlgeschlagen: " + ex.getMessage());
            }
        }
    }

    private Location calculateCenter(VillageBuilding building, WorldEditHook.SchematicData schematicData) {
        Location origin = building.getLocation();
        if (schematicData == null || schematicData.getBlocks().isEmpty()) {
            return origin.clone().add(0.5, 1.0, 0.5);
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (WorldEditHook.SchematicBlock block : schematicData.getBlocks()) {
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

        return new Location(origin.getWorld(),
                (minX + maxX) / 2.0 + 0.5,
                (minY + maxY) / 2.0 + 0.5,
                (minZ + maxZ) / 2.0 + 0.5);
    }

    private Object getBlueMapApi() throws Exception {
        Class<?> apiClass = Class.forName("de.bluecolored.bluemap.api.BlueMapAPI");
        Object optional = invokeStatic(apiClass, "getInstance");
        if (optional == null) return null;
        boolean present = (boolean) invoke(optional, "isPresent");
        return present ? invoke(optional, "get") : null;
    }

    private Object invoke(Object target, String method, Object... args) throws Exception {
        Method m = findMethod(target.getClass(), method, args.length);
        if (m == null) throw new NoSuchMethodException(method);
        return m.invoke(target, args);
    }

    private Object invokeStatic(Class<?> type, String method, Object... args) throws Exception {
        Method m = findMethod(type, method, args.length);
        if (m == null) throw new NoSuchMethodException(method);
        return m.invoke(null, args);
    }

    private Method findMethod(Class<?> type, String name, int argCount) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == argCount) {
                return method;
            }
        }
        return null;
    }
}
