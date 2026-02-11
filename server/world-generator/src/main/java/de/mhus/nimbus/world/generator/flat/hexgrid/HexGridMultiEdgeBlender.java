package de.mhus.nimbus.world.generator.flat.hexgrid;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.util.HexMathUtil;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Random;

/**
 * Helper class for blending edges between multiple neighboring hex grids simultaneously.
 * Uses area-based blending similar to EdgeFiller but with smooth fading instead of copying.
 */
@Slf4j
public class HexGridMultiEdgeBlender {

    private final WFlat centerFlat;
    private final HashMap<WHexGrid.EDGE, WFlat> neighbors;
    private final int width;
    private final BuilderContext context;
    private final Random random;
    private final FlatProjection projection;
    private final int range;

    public HexGridMultiEdgeBlender(WFlat centerFlat, HashMap<WHexGrid.EDGE, WFlat> neighbors,
                                   int width, int range, BuilderContext context) {
        this.centerFlat = centerFlat;
        this.neighbors = neighbors;
        this.width = width;
        this.context = context;
        this.random = new Random(centerFlat.getFlatId().hashCode());
        this.range = range;

        // Initialize projection system with all flats
        this.projection = new FlatProjection(centerFlat, neighbors);
    }

    /**
     * Blend all edges between center flat and its neighbors.
     */
    public void blendAllEdges() {
        log.debug("Starting multi-edge blending for center flat: {}, neighbors: {}",
                centerFlat.getFlatId(), neighbors.keySet());

        for (var entry : neighbors.entrySet()) {
            WHexGrid.EDGE side = entry.getKey();
            WFlat neighborFlat = entry.getValue();

            log.debug("Blending edge {} with neighbor {}", side, neighborFlat.getFlatId());
            blendEdge(side, range);
        }

        log.debug("Multi-edge blending completed");
    }

    /**
     * Blend a single edge using projection system - works in world coordinates.
     * Uses simple perpendicular distance from edge boundary.
     */
    private void blendEdge(WHexGrid.EDGE direction, int range) {
        // Get edge corners in world coordinates (both at once to avoid duplicate calculation)
        int[][] corners = getHexSideCorners(direction, centerFlat);
        int[] corner1World = corners[0];
        int[] corner2World = corners[1];

        // Calculate bounding box for blend zone in world coordinates
        int worldMinX = Math.min(corner1World[0], corner2World[0]) - width;
        int worldMaxX = Math.max(corner1World[0], corner2World[0]) + width;
        int worldMinZ = Math.min(corner1World[1], corner2World[1]) - width;
        int worldMaxZ = Math.max(corner1World[1], corner2World[1]) + width;

        int blendedPixels = 0;
        int totalPixels = 0;
        int skippedEmpty = 0;

        log.debug("Blend zone for {}: world coords ({},{}) to ({},{}) with range {}",
                  direction, worldMinX, worldMinZ, worldMaxX, worldMaxZ, range);

        // Iterate over the blend zone in world coordinates
        for (int worldZ = worldMinZ; worldZ < worldMaxZ; worldZ++) {
            for (int worldX = worldMinX; worldX < worldMaxX; worldX++) {
                totalPixels++;

                // Get current height via projection
                Integer currentHeight = projection.getLevel(worldX, worldZ);
                if (currentHeight == null || currentHeight == 0) {
                    skippedEmpty++;
                    continue;
                }

                // Calculate perpendicular distance to edge line in world coordinates
                double distanceToEdge = calculateDistanceToEdgeLine(
                    worldX, worldZ, corner1World, corner2World);

                // Skip if outside blend zone
                if (distanceToEdge > width) {
                    continue;
                }

                // Calculate blend factor (0.0 = at edge, 1.0 = far from edge)
                double blendFactor = distanceToEdge / width;
                blendFactor = blendFactor * blendFactor * (3.0 - 2.0 * blendFactor); // smoothstep

                // Sample surrounding area for averaging
                int sampleCount = 0;
                int heightSum = 0;
                for (int dy = -range; dy <= range; dy++) {
                    for (int dx = -range; dx <= range; dx++) {
                        Integer sampleHeight = projection.getLevel(worldX + dx, worldZ + dy);
                        if (sampleHeight != null && sampleHeight > 0) {
                            heightSum += sampleHeight;
                            sampleCount++;
                        }
                    }
                }

                if (sampleCount < 3) {
                    continue; // Not enough samples
                }

                int avgHeight = heightSum / sampleCount;

                // Calculate adjustment - more aggressive blending
                int heightDifference = avgHeight - currentHeight;
                double adjustmentFactor = (1.0 - blendFactor); // Stronger effect near edge
                double rawAdjustment = heightDifference * adjustmentFactor;
                //double rawAdjustment = 255; //XXX
                int adjustment = (int) Math.round(rawAdjustment);

                // Apply adjustment
                int newHeight = currentHeight + adjustment;
                newHeight = Math.max(0, Math.min(255, newHeight));

                // Write back (always write, even if adjustment is small)
                projection.setLevel(worldX, worldZ, newHeight);
                blendedPixels++;
            }
        }

        log.debug("Blended {} pixels for edge {} (total={}, empty={})",
                  blendedPixels, direction, totalPixels, skippedEmpty);
    }

    /**
     * Calculate perpendicular distance from point to edge line in world coordinates.
     */
    private double calculateDistanceToEdgeLine(int worldX, int worldZ,
                                               int[] corner1, int[] corner2) {
        // Calculate perpendicular distance to line from corner1 to corner2
        double dx = corner2[0] - corner1[0];
        double dz = corner2[1] - corner1[1];
        double lineLength = Math.sqrt(dx * dx + dz * dz);

        if (lineLength < 1) {
            return Math.hypot(worldX - corner1[0], worldZ - corner1[1]);
        }

        // Project point onto line
        double t = ((worldX - corner1[0]) * dx + (worldZ - corner1[1]) * dz) / (lineLength * lineLength);
        t = Math.max(0, Math.min(1, t));

        double projX = corner1[0] + t * dx;
        double projZ = corner1[1] + t * dz;

        return Math.hypot(worldX - projX, worldZ - projZ);
    }

    /**
     * Projection system: Maps world coordinates to the appropriate flat.
     * Automatically determines which flat a coordinate belongs to and provides
     * read/write access across all flats.
     */
    private static class FlatProjection {
        private final WFlat centerFlat;
        private final HashMap<WHexGrid.EDGE, WFlat> neighbors;
        private final java.util.List<WFlat> allFlats;

        public FlatProjection(WFlat centerFlat, HashMap<WHexGrid.EDGE, WFlat> neighbors) {
            this.centerFlat = centerFlat;
            this.neighbors = neighbors;
            this.allFlats = new java.util.ArrayList<>();
            this.allFlats.add(centerFlat);
            this.allFlats.addAll(neighbors.values());
        }

        /**
         * Get level at world coordinates.
         * Automatically finds the correct flat and returns the level.
         * Returns null if coordinates are not in any flat.
         */
        public Integer getLevel(int worldX, int worldZ) {
            // Try all flats to find which one contains this coordinate
            for (WFlat flat : allFlats) {
                int localX = worldX - flat.getMountX();
                int localZ = worldZ - flat.getMountZ();

                if (localX >= 0 && localX < flat.getSizeX() &&
                    localZ >= 0 && localZ < flat.getSizeZ()) {
                    // This flat maybe contains the coordinate
                    var material = flat.getColumn(localX, localZ); // Ensure column is loaded for debugging
                    if (material != WFlat.MATERIAL_NOT_SET) { // this means the coordinate is in the hex grid area, not in the border
                        return flat.getLevel(localX, localZ);
                    }
                }
            }

            return null; // Coordinate not in any flat
        }

        /**
         * Set level at world coordinates.
         * Automatically finds the correct flat and sets the level.
         * Returns true if successful, false if coordinates are not in any flat.
         */
        public boolean setLevel(int worldX, int worldZ, int level) {
            // Try all flats to find which one contains this coordinate
            boolean found = false;
            for (WFlat flat : allFlats) {
                int localX = worldX - flat.getMountX();
                int localZ = worldZ - flat.getMountZ();

                if (localX >= 0 && localX < flat.getSizeX() &&
                    localZ >= 0 && localZ < flat.getSizeZ()) {

                    var material = flat.getColumn(localX, localZ); // Ensure column is loaded for debugging
                    if (material != WFlat.MATERIAL_NOT_SET) { // this means the coordinate is in the hex grid area, not in the border
                        // This flat contains the coordinate
                        int oldLevel = flat.getLevel(localX, localZ);
                        flat.setLevel(localX, localZ, level);

                        // Debug: log first few writes
                        if (writeCount++ < 5) {
                            log.debug("Write: world({},{}) -> flat={} local({},{}) level {} -> {}",
                                    worldX, worldZ, flat.getFlatId(), localX, localZ, oldLevel, level);
                        }
                        found = true;
                    }
                }
            }

            return found; // Coordinate not in any flat
        }

        private int writeCount = 0;
    }

    /**
     * Get both corners of a hex side in world coordinates.
     * Returns array with [corner1, corner2] where each corner is [worldX, worldZ].
     * Calculates both corners at once to avoid duplicate flat parsing and hex center calculation.
     */
    private int[][] getHexSideCorners(WHexGrid.EDGE side, WFlat flat) {
        double angle1, angle2;

        switch (side) {
            case NORTH_EAST:
                angle1 = Math.toRadians(270);  // Top
                angle2 = Math.toRadians(330);  // Top-right
                break;
            case EAST:
                angle1 = Math.toRadians(330);  // Top-right
                angle2 = Math.toRadians(30);   // Bottom-right
                break;
            case SOUTH_EAST:
                angle1 = Math.toRadians(30);   // Bottom-right
                angle2 = Math.toRadians(90);   // Bottom
                break;
            case SOUTH_WEST:
                angle1 = Math.toRadians(150);  // Bottom-left
                angle2 = Math.toRadians(90);   // Bottom
                break;
            case WEST:
                angle1 = Math.toRadians(210);  // Top-left
                angle2 = Math.toRadians(150);  // Bottom-left
                break;
            case NORTH_WEST:
                angle1 = Math.toRadians(270);  // Top
                angle2 = Math.toRadians(210);  // Top-left
                break;
            default:
                return new int[][]{{0, 0}, {0, 0}};
        }

        return new int[][]{
            getHexCorner(flat, angle1),
            getHexCorner(flat, angle2)
        };
    }

    /**
     * Calculate hex corner position in world coordinates.
     * @param flat The flat to get hex center from
     * @param angleRadians Angle in radians for the corner
     * @return Corner position in world coordinates [worldX, worldZ]
     */
    private int[] getHexCorner(WFlat flat, double angleRadians) {
        int gridSize = context.getWorld().getPublicData().getHexGridSize();
        double radius = gridSize / 2.0;

        // Get hex coordinates directly from flat.hexGrid
        HexVector2 hexVec = flat.getHexGrid();
        if (hexVec == null) {
            log.error("Flat {} has no hexGrid set", flat.getFlatId());
            return new int[]{0, 0};
        }

        // Get hex center in world coordinates (without borders)
        double[] worldCenter = HexMathUtil.hexToCartesian(hexVec, gridSize);

        // Calculate corner in world coordinates
        int worldCornerX = (int) Math.round(worldCenter[0] + radius * Math.cos(angleRadians));
        int worldCornerZ = (int) Math.round(worldCenter[1] + radius * Math.sin(angleRadians));

        return new int[]{worldCornerX, worldCornerZ};
    }
}
