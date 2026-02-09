package de.mhus.nimbus.world.generator.flat.hexgrid;

import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

/**
 * Helper class for blending edges between multiple neighboring hex grids simultaneously.
 * Uses a projection system to automatically write to the correct flat based on world coordinates.
 * Ensures smooth transitions at hex boundaries by processing all adjacent flats together.
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

    // Projection system: maps world coordinates to the appropriate flat
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
     * Processes each edge line and writes to the appropriate flats via projection system.
     */
    public void blendAllEdges() {
        log.debug("Starting multi-edge blending for center flat: {}, neighbors: {}",
                centerFlat.getFlatId(), neighbors.keySet());

        // Process each edge
        for (var entry : neighbors.entrySet()) {
            WHexGrid.EDGE side = entry.getKey();
            WFlat neighborFlat = entry.getValue();

            log.debug("Blending edge {} between center and neighbor {}",
                    side, neighborFlat.getFlatId());

            blendEdge(side, neighborFlat);
        }

        // Blur is disabled for multi-edge blending to avoid unintended side effects
        // The blending algorithm itself provides smooth transitions

        log.debug("Multi-edge blending completed");
    }

    /**
     * Blend a single edge between center and neighbor using distance-based fading.
     * Professional approach: Calculate distance from each pixel to the edge line,
     * then fade smoothly based on that distance.
     */
    private void blendEdge(WHexGrid.EDGE direction, WFlat neighborFlat) {
        // Calculate the two corners of this hex side in center flat (local coordinates)
        int[] corner1 = getCorner1ForSide(direction);
        int[] corner2 = getCorner2ForSide(direction);

        // Convert to world coordinates for the edge line
        int worldCorner1X = centerFlat.getMountX() + corner1[0];
        int worldCorner1Z = centerFlat.getMountZ() + corner1[1];
        int worldCorner2X = centerFlat.getMountX() + corner2[0];
        int worldCorner2Z = centerFlat.getMountZ() + corner2[1];

        log.debug("Blending edge {}: corner1=({},{}) corner2=({},{}) in world coords",
                direction, worldCorner1X, worldCorner1Z, worldCorner2X, worldCorner2Z);

        // Calculate edge line vector and normal
        double edgeDx = worldCorner2X - worldCorner1X;
        double edgeDz = worldCorner2Z - worldCorner1Z;
        double edgeLength = Math.sqrt(edgeDx * edgeDx + edgeDz * edgeDz);

        if (edgeLength < 1.0) {
            log.warn("Edge line length too short for side {}", direction);
            return;
        }

        // Normalize edge direction
        double edgeDirX = edgeDx / edgeLength;
        double edgeDirZ = edgeDz / edgeLength;

        // Calculate perpendicular normal (pointing towards center flat)
        double normalX = -edgeDirZ;
        double normalZ = edgeDirX;

        // Determine which side is center and which is neighbor
        // Normal should point from neighbor towards center
        double centerX = centerFlat.getMountX() + centerFlat.getSizeX() / 2.0;
        double centerZ = centerFlat.getMountZ() + centerFlat.getSizeZ() / 2.0;
        double edgeMidX = (worldCorner1X + worldCorner2X) / 2.0;
        double edgeMidZ = (worldCorner1Z + worldCorner2Z) / 2.0;
        double toCenter = (centerX - edgeMidX) * normalX + (centerZ - edgeMidZ) * normalZ;
        if (toCenter < 0) {
            // Flip normal to point towards center
            normalX = -normalX;
            normalZ = -normalZ;
        }

        // Define blend zone: extend width pixels in both directions from edge line
        double blendWidth = width * 1.5; // Total blend width on each side

        // Calculate bounding box for the blend zone (in world coordinates)
        double minX = Math.min(worldCorner1X, worldCorner2X) - blendWidth;
        double maxX = Math.max(worldCorner1X, worldCorner2X) + blendWidth;
        double minZ = Math.min(worldCorner1Z, worldCorner2Z) - blendWidth;
        double maxZ = Math.max(worldCorner1Z, worldCorner2Z) + blendWidth;

        // Extend bounding box perpendicular to edge line
        minX -= blendWidth;
        maxX += blendWidth;
        minZ -= blendWidth;
        maxZ += blendWidth;

        int pixelsBlended = 0;

        // Iterate over all pixels in the bounding box
        for (int worldX = (int) Math.floor(minX); worldX <= (int) Math.ceil(maxX); worldX++) {
            for (int worldZ = (int) Math.floor(minZ); worldZ <= (int) Math.ceil(maxZ); worldZ++) {

                // Calculate signed distance from this pixel to the edge line
                // Distance is positive on center side, negative on neighbor side
                double signedDistance = calculateSignedDistanceToEdgeLine(
                        worldX, worldZ,
                        worldCorner1X, worldCorner1Z,
                        worldCorner2X, worldCorner2Z,
                        normalX, normalZ);

                // Check if pixel is within blend zone
                if (Math.abs(signedDistance) > blendWidth) {
                    continue; // Outside blend zone
                }

                // Calculate blend factor based on distance
                // 0.0 = full neighbor (negative distance)
                // 1.0 = full center (positive distance)
                // 0.5 = exactly on edge line (distance = 0)
                double normalizedDistance = signedDistance / blendWidth; // Range: -1.0 to 1.0
                double blendFactor = (normalizedDistance + 1.0) / 2.0; // Range: 0.0 to 1.0

                // Apply smooth fade (smoothstep)
                blendFactor = blendFactor * blendFactor * (3.0 - 2.0 * blendFactor);

                // Add randomness for organic look
                if (randomness > 0.1) {
                    double noise = calculateNoiseAtPosition(worldX, worldZ, direction);
                    blendFactor = Math.max(0.0, Math.min(1.0, blendFactor + noise * randomness * 0.1));
                }

                // Get heights from both flats
                Integer centerHeight = projection.getLevel(worldX, worldZ);
                if (centerHeight == null) {
                    continue; // Pixel not in any flat
                }

                // Sample neighbor height by looking slightly towards the neighbor
                int neighborSampleX = (int) Math.round(worldX - normalX * blendWidth * 0.5);
                int neighborSampleZ = (int) Math.round(worldZ - normalZ * blendWidth * 0.5);
                Integer neighborHeight = projection.getLevel(neighborSampleX, neighborSampleZ);
                if (neighborHeight == null) {
                    neighborHeight = centerHeight; // Fallback
                }

                // Blend heights
                int blendedHeight = (int) Math.round(centerHeight * blendFactor + neighborHeight * (1.0 - blendFactor));
                blendedHeight = Math.max(0, Math.min(255, blendedHeight));

                // Write blended height to the appropriate flat
                boolean written = projection.setLevel(worldX, worldZ, blendedHeight);
                if (written) {
                    pixelsBlended++;
                }
            }
        }

        log.debug("Blended {} pixels for edge {}", pixelsBlended, direction);
    }

    /**
     * Calculate signed distance from a point to an infinite line defined by two points.
     * The sign is determined by the normal vector: positive if point is on normal side.
     */
    private double calculateSignedDistanceToEdgeLine(
            double px, double pz,
            double line1X, double line1Z,
            double line2X, double line2Z,
            double normalX, double normalZ) {

        // Vector from line point 1 to the test point
        double toPx = px - line1X;
        double toPz = pz - line1Z;

        // Project onto normal to get signed distance
        double distance = toPx * normalX + toPz * normalZ;

        return distance;
    }

    /**
     * Calculate noise value at a specific position for organic variation.
     */
    private double calculateNoiseAtPosition(int worldX, int worldZ, WHexGrid.EDGE side) {
        long seed = (long) centerFlat.getMountX() * 31 + (long) centerFlat.getMountZ() * 37 + side.ordinal();
        long hash = seed ^ ((long) worldX * 73856093) ^ ((long) worldZ * 19349663);
        Random rnd = new Random(hash);
        return (rnd.nextDouble() - 0.5) * 2.0; // Range: -1.0 to 1.0
    }


    /**
     * Apply blur effect to all modified neighbor flats.
     * The center flat is NOT blurred here as it will be saved by the main builder.
     */
    private void applyBlurToAllFlats() {
        log.debug("Applying blur with radius {} to neighbor flats only", blurRadius);

        // Only blur neighbor flats, NOT the center flat
        // The center flat will be handled by the main builder
        for (WFlat neighborFlat : neighbors.values()) {
            applyBlurToFlat(neighborFlat);
        }
    }

    /**
     * Apply blur to a single flat.
     * Uses a simple box blur for smooth transitions.
     */
    private void applyBlurToFlat(WFlat flat) {
        int sizeX = flat.getSizeX();
        int sizeZ = flat.getSizeZ();

        // Create temporary buffer for blur result
        int[][] blurred = new int[sizeX][sizeZ];

        // Apply box blur to entire flat
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                int sum = 0;
                int count = 0;

                // Sample neighbors within blur radius
                for (int dx = -blurRadius; dx <= blurRadius; dx++) {
                    for (int dz = -blurRadius; dz <= blurRadius; dz++) {
                        int nx = x + dx;
                        int nz = z + dz;

                        if (nx >= 0 && nx < sizeX && nz >= 0 && nz < sizeZ) {
                            sum += flat.getLevel(nx, nz);
                            count++;
                        }
                    }
                }

                blurred[x][z] = count > 0 ? sum / count : flat.getLevel(x, z);
            }
        }

        // Write blurred values back to flat
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                flat.setLevel(x, z, blurred[x][z]);
            }
        }
    }

    /**
     * Get first corner of the hex side (in local flat coordinates).
     */
    private int[] getCorner1ForSide(WHexGrid.EDGE side) {
        int sizeX = centerFlat.getSizeX();
        int sizeZ = centerFlat.getSizeZ();
        double centerX = sizeX / 2.0;
        double centerZ = sizeZ / 2.0;
        double radius = sizeX / 2.0;

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
    private int[] getCorner2ForSide(WHexGrid.EDGE side) {
        int sizeX = centerFlat.getSizeX();
        int sizeZ = centerFlat.getSizeZ();
        double centerX = sizeX / 2.0;
        double centerZ = sizeZ / 2.0;
        double radius = sizeX / 2.0;

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

    /**
     * Extend a point along a ray from center.
     */
    private double[] extendPointAlongRay(double centerX, double centerZ, double pointX, double pointZ,
                                          double radius, double extension) {
        double dx = pointX - centerX;
        double dz = pointZ - centerZ;
        double currentDist = Math.sqrt(dx * dx + dz * dz);

        if (currentDist == 0) {
            return new double[]{pointX, pointZ};
        }

        double dirX = dx / currentDist;
        double dirZ = dz / currentDist;
        double newDist = currentDist + extension;

        double newX = centerX + dirX * newDist;
        double newZ = centerZ + dirZ * newDist;

        return new double[]{newX, newZ};
    }

    /**
     * Projection system: Maps world coordinates to the appropriate flat.
     * Automatically determines which flat a coordinate belongs to and provides
     * read/write access across all flats.
     */
    private static class FlatProjection {
        private final WFlat centerFlat;
        private final HashMap<WHexGrid.EDGE, WFlat> neighbors;
        private final List<WFlat> allFlats;

        public FlatProjection(WFlat centerFlat, HashMap<WHexGrid.EDGE, WFlat> neighbors) {
            this.centerFlat = centerFlat;
            this.neighbors = neighbors;
            this.allFlats = new ArrayList<>();
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
                    flat.setLevel(localX, localZ, level);
                    return true;
                }
            }

            return false; // Coordinate not in any flat
        }
    }
}
