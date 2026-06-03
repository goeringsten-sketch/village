package com.example.village.service;

import com.example.village.model.VillageBorder;

import java.util.List;

/**
 * Validates village borders using the configurable square-check algorithm.
 */
public final class BorderValidationService {

    /**
     * Validates that at every edge block of the border, an NxN square can be placed
     * that is fully inside the bordered area.
     *
     * @param border   The border to validate
     * @param squareSize The side length of the required square (e.g. 3 for 3x3)
     * @return true if the border is valid
     */
    public boolean validateBorder(VillageBorder border, int squareSize) {
        if (border.getBorderPoints().size() < 3) return false;
        if (squareSize <= 0) return true;

        List<int[]> edgeBlocks = border.getEdgeBlocks();
        if (edgeBlocks.isEmpty()) return false;

        int halfSize = squareSize / 2;

        for (int[] edge : edgeBlocks) {
            if (!canPlaceSquareAt(border, edge[0], edge[1], squareSize, halfSize)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Checks if an NxN square can be placed at or near the given border point
     * such that all blocks of the square are inside the border.
     *
     * We try placing the square centered on the edge point and in all nearby
     * positions within the square size range to find at least one valid placement.
     */
    private boolean canPlaceSquareAt(VillageBorder border, int edgeX, int edgeZ,
                                      int squareSize, int halfSize) {
        // Try all possible square placements that include the edge point
        for (int offsetX = -halfSize; offsetX <= halfSize; offsetX++) {
            for (int offsetZ = -halfSize; offsetZ <= halfSize; offsetZ++) {
                int cornerX = edgeX + offsetX;
                int cornerZ = edgeZ + offsetZ;

                if (isSquareInside(border, cornerX, cornerZ, squareSize)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks if a square starting at (cornerX, cornerZ) with the given size
     * is fully inside the border (all blocks contained or on border).
     */
    private boolean isSquareInside(VillageBorder border, int cornerX, int cornerZ, int size) {
        for (int dx = 0; dx < size; dx++) {
            for (int dz = 0; dz < size; dz++) {
                int checkX = cornerX + dx;
                int checkZ = cornerZ + dz;
                if (!border.contains(checkX, checkZ) && !border.isOnBorder(checkX, checkZ)) {
                    return false;
                }
            }
        }
        return true;
    }
}
