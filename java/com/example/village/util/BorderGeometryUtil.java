package com.example.village.util;

import com.example.village.model.VillageBorder;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.union.CascadedPolygonUnion;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class BorderGeometryUtil {

    private static final GeometryFactory GF = new GeometryFactory();

    private BorderGeometryUtil() {
    }

    public static Polygon buildPolygon(VillageBorder border) {
        List<int[]> points = border.getBorderPoints();
        if (points == null || points.size() < 3) {
            return GF.createPolygon();
        }

        List<Coordinate> coords = new ArrayList<>();
        int[] previous = null;
        for (int[] point : points) {
            if (point == null || point.length < 2) continue;
            if (previous != null && previous[0] == point[0] && previous[1] == point[1]) continue;
            coords.add(new Coordinate(point[0], point[1]));
            previous = point;
        }
        if (coords.size() < 3) {
            return GF.createPolygon();
        }
        if (!coords.get(0).equals2D(coords.get(coords.size() - 1))) {
            coords.add(coords.get(0));
        }
        try {
            LinearRing ring = GF.createLinearRing(coords.toArray(new Coordinate[0]));
            return GF.createPolygon(ring, null);
        } catch (Exception ex) {
            Geometry poly = GF.createPolygon(GF.createLinearRing(coords.toArray(new Coordinate[0])), null);
            Geometry cleaned = clean(poly);
            if (cleaned instanceof Polygon) {
                return (Polygon) cleaned;
            }
            return GF.createPolygon();
        }
    }

    public static Geometry unionBorders(Collection<VillageBorder> borders) {
        if (borders == null || borders.isEmpty()) {
            return GF.createPolygon();
        }
        List<Polygon> polygons = new ArrayList<>();
        for (VillageBorder border : borders) {
            Polygon polygon = buildPolygon(border);
            if (polygon == null || polygon.isEmpty()) continue;
            polygons.add(polygon);
        }
        if (polygons.isEmpty()) {
            return GF.createPolygon();
        }
        Geometry union = CascadedPolygonUnion.union(polygons);
        Geometry cleaned = clean(union);
        // If the union produced multiple disconnected polygons but they touch at borders
        // try a zero-width buffer to merge touching components (common JTS trick).
        if (cleaned instanceof GeometryCollection && cleaned.getNumGeometries() > 1) {
            try {
                Geometry buffered = cleaned.buffer(0);
                Geometry finalClean = clean(buffered);
                if (finalClean != null && !finalClean.isEmpty()) return finalClean;
            } catch (Exception ignored) {
            }
        }
        return cleaned;
    }

    public static Geometry difference(Geometry subject, Geometry other) {
        if (subject == null || subject.isEmpty()) {
            return GF.createPolygon();
        }
        if (other == null || other.isEmpty()) {
            return clean(subject);
        }
        try {
            return clean(subject.difference(other));
        } catch (Exception ex) {
            return clean(subject.buffer(0).difference(other.buffer(0)));
        }
    }

    public static Polygon largestPolygon(Geometry geometry) {
        if (geometry == null || geometry.isEmpty()) {
            return null;
        }
        if (geometry instanceof Polygon) {
            return (Polygon) geometry;
        }
        if (geometry instanceof GeometryCollection) {
            Polygon best = null;
            double bestArea = -1;
            for (int i = 0; i < geometry.getNumGeometries(); i++) {
                Geometry part = geometry.getGeometryN(i);
                if (part instanceof Polygon && part.getArea() > bestArea) {
                    bestArea = part.getArea();
                    best = (Polygon) part;
                }
            }
            return best;
        }
        return null;
    }

    public static java.util.List<int[]> extractBoundaryLoop(Polygon polygon) {
        if (polygon == null || polygon.isEmpty()) {
            return null;
        }
        Coordinate[] ext = polygon.getExteriorRing().getCoordinates();
        if (ext == null || ext.length < 4) {
            return null;
        }
        List<int[]> loop = new ArrayList<>();
        for (int i = 0; i < ext.length - 1; i++) {
            int vx = (int) Math.round(ext[i].x);
            int vz = (int) Math.round(ext[i].y);
            if (loop.isEmpty() || loop.get(loop.size() - 1)[0] != vx || loop.get(loop.size() - 1)[1] != vz) {
                loop.add(new int[]{vx, vz});
            }
        }
        return loop;
    }

    public static Geometry clean(Geometry geometry) {
        if (geometry == null || geometry.isEmpty()) {
            return GF.createPolygon();
        }
        if (geometry.isValid()) {
            return geometry;
        }
        Geometry cleaned = geometry.buffer(0);
        return cleaned == null ? GF.createPolygon() : cleaned;
    }
}
