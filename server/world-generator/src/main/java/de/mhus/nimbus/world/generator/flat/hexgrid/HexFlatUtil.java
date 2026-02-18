package de.mhus.nimbus.world.generator.flat.hexgrid;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.world.shared.util.HexMathUtil;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.experimental.UtilityClass;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility for hex flat calculations.
 * Flat coordinates use the same hex coordinate system as the 3D world (HexMathUtil):
 * NORTH = r+1, SOUTH = r-1, with odd-r offset stagger for Q.
 * Provides neighbor ID resolution, edge coordinate calculations,
 * and hex side corner lookups for flat operations.
 */
@UtilityClass
public class HexFlatUtil {

    /**
     * Calculate all neighbor flat IDs for a given flat ID.
     * Flat IDs follow the format "prefix_q_r" (e.g., "genesis_0_0").
     * Uses Z-flipped odd-r offset hex coordinates.
     *
     * @param flatId The center flat ID
     * @return Map of EDGE to neighbor flat ID, empty map if flatId format is invalid
     */
    public static Map<WHexGrid.EDGE, String> getNeighborFlatIds(String flatId) {
        Map<WHexGrid.EDGE, String> neighbors = new HashMap<>();

        String[] parts = flatId.split("_");
        if (parts.length != 3) {
            return neighbors;
        }

        try {
            String prefix = parts[0];
            int q = Integer.parseInt(parts[1]);
            int r = Integer.parseInt(parts[2]);

            for (WHexGrid.EDGE side : WHexGrid.EDGE.values()) {
                int[] delta = getFlatNeighborDelta(side, r);
                String neighborId = prefix + "_" + (q + delta[0]) + "_" + (r + delta[1]);
                neighbors.put(side, neighborId);
            }
        } catch (NumberFormatException e) {
            // Invalid format, return empty map
        }

        return neighbors;
    }

    /**
     * Get the [dq, dr] delta for a neighbor in the flat coordinate system.
     * Uses the same odd-r offset hex coordinates as {@link HexMathUtil#getNeighborPosition}:
     * - NORTH directions use r+1
     * - SOUTH directions use r-1
     * - EAST/WEST are unchanged (horizontal, no R delta)
     * Q offset depends on row parity (odd-r stagger).
     */
    private static int[] getFlatNeighborDelta(WHexGrid.EDGE side, int currentR) {
        boolean evenRow = (currentR % 2 == 0);

        return switch (side) {
            case EAST -> new int[]{1, 0};
            case WEST -> new int[]{-1, 0};
            case NORTH_EAST -> evenRow ? new int[]{0, 1} : new int[]{1, 1};
            case NORTH_WEST -> evenRow ? new int[]{-1, 1} : new int[]{0, 1};
            case SOUTH_EAST -> evenRow ? new int[]{0, -1} : new int[]{1, -1};
            case SOUTH_WEST -> evenRow ? new int[]{-1, -1} : new int[]{0, -1};
        };
    }

    /**
     * Calculate edge coordinates for a hex side within a flat of given dimensions.
     * Returns start/end positions and direction vectors for iterating along the edge.
     *
     * Hex edges on a pointy-top hexagon (flat coordinate system):
     * <pre>
     *        NW    NE
     *       /        \
     *   W  |          |  E
     *       \        /
     *        SW    SE
     * </pre>
     *
     * @param side  The hex edge
     * @param sizeX Flat width
     * @param sizeZ Flat height
     * @return Edge coordinates with start, end, direction, and length
     */
    public static EdgeCoordinates getEdgeCoordinates(WHexGrid.EDGE side, int sizeX, int sizeZ) {
        return switch (side) {
            case NORTH_EAST -> new EdgeCoordinates(sizeX / 2, 0, sizeX - 1, sizeZ / 4, 1, 1);
            case EAST -> new EdgeCoordinates(sizeX - 1, sizeZ / 4, sizeX - 1, 3 * sizeZ / 4, 0, 1);
            case SOUTH_EAST -> new EdgeCoordinates(sizeX - 1, 3 * sizeZ / 4, sizeX / 2, sizeZ - 1, -1, 1);
            case SOUTH_WEST -> new EdgeCoordinates(sizeX / 2, sizeZ - 1, 0, 3 * sizeZ / 4, -1, -1);
            case WEST -> new EdgeCoordinates(0, 3 * sizeZ / 4, 0, sizeZ / 4, 0, -1);
            case NORTH_WEST -> new EdgeCoordinates(0, sizeZ / 4, sizeX / 2, 0, 1, -1);
        };
    }

    /**
     * Get both corners of a hex side in local flat coordinates.
     * Uses the flat center (sizeX/2, sizeZ/2) as origin and adds corner offsets
     * from {@link HexMathUtil#getCornersForSide}.
     *
     * @param side        The hex edge
     * @param flatSizeX   Flat width in blocks
     * @param flatSizeZ   Flat height in blocks
     * @param hexGridSize Hex grid size (diameter in blocks)
     * @return Array with [corner1, corner2] where each corner is [localX, localZ]
     */
    public static int[][] getHexSideCornersLocal(WHexGrid.EDGE side, int flatSizeX, int flatSizeZ, int hexGridSize) {
        int centerX = flatSizeX / 2;
        int centerZ = flatSizeZ / 2;
        int[][] corners = HexMathUtil.getCornersForSide(side, hexGridSize);
        return new int[][]{
            {centerX + corners[0][0], centerZ + corners[0][1]},
            {centerX + corners[1][0], centerZ + corners[1][1]}
        };
    }

    /**
     * Get both corners of a hex side in world coordinates.
     * Uses {@link HexMathUtil#hexToCartesian} for the hex center and adds corner offsets
     * from {@link HexMathUtil#getCornersForSide}.
     *
     * @param side        The hex edge
     * @param hexPosition Hex position (q, r)
     * @param hexGridSize Hex grid size (diameter in blocks)
     * @return Array with [corner1, corner2] where each corner is [worldX, worldZ]
     */
    public static int[][] getHexSideCornersWorld(WHexGrid.EDGE side, HexVector2 hexPosition, int hexGridSize) {
        int[] worldCenter = HexMathUtil.hexToCartesian(hexPosition, hexGridSize);
        int[][] corners = HexMathUtil.getCornersForSide(side, hexGridSize);
        return new int[][]{
            {worldCenter[0] + corners[0][0], worldCenter[1] + corners[0][1]},
            {worldCenter[0] + corners[1][0], worldCenter[1] + corners[1][1]}
        };
    }

    /**
     * Represents edge coordinates for a hex side.
     * Provides start/end positions, direction vectors, and methods for
     * calculating positions along and perpendicular to the edge.
     */
    public static class EdgeCoordinates {
        private final int startX, startZ, endX, endZ, dirX, dirZ;
        public final int length;

        public EdgeCoordinates(int startX, int startZ, int endX, int endZ, int dirX, int dirZ) {
            this.startX = startX;
            this.startZ = startZ;
            this.endX = endX;
            this.endZ = endZ;
            this.dirX = dirX;
            this.dirZ = dirZ;
            this.length = Math.max(Math.abs(endX - startX), Math.abs(endZ - startZ)) + 1;
        }

        /**
         * Get position along the edge at a given step (0 to length-1).
         * Linearly interpolates between start and end.
         *
         * @return [x, z] position
         */
        public int[] getPosition(int step) {
            float t = (float) step / (length - 1);
            int x = Math.round(startX + (endX - startX) * t);
            int z = Math.round(startZ + (endZ - startZ) * t);
            return new int[]{x, z};
        }

        /**
         * Get position at a perpendicular distance from the edge at a given step.
         * Moves inward from the edge using the perpendicular direction (-dirZ, dirX).
         *
         * @return [x, z] position
         */
        public int[] getPositionAtDistance(int step, int distance) {
            int[] edgePos = getPosition(step);
            int perpX = -dirZ;
            int perpZ = dirX;
            return new int[]{edgePos[0] + perpX * distance, edgePos[1] + perpZ * distance};
        }

        public int getStartX() { return startX; }
        public int getStartZ() { return startZ; }
        public int getEndX() { return endX; }
        public int getEndZ() { return endZ; }
        public int getDirX() { return dirX; }
        public int getDirZ() { return dirZ; }
    }
}
