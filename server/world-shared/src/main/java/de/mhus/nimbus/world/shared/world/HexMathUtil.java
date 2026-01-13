package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.generated.types.HexVector2;
import lombok.experimental.UtilityClass;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * Utility class for hexagonal grid mathematics.
 * Uses pointy-top hexagon orientation (hexagon point facing up).
 * Coordinates are axial (q, r) with cube coordinates s = -q - r.
 */
@UtilityClass
public class HexMathUtil {

    private static final double SQRT_3 = Math.sqrt(3.0);

    /**
     * Generates a position key string from hex coordinates.
     *
     * @param hex The hex vector with q and r coordinates
     * @return Position key in format "q:r"
     */
    public static String positionKey(HexVector2 hex) {
        if (hex == null) {
            throw new IllegalArgumentException("HexVector2 cannot be null");
        }
        return hex.getQ() + ":" + hex.getR();
    }

    /**
     * Parses a position key string to hex coordinates.
     *
     * @param key Position key in format "q:r"
     * @return HexVector2 with parsed coordinates
     * @throws IllegalArgumentException if key format is invalid
     */
    public static HexVector2 parsePositionKey(String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Position key cannot be null or empty");
        }

        String[] parts = key.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid position key format: " + key + " (expected 'q:r')");
        }

        try {
            int q = Integer.parseInt(parts[0]);
            int r = Integer.parseInt(parts[1]);
            return HexVector2.builder().q(q).r(r).build();
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid position key format: " + key + " (coordinates must be integers)", e);
        }
    }

    /**
     * Converts hex coordinates to cartesian coordinates (center position).
     * Uses pointy-top hexagon orientation.
     *
     * @param hex The hex vector with q and r coordinates
     * @param gridSize The diameter of the hexagon in blocks
     * @return Array with [x, z] center coordinates
     */
    public static double[] hexToCartesian(HexVector2 hex, int gridSize) {
        if (hex == null) {
            throw new IllegalArgumentException("HexVector2 cannot be null");
        }
        if (gridSize <= 0) {
            throw new IllegalArgumentException("Grid size must be positive");
        }

        double radius = gridSize / 2.0;
        double x = radius * SQRT_3 * (hex.getQ() + hex.getR() / 2.0);
        double z = radius * 1.5 * hex.getR();

        return new double[]{x, z};
    }

    /**
     * Tests if a cartesian point is inside a hexagon.
     * Uses cube coordinate conversion for accurate point-in-hex testing.
     *
     * @param x The x coordinate of the point to test
     * @param z The z coordinate of the point to test
     * @param hexCenterX The x coordinate of the hex center
     * @param hexCenterZ The z coordinate of the hex center
     * @param gridSize The diameter of the hexagon in blocks
     * @return true if the point is inside the hexagon
     */
    public static boolean isPointInHex(double x, double z, double hexCenterX, double hexCenterZ, int gridSize) {
        if (gridSize <= 0) {
            throw new IllegalArgumentException("Grid size must be positive");
        }

        double radius = gridSize / 2.0;

        // Transform to hex-relative coordinates
        double dx = x - hexCenterX;
        double dz = z - hexCenterZ;

        // Convert to cube coordinates (fractional)
        double q = (SQRT_3 / 3.0 * dx - 1.0 / 3.0 * dz) / radius;
        double r = (2.0 / 3.0 * dz) / radius;
        double s = -q - r;

        // Point is inside if all cube coordinate magnitudes <= 1
        return Math.abs(q) <= 1.0 && Math.abs(r) <= 1.0 && Math.abs(s) <= 1.0;
    }

    /**
     * Creates an iterator that lazily generates flat positions within a hexagon.
     * This is memory-efficient for large grid sizes as it doesn't allocate a full set.
     *
     * @param hex The hex vector with q and r coordinates
     * @param gridSize The diameter of the hexagon in blocks
     * @return Iterator over FlatPosition objects within the hexagon
     */
    public static Iterator<FlatPosition> createFlatPositionIterator(HexVector2 hex, int gridSize) {
        if (hex == null) {
            throw new IllegalArgumentException("HexVector2 cannot be null");
        }
        if (gridSize <= 0) {
            throw new IllegalArgumentException("Grid size must be positive");
        }

        return new HexPositionIterator(hex, gridSize);
    }

    public static HexVector2 getNeighborPosition(HexVector2 position, WHexGrid.NEIGHBOR nabor) {
        int q = position.getQ();
        int r = position.getR();
        switch (nabor) {
            case TOP_RIGHT:
                return HexVector2.builder().q(q + 1).r(r - 1).build();
            case RIGHT:
                return HexVector2.builder().q(q + 1).r(r).build();
            case BOTTOM_RIGHT:
                return HexVector2.builder().q(q).r(r + 1).build();
            case BOTTOM_LEFT:
                return HexVector2.builder().q(q - 1).r(r + 1).build();
            case LEFT:
                return HexVector2.builder().q(q - 1).r(r).build();
            case TOP_LEFT:
                return HexVector2.builder().q(q).r(r - 1).build();
            default:
                throw new IllegalArgumentException("Unknown nabor direction: " + nabor);
        }
    }

    /**
     * Internal iterator implementation for lazy position generation.
     */
    private static class HexPositionIterator implements Iterator<FlatPosition> {
        private final double hexCenterX;
        private final double hexCenterZ;
        private final int gridSize;
        private final int minX;
        private final int maxX;
        private final int minZ;
        private final int maxZ;

        private int currentX;
        private int currentZ;
        private FlatPosition nextPosition;
        private boolean hasSearchedNext;

        HexPositionIterator(HexVector2 hex, int gridSize) {
            double[] center = hexToCartesian(hex, gridSize);
            this.hexCenterX = center[0];
            this.hexCenterZ = center[1];
            this.gridSize = gridSize;

            // Calculate bounding box
            this.minX = (int) Math.floor(hexCenterX - gridSize);
            this.maxX = (int) Math.ceil(hexCenterX + gridSize);
            this.minZ = (int) Math.floor(hexCenterZ - gridSize);
            this.maxZ = (int) Math.ceil(hexCenterZ + gridSize);

            // Start iteration
            this.currentX = minX;
            this.currentZ = minZ;
            this.hasSearchedNext = false;
        }

        @Override
        public boolean hasNext() {
            if (!hasSearchedNext) {
                searchNext();
            }
            return nextPosition != null;
        }

        @Override
        public FlatPosition next() {
            if (!hasNext()) {
                throw new NoSuchElementException("No more positions in hexagon");
            }

            FlatPosition result = nextPosition;
            hasSearchedNext = false;
            nextPosition = null;
            return result;
        }

        private void searchNext() {
            hasSearchedNext = true;

            while (currentZ <= maxZ) {
                while (currentX <= maxX) {
                    if (isPointInHex(currentX, currentZ, hexCenterX, hexCenterZ, gridSize)) {
                        nextPosition = new FlatPosition(currentX, currentZ);
                        currentX++;
                        return;
                    }
                    currentX++;
                }

                // Move to next row
                currentZ++;
                currentX = minX;
            }

            // No more positions found
            nextPosition = null;
        }
    }
}
