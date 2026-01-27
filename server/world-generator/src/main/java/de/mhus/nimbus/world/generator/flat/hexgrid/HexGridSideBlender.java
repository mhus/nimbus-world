package de.mhus.nimbus.world.generator.flat.hexgrid;

import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Random;

/**
 * Helper class for blending edges between neighboring hex grids.
 * Ensures smooth transitions at hex boundaries by calculating corner heights
 * and interpolating along edges.
 */
@Slf4j
public class HexGridSideBlender {

    private final WFlat flat;
    private final BuilderContext context;
    private final int width;
    private final double randomness;

    public HexGridSideBlender(WFlat flat, int width, BuilderContext context, double randomness) {
        this.flat = flat;
        this.context = context;
        this.width = width;
        this.randomness = randomness;
    }

    /**
     * Blend all sides of this hex grid with its neighbors.
     */
    public void blendAllSides(HashMap<WHexGrid.SIDE, String> sideFlats) {
        log.debug("Starting side blending for flat: {}, width={}", flat.getFlatId(), width);

        // Blend each side
        for (var side : sideFlats.entrySet()) {
            var neighborFlat = context.getFlatService().findByWorldAndFlatId(context.getWorld().getWorldId(), side.getValue());
            if (neighborFlat == null) {
                log.warn("Neighbor flat not found: {} for side {}", side.getValue(), side.getKey());
                continue;
            }
            blendSide(side.getKey(), neighborFlat);
        }

        log.info("Side blending completed for flat: {}", flat.getFlatId());
    }

    /**
     * Blend a single side with its neighbor.
     */
    private void blendSide(WHexGrid.SIDE direction, WFlat neighborFlat) {
        log.trace("Blending side: {}", direction);

        SideBlender sideBlender = new SideBlender(flat, context, direction, neighborFlat, width, randomness);
        sideBlender.blend();
    }

    /**
     * Helper class for blending a complete side with a neighbor.
     * Takes the neighbor's side data and blends it into our border area.
     */
    private static class SideBlender {
        private final WFlat flat;
        private final BuilderContext context;
        private final WHexGrid.SIDE direction;
        private final WFlat neighborFlat;
        private final int width;
        private final double randomness;
        private final Random random;

        public SideBlender(WFlat flat, BuilderContext context, WHexGrid.SIDE direction,
                           WFlat neighborFlat, int width, double randomness) {
            this.flat = flat;
            this.context = context;
            this.direction = direction;
            this.neighborFlat = neighborFlat;
            this.width = width;
            this.randomness = randomness;
            // Use flat position for reproducible random, but add direction for variation
            this.random = new Random(flat.getFlatId().hashCode() + direction.ordinal());
        }

        /**
         * Blend the entire side with the neighbor.
         * Algorithm:
         * 1. Calculate the two corner points of the hex side
         * 2. Walk along the edge between corners
         * 3. For each point on the edge:
         *    - Sample outward (perpendicular away from hex center) to get average neighbor height
         *    - Blend inward (toward hex center) with decreasing influence
         */
        public void blend() {
            log.info("Blending side {} with neighbor flat {}, width={}",
                    direction, neighborFlat.getFlatId(), width);

            // Calculate the two corners of this hex side
            int[] corner1 = getCorner1ForSide(direction);
            int[] corner2 = getCorner2ForSide(direction);

            log.info("Side {} corners: ({},{}) to ({},{})",
                    direction, corner1[0], corner1[1], corner2[0], corner2[1]);

            // Calculate direction vectors
            double[] centerPos = getCenterPosition();
            double[] outwardDir = getOutwardDirection(corner1, corner2, centerPos);

            log.debug("Hex center: ({},{}), outward direction: ({},{})",
                    centerPos[0], centerPos[1], outwardDir[0], outwardDir[1]);

            int blendedCount = 0;

            // Walk along the edge from corner1 to corner2
            // Use more steps for smoother coverage (at least every 0.5 pixels)
            int dx = Math.abs(corner2[0] - corner1[0]);
            int dz = Math.abs(corner2[1] - corner1[1]);
            int edgeLength = Math.max(dx, dz);
            int steps = Math.max(edgeLength * 2, edgeLength + 10); // Denser sampling for smoothness

            for (int step = 0; step <= steps; step++) {
                // Interpolate position along edge
                double t = steps > 0 ? step / (double) steps : 0.5;
                double edgeXd = corner1[0] * (1 - t) + corner2[0] * t;
                double edgeZd = corner1[1] * (1 - t) + corner2[1] * t;
                int edgeX = (int) Math.round(edgeXd);
                int edgeZ = (int) Math.round(edgeZd);

                // Check bounds
                if (edgeX < 0 || edgeX >= flat.getSizeX() ||
                    edgeZ < 0 || edgeZ >= flat.getSizeZ()) {
                    continue;
                }

                // Sample outward to get average neighbor height
                double avgOutwardHeight = sampleOutward(edgeX, edgeZ, outwardDir);

                if (avgOutwardHeight < 0) {
                    // No valid samples found
                    continue;
                }

                // Blend inward from this edge point
                blendInward(edgeX, edgeZ, outwardDir, avgOutwardHeight);
                blendedCount++;
            }

            log.info("Side {} blending completed: {} edge points processed (steps={})",
                    direction, blendedCount, steps);
        }

        /**
         * Get first corner of the hex side (in local flat coordinates).
         */
        private int[] getCorner1ForSide(WHexGrid.SIDE side) {
            int sizeX = flat.getSizeX();
            int sizeZ = flat.getSizeZ();
            int centerX = sizeX / 2;
            int centerZ = sizeZ / 2;

            switch (side) {
                case NORTH_EAST:
                    return new int[]{centerX, 0};  // Top center
                case EAST:
                    return new int[]{sizeX - 1, centerZ / 2};  // Right upper
                case SOUTH_EAST:
                    return new int[]{sizeX - 1, centerZ + centerZ / 2};  // Right lower
                case SOUTH_WEST:
                    return new int[]{centerX, sizeZ - 1};  // Bottom center
                case WEST:
                    return new int[]{0, centerZ + centerZ / 2};  // Left lower
                case NORTH_WEST:
                    return new int[]{0, centerZ / 2};  // Left upper
                default:
                    return new int[]{0, 0};
            }
        }

        /**
         * Get second corner of the hex side (in local flat coordinates).
         */
        private int[] getCorner2ForSide(WHexGrid.SIDE side) {
            int sizeX = flat.getSizeX();
            int sizeZ = flat.getSizeZ();
            int centerX = sizeX / 2;
            int centerZ = sizeZ / 2;

            switch (side) {
                case NORTH_EAST:
                    return new int[]{sizeX - 1, centerZ / 2};  // Right upper
                case EAST:
                    return new int[]{sizeX - 1, centerZ + centerZ / 2};  // Right lower
                case SOUTH_EAST:
                    return new int[]{centerX, sizeZ - 1};  // Bottom center
                case SOUTH_WEST:
                    return new int[]{0, centerZ + centerZ / 2};  // Left lower
                case WEST:
                    return new int[]{0, centerZ / 2};  // Left upper
                case NORTH_WEST:
                    return new int[]{centerX, 0};  // Top center
                default:
                    return new int[]{0, 0};
            }
        }

        /**
         * Get center position of the hex in local flat coordinates.
         */
        private double[] getCenterPosition() {
            return new double[]{flat.getSizeX() / 2.0, flat.getSizeZ() / 2.0};
        }

        /**
         * Calculate the outward direction (perpendicular to edge, away from center).
         */
        private double[] getOutwardDirection(int[] corner1, int[] corner2, double[] center) {
            // Edge direction vector
            double edgeDx = corner2[0] - corner1[0];
            double edgeDz = corner2[1] - corner1[1];

            // Perpendicular to edge (rotate 90 degrees)
            double perpDx = -edgeDz;
            double perpDz = edgeDx;

            // Normalize
            double length = Math.sqrt(perpDx * perpDx + perpDz * perpDz);
            if (length > 0) {
                perpDx /= length;
                perpDz /= length;
            }

            // Edge midpoint
            double midX = (corner1[0] + corner2[0]) / 2.0;
            double midZ = (corner1[1] + corner2[1]) / 2.0;

            // Vector from center to edge midpoint
            double toCenterDx = midX - center[0];
            double toCenterDz = midZ - center[1];

            // Check if perpendicular points away from center (dot product)
            double dot = perpDx * toCenterDx + perpDz * toCenterDz;

            // If pointing inward, flip direction
            if (dot < 0) {
                perpDx = -perpDx;
                perpDz = -perpDz;
            }

            return new double[]{perpDx, perpDz};
        }

        /**
         * Sample outward from edge point to get average height from neighbor.
         * Uses multiple sample points with slight random offsets for more natural results.
         * Returns -1 if no valid samples found.
         */
        private double sampleOutward(int edgeX, int edgeZ, double[] outwardDir) {
            double sumHeight = 0;
            int validSamples = 0;

            // Sample at multiple distances outward with slight variations
            // Use more samples for better averaging and smoothness
            int maxSampleDist = Math.min(width, 15);

            for (int dist = 1; dist <= maxSampleDist; dist++) {
                // Sample main direction
                int sampleX = edgeX + (int) Math.round(outwardDir[0] * dist);
                int sampleZ = edgeZ + (int) Math.round(outwardDir[1] * dist);

                // Get height at this sample point
                double height = getNeighborHeight(sampleX, sampleZ);
                if (height >= 0) {
                    sumHeight += height;
                    validSamples++;
                }

                // Add lateral samples for better smoothing (±1 pixel perpendicular)
                if (randomness > 0.3) { // Only if we want some variation
                    double perpDx = -outwardDir[1];
                    double perpDz = outwardDir[0];

                    for (int lateral = -1; lateral <= 1; lateral += 2) {
                        int lateralX = sampleX + (int) Math.round(perpDx * lateral);
                        int lateralZ = sampleZ + (int) Math.round(perpDz * lateral);

                        double lateralHeight = getNeighborHeight(lateralX, lateralZ);
                        if (lateralHeight >= 0) {
                            sumHeight += lateralHeight * 0.5; // Lower weight for lateral samples
                            validSamples += 0.5;
                        }
                    }
                }
            }

            return validSamples > 0 ? sumHeight / validSamples : -1;
        }

        /**
         * Get height from neighbor at our local coordinates.
         * Returns -1 if not available.
         */
        private double getNeighborHeight(int localX, int localZ) {
            // Check bounds in our flat
            if (localX < 0 || localX >= flat.getSizeX() ||
                localZ < 0 || localZ >= flat.getSizeZ()) {
                return -1;
            }

            // Convert to world coordinates
            int worldX = flat.getMountX() + localX;
            int worldZ = flat.getMountZ() + localZ;

            // Convert to neighbor coordinates
            int neighborX = worldX - neighborFlat.getMountX();
            int neighborZ = worldZ - neighborFlat.getMountZ();

            // Check bounds in neighbor flat
            if (neighborX < 0 || neighborX >= neighborFlat.getSizeX() ||
                neighborZ < 0 || neighborZ >= neighborFlat.getSizeZ()) {
                return -1;
            }

            // Get height from neighbor
            int material = neighborFlat.getColumn(neighborX, neighborZ);
            if (material == WFlat.MATERIAL_NOT_SET || material == WFlat.MATERIAL_NOT_SET_MUTABLE) {
                return -1;
            }

            return neighborFlat.getLevel(neighborX, neighborZ);
        }

        /**
         * Blend inward from edge point toward hex center with smooth transitions.
         */
        private void blendInward(int edgeX, int edgeZ, double[] outwardDir, double avgOutwardHeight) {
            // Inward direction is opposite of outward
            double inwardDx = -outwardDir[0];
            double inwardDz = -outwardDir[1];

            // Blend from edge (depth=0) to center (depth=width)
            for (int depth = 0; depth <= width; depth++) {
                // Calculate position
                double x = edgeX + inwardDx * depth;
                double z = edgeZ + inwardDz * depth;

                // Add very slight lateral offset for organic feel (scaled by randomness)
                if (randomness > 0.2) {
                    double perpDx = -inwardDz;
                    double perpDz = inwardDx;
                    double lateralOffset = (random.nextDouble() - 0.5) * randomness * 0.5;
                    x += perpDx * lateralOffset;
                    z += perpDz * lateralOffset;
                }

                int xi = (int) Math.round(x);
                int zi = (int) Math.round(z);

                // Check bounds
                if (xi < 0 || xi >= flat.getSizeX() || zi < 0 || zi >= flat.getSizeZ()) {
                    break;
                }

                // Get current height
                int currentHeight = flat.getLevel(xi, zi);

                // Calculate smooth blend factor using smoothstep function
                // This creates a much smoother transition than linear
                double t = depth / (double) width;

                // Smoothstep: 3t² - 2t³ (smooth S-curve)
                double smoothT = t * t * (3.0 - 2.0 * t);

                // Apply slight curve variation for organic feel
                if (randomness > 0) {
                    double curveVar = 1.0 + (random.nextDouble() - 0.5) * randomness * 0.3;
                    smoothT = Math.pow(smoothT, curveVar);
                }

                float blendFactor = 1.0f - (float) smoothT;

                // Add minimal random variation (only if randomness is high)
                if (randomness > 0.5) {
                    float randomVar = (random.nextFloat() - 0.5f) * 0.05f * (float) randomness;
                    blendFactor = Math.max(0.0f, Math.min(1.0f, blendFactor + randomVar));
                }

                // Smooth interpolation
                float interpolated = (float) (avgOutwardHeight * blendFactor + currentHeight * (1.0f - blendFactor));

                // Very subtle noise (only at high randomness)
                int heightNoise = 0;
                if (randomness > 0.7 && depth > 0 && depth < width) {
                    heightNoise = (random.nextInt(3) - 1);
                }

                int blendedHeight = Math.round(interpolated) + heightNoise;
                blendedHeight = Math.max(0, Math.min(255, blendedHeight));

                // Set the blended height
                flat.setLevel(xi, zi, blendedHeight);
            }
        }

        /**
         * Check if a point in the neighbor flat is on the side that faces us.
         * Uses a simplified border-based check (points within 15 pixels of the edge).
         */
        private boolean isOnNeighborSide(int nx, int nz, WHexGrid.SIDE side) {
            final int BORDER = 15;
            int sizeX = neighborFlat.getSizeX();
            int sizeZ = neighborFlat.getSizeZ();

            switch (side) {
                case EAST:
                    return nx >= sizeX - BORDER;
                case WEST:
                    return nx < BORDER;
                case NORTH_EAST:
                    return nz < sizeZ / 2 && nx >= sizeX / 2;
                case NORTH_WEST:
                    return nz < sizeZ / 2 && nx < sizeX / 2;
                case SOUTH_EAST:
                    return nz >= sizeZ / 2 && nx >= sizeX / 2;
                case SOUTH_WEST:
                    return nz >= sizeZ / 2 && nx < sizeX / 2;
                default:
                    return false;
            }
        }

        /**
         * Blend a single point from the neighbor into our flat.
         * Sets the point at the given position and blends inward with interpolation.
         */
        private void blendPoint(int startX, int startZ, int neighborHeight, int neighborMaterial) {
            // Get inward direction for blending
            int[] inwardDir = getInwardDirection(direction);

            // Blend from the border point inward for 'width' pixels
            for (int depth = 0; depth <= width; depth++) {
                int x = startX + inwardDir[0] * depth;
                int z = startZ + inwardDir[1] * depth;

                // Check bounds
                if (x < 0 || x >= flat.getSizeX() || z < 0 || z >= flat.getSizeZ()) {
                    break;
                }

                // Get current values
                int currentHeight = flat.getLevel(x, z);
                int currentMaterial = flat.getColumn(x, z);

                // Calculate blend factor (1.0 at border edge, 0.0 at full depth)
                float baseFactor = depth == 0 ? 1.0f : 1.0f - (depth / (float) width);

                // Add random variation (±10%) for more natural transitions
                float randomVariation = (random.nextFloat() - 0.5f) * 0.2f;
                float blendFactor = Math.max(0.0f, Math.min(1.0f, baseFactor + randomVariation));

                // At depth 0 (border edge), always use neighbor data
                if (depth == 0) {
                    flat.setLevel(x, z, neighborHeight);
                    flat.setColumn(x, z, neighborMaterial);
                } else {
                    // Check if we've reached the hex interior (non-mutable area)
                    if (currentMaterial != WFlat.MATERIAL_NOT_SET &&
                        currentMaterial != WFlat.MATERIAL_NOT_SET_MUTABLE) {
                        // Blend with existing interior height
                        int heightVariation = random.nextInt(3) - 1; // -1, 0, +1
                        int blendedHeight = Math.round(neighborHeight * blendFactor + currentHeight * (1.0f - blendFactor)) + heightVariation;
                        blendedHeight = Math.max(0, Math.min(255, blendedHeight));
                        flat.setLevel(x, z, blendedHeight);

                        // Material blending: probabilistic selection
                        if (random.nextFloat() < blendFactor) {
                            flat.setColumn(x, z, neighborMaterial);
                        }
                        // else keep current material
                    } else {
                        // Still in border/empty area
                        int heightVariation = random.nextInt(3) - 1;
                        int blendedHeight = Math.round(neighborHeight * blendFactor + currentHeight * (1.0f - blendFactor)) + heightVariation;
                        blendedHeight = Math.max(0, Math.min(255, blendedHeight));
                        flat.setLevel(x, z, blendedHeight);
                        flat.setColumn(x, z, neighborMaterial);
                    }
                }
            }
        }

        /**
         * Get inward direction vector for blending.
         * Returns [dx, dz] pointing from the side into the flat interior.
         */
        private int[] getInwardDirection(WHexGrid.SIDE side) {
            switch (side) {
                case EAST:
                    return new int[]{-1, 0}; // From right edge, blend left
                case WEST:
                    return new int[]{1, 0}; // From left edge, blend right
                case NORTH_EAST:
                    return new int[]{-1, 1}; // From top-right, blend to bottom-left
                case NORTH_WEST:
                    return new int[]{1, 1}; // From top-left, blend to bottom-right
                case SOUTH_EAST:
                    return new int[]{-1, -1}; // From bottom-right, blend to top-left
                case SOUTH_WEST:
                    return new int[]{1, -1}; // From bottom-left, blend to top-right
                default:
                    return new int[]{0, 0};
            }
        }

        /**
         * Get the mirrored side (opposite side on neighbor).
         */
        private WHexGrid.SIDE getMirroredSide(WHexGrid.SIDE direction) {
            switch (direction) {
                case NORTH_WEST:
                    return WHexGrid.SIDE.SOUTH_EAST;
                case NORTH_EAST:
                    return WHexGrid.SIDE.SOUTH_WEST;
                case EAST:
                    return WHexGrid.SIDE.WEST;
                case SOUTH_EAST:
                    return WHexGrid.SIDE.NORTH_WEST;
                case SOUTH_WEST:
                    return WHexGrid.SIDE.NORTH_EAST;
                case WEST:
                    return WHexGrid.SIDE.EAST;
                default:
                    return direction;
            }
        }
    }
}
