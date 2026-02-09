package de.mhus.nimbus.world.generator.flat.hexgrid;

import de.mhus.nimbus.world.shared.generator.WFlat;
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
    private final double randomness;
    private final double shakeStrength;
    private final int blurRadius;
    private final Random random;
    private final FlatProjection projection;

    public HexGridMultiEdgeBlender(WFlat centerFlat, HashMap<WHexGrid.EDGE, WFlat> neighbors,
                                   int width, BuilderContext context, double randomness,
                                   double shakeStrength, int blurRadius) {
        this.centerFlat = centerFlat;
        this.neighbors = neighbors;
        this.width = width;
        this.context = context;
        this.randomness = randomness;
        this.shakeStrength = shakeStrength;
        this.blurRadius = blurRadius;
        this.random = new Random(centerFlat.getFlatId().hashCode());

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
            blendEdge(side, neighborFlat);
        }

        log.debug("Multi-edge blending completed");
    }

    /**
     * Blend a single edge using projection system - works in world coordinates.
     * Uses simple perpendicular distance from edge boundary.
     */
    private void blendEdge(WHexGrid.EDGE direction, WFlat neighborFlat) {
        // Get the edge boundary in center flat (local coordinates)
        int[] centerArea = getAreaForSide(direction, centerFlat);

        // Calculate edge line position (the actual boundary)
        int edgePosition = getEdgePosition(direction, centerFlat);

        // Define blend zone in local coordinates
        int blendStart, blendEnd;
        boolean isHorizontal = (direction == WHexGrid.EDGE.EAST || direction == WHexGrid.EDGE.WEST);

        if (isHorizontal) {
            // For EAST/WEST, blend along X axis
            if (direction == WHexGrid.EDGE.EAST) {
                blendStart = edgePosition - width;
                blendEnd = edgePosition + width;
            } else {
                blendStart = edgePosition - width;
                blendEnd = edgePosition + width;
            }
        } else {
            // For diagonal edges, blend in both directions
            blendStart = -width;
            blendEnd = width;
        }

        // Convert area to world coordinates
        int worldMinX = centerFlat.getMountX() + centerArea[0] - width;
        int worldMinZ = centerFlat.getMountZ() + centerArea[1] - width;
        int worldMaxX = centerFlat.getMountX() + centerArea[2] + width;
        int worldMaxZ = centerFlat.getMountZ() + centerArea[3] + width;

        int blendedPixels = 0;
        int totalPixels = 0;
        int skippedEmpty = 0;

        log.debug("Blend zone for {}: world coords ({},{}) to ({},{}), edgePos={}",
                  direction, worldMinX, worldMinZ, worldMaxX, worldMaxZ, edgePosition);

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

                // Calculate perpendicular distance to edge boundary
                int localX = worldX - centerFlat.getMountX();
                int localZ = worldZ - centerFlat.getMountZ();

                double distanceToEdge;
                if (direction == WHexGrid.EDGE.EAST) {
                    distanceToEdge = Math.abs(localX - edgePosition);
                } else if (direction == WHexGrid.EDGE.WEST) {
                    distanceToEdge = Math.abs(localX - edgePosition);
                } else {
                    // For diagonal edges, use approximate distance
                    distanceToEdge = calculateDiagonalDistance(localX, localZ, direction, centerFlat);
                }

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
                for (int dy = -3; dy <= 3; dy++) {
                    for (int dx = -3; dx <= 3; dx++) {
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
                double adjustmentFactor = (1.0 - blendFactor) * 0.8; // Stronger effect near edge
                double rawAdjustment = heightDifference * adjustmentFactor;
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
     * Get the edge position (coordinate along the main axis).
     */
    private int getEdgePosition(WHexGrid.EDGE direction, WFlat flat) {
        int sizeX = flat.getSizeX();
        int sizeZ = flat.getSizeZ();

        switch (direction) {
            case EAST:
                return sizeX - 1;
            case WEST:
                return 0;
            case NORTH_EAST:
            case SOUTH_EAST:
                return sizeX - 1;
            case NORTH_WEST:
            case SOUTH_WEST:
                return 0;
            default:
                return sizeX / 2;
        }
    }

    /**
     * Calculate distance to diagonal edge.
     */
    private double calculateDiagonalDistance(int x, int z, WHexGrid.EDGE direction, WFlat flat) {
        int[] corner1 = getCorner1ForSide(direction, flat);
        int[] corner2 = getCorner2ForSide(direction, flat);

        // Calculate perpendicular distance to line from corner1 to corner2
        double dx = corner2[0] - corner1[0];
        double dz = corner2[1] - corner1[1];
        double lineLength = Math.sqrt(dx * dx + dz * dz);

        if (lineLength < 1) {
            return Math.hypot(x - corner1[0], z - corner1[1]);
        }

        // Project point onto line
        double t = ((x - corner1[0]) * dx + (z - corner1[1]) * dz) / (lineLength * lineLength);
        t = Math.max(0, Math.min(1, t));

        double projX = corner1[0] + t * dx;
        double projZ = corner1[1] + t * dz;

        return Math.hypot(x - projX, z - projZ);
    }

    /**
     * Calculate distance from a pixel to the edge of the flat (perpendicular distance).
     * Returns distance in pixels from the outer edge.
     */
    private double calculateDistanceFromEdge(int x, int z, WHexGrid.EDGE direction, WFlat flat) {
        int sizeX = flat.getSizeX();
        int sizeZ = flat.getSizeZ();

        switch (direction) {
            case NORTH_EAST:
                // Distance from top-right edge
                // Use minimum distance to either top or right edge
                return Math.min(z, sizeX - 1 - x);
            case EAST:
                // Distance from right edge
                return sizeX - 1 - x;
            case SOUTH_EAST:
                // Distance from bottom-right edge
                return Math.min(sizeZ - 1 - z, sizeX - 1 - x);
            case SOUTH_WEST:
                // Distance from bottom-left edge
                return Math.min(sizeZ - 1 - z, x);
            case WEST:
                // Distance from left edge
                return x;
            case NORTH_WEST:
                // Distance from top-left edge
                return Math.min(z, x);
            default:
                return 0;
        }
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
                    // This flat contains the coordinate
                    return flat.getLevel(localX, localZ);
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
            for (WFlat flat : allFlats) {
                int localX = worldX - flat.getMountX();
                int localZ = worldZ - flat.getMountZ();

                if (localX >= 0 && localX < flat.getSizeX() &&
                    localZ >= 0 && localZ < flat.getSizeZ()) {
                    // This flat contains the coordinate
                    int oldLevel = flat.getLevel(localX, localZ);
                    flat.setLevel(localX, localZ, level);

                    // Debug: log first few writes
                    if (writeCount++ < 5) {
                        log.debug("Write: world({},{}) -> flat={} local({},{}) level {} -> {}",
                                worldX, worldZ, flat.getFlatId(), localX, localZ, oldLevel, level);
                    }
                    return true;
                }
            }

            return false; // Coordinate not in any flat
        }

        private int writeCount = 0;
    }

    /**
     * Get the area (bounding box) for a side of the flat.
     * Returns [minX, minZ, maxX, maxZ] in local flat coordinates.
     * Based on EdgeFiller implementation.
     */
    private int[] getAreaForSide(WHexGrid.EDGE direction, WFlat flat) {
        int sizeX = flat.getSizeX();
        int sizeZ = flat.getSizeZ();
        int[] corner1 = getCorner1ForSide(direction, flat);
        int[] corner2 = getCorner2ForSide(direction, flat);

        switch (direction) {
            case NORTH_EAST:
                return new int[]{corner1[0], 0, sizeX, corner2[1]};
            case EAST:
                return new int[]{corner1[0], corner1[1], sizeX, corner2[1]};
            case SOUTH_EAST:
                return new int[]{Math.min(corner1[0], corner2[0]), Math.min(corner1[1], corner2[1]), sizeX, sizeZ};
            case SOUTH_WEST:
                return new int[]{0, corner1[1], corner2[0], sizeZ};
            case WEST:
                return new int[]{0, corner1[1], corner2[0], corner2[1]};
            case NORTH_WEST:
                return new int[]{0, 0, Math.max(corner1[0], corner2[0]), Math.max(corner1[1], corner2[1])};
            default:
                return new int[]{0, 0, sizeX, sizeZ};
        }
    }

    /**
     * Get first corner of the hex side (in local flat coordinates).
     * Uses the world's hex grid size, not the flat size.
     */
    private int[] getCorner1ForSide(WHexGrid.EDGE side, WFlat flat) {
        int sizeX = flat.getSizeX();
        int sizeZ = flat.getSizeZ();
        double centerX = sizeX / 2.0;
        double centerZ = sizeZ / 2.0;
        double radius = context.getWorld().getPublicData().getHexGridSize() / 2.0;

        double angle;
        switch (side) {
            case NORTH_EAST:
                angle = Math.toRadians(270);
                break;
            case EAST:
                angle = Math.toRadians(330);
                break;
            case SOUTH_EAST:
                angle = Math.toRadians(30);
                break;
            case SOUTH_WEST:
                angle = Math.toRadians(150);
                break;
            case WEST:
                angle = Math.toRadians(210);
                break;
            case NORTH_WEST:
                angle = Math.toRadians(270);
                break;
            default:
                return new int[]{0, 0};
        }

        int x = (int) Math.round(centerX + radius * Math.cos(angle));
        int z = (int) Math.round(centerZ + radius * Math.sin(angle));
        return new int[]{x, z};
    }

    /**
     * Get second corner of the hex side (in local flat coordinates).
     */
    private int[] getCorner2ForSide(WHexGrid.EDGE side, WFlat flat) {
        int sizeX = flat.getSizeX();
        int sizeZ = flat.getSizeZ();
        double centerX = sizeX / 2.0;
        double centerZ = sizeZ / 2.0;
        double radius = context.getWorld().getPublicData().getHexGridSize() / 2.0;

        double angle;
        switch (side) {
            case NORTH_EAST:
                angle = Math.toRadians(330);
                break;
            case EAST:
                angle = Math.toRadians(30);
                break;
            case SOUTH_EAST:
                angle = Math.toRadians(90);
                break;
            case SOUTH_WEST:
                angle = Math.toRadians(90);
                break;
            case WEST:
                angle = Math.toRadians(150);
                break;
            case NORTH_WEST:
                angle = Math.toRadians(210);
                break;
            default:
                return new int[]{0, 0};
        }

        int x = (int) Math.round(centerX + radius * Math.cos(angle));
        int z = (int) Math.round(centerZ + radius * Math.sin(angle));
        return new int[]{x, z};
    }
}
