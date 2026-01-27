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
    private final double shakeStrength;
    private final int blurRadius;

    public HexGridSideBlender(WFlat flat, int width, BuilderContext context, double randomness,
                              double shakeStrength, int blurRadius) {
        this.flat = flat;
        this.context = context;
        this.width = width;
        this.randomness = randomness;
        this.shakeStrength = shakeStrength;
        this.blurRadius = blurRadius;
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

        SideBlender sideBlender = new SideBlender(flat, context, direction, neighborFlat, width, randomness,
                shakeStrength, blurRadius);
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
        private final double shakeStrength;
        private final int blurRadius;
        private final Random random;
        private final long noiseSeed;

        public SideBlender(WFlat flat, BuilderContext context, WHexGrid.SIDE direction,
                           WFlat neighborFlat, int width, double randomness,
                           double shakeStrength, int blurRadius) {
            this.flat = flat;
            this.context = context;
            this.direction = direction;
            this.neighborFlat = neighborFlat;
            this.width = width;
            this.randomness = randomness;
            this.shakeStrength = shakeStrength;
            this.blurRadius = blurRadius;
            // Use flat position for reproducible random, but add direction for variation
            this.random = new Random(flat.getFlatId().hashCode() + direction.ordinal());
            // Create noise seed based on flat position for consistent noise
            this.noiseSeed = (long) flat.getMountX() * 31 + (long) flat.getMountZ() * 37 + direction.ordinal();
        }

        /**
         * Blend the entire side with the neighbor.
         * Algorithm:
         * 1. Calculate the two corner points of the hex side (x1/z1, x2/z2)
         * 2. Calculate radius of the hex grid
         * 3. Extend corners outward along rays from center:
         *    - Outer line: radius+width → (xs1/zs1) to (xs2/zs2)
         *    - Inner line: radius-width → (xb1/zb1) to (xb2/zb2)
         * 4. Walk along the outer straight line from (xs1/zs1) to (xs2/zs2)
         * 5. For each point on outer line, interpolate inward to inner line
         */
        public void blend() {
            log.info("Blending side {} with neighbor flat {}, width={}",
                    direction, neighborFlat.getFlatId(), width);

            // Calculate the two corners of this hex side
            int[] corner1 = getCorner1ForSide(direction);
            int[] corner2 = getCorner2ForSide(direction);

            // Get hex center
            double[] centerPos = getCenterPosition();
            double centerX = centerPos[0];
            double centerZ = centerPos[1];

            // Calculate radius (approximate - use average distance of corners to center)
            double dist1 = Math.sqrt(Math.pow(corner1[0] - centerX, 2) + Math.pow(corner1[1] - centerZ, 2));
            double dist2 = Math.sqrt(Math.pow(corner2[0] - centerX, 2) + Math.pow(corner2[1] - centerZ, 2));
            double radius = (dist1 + dist2) / 2.0;

            // Extend corner1 along ray from center
            double[] outerCorner1 = extendPointAlongRay(centerX, centerZ, corner1[0], corner1[1], radius, width);
            // Inner line extends further inward (1.5x width) for softer fade-out
            double[] innerCorner1 = extendPointAlongRay(centerX, centerZ, corner1[0], corner1[1], radius, -width * 1.5);

            // Extend corner2 along ray from center
            double[] outerCorner2 = extendPointAlongRay(centerX, centerZ, corner2[0], corner2[1], radius, width);
            // Inner line extends further inward (1.5x width) for softer fade-out
            double[] innerCorner2 = extendPointAlongRay(centerX, centerZ, corner2[0], corner2[1], radius, -width * 1.5);

            // Calculate length of outer line
            double dx = outerCorner2[0] - outerCorner1[0];
            double dz = outerCorner2[1] - outerCorner1[1];
            double outerLineLength = Math.sqrt(dx * dx + dz * dz);
            int steps = (int) Math.ceil(outerLineLength);

            if (steps == 0) {
                log.warn("Outer line length is 0 for side {}", direction);
                return;
            }

            int blendedCount = 0;

            // Walk along the outer line
            for (int step = 0; step <= steps; step++) {
                // Interpolate position along outer line
                double t = steps > 0 ? step / (double) steps : 0.5;
                double outerX = outerCorner1[0] * (1 - t) + outerCorner2[0] * t;
                double outerZ = outerCorner1[1] * (1 - t) + outerCorner2[1] * t;

                // Calculate corresponding point on inner line
                double innerX = innerCorner1[0] * (1 - t) + innerCorner2[0] * t;
                double innerZ = innerCorner1[1] * (1 - t) + innerCorner2[1] * t;

                // Apply organic noise to make edges less straight and more natural
                if (randomness > 0.1) {
                    double[] noise = calculateOrganicNoise(outerX, outerZ, t, direction);
                    outerX += noise[0];
                    outerZ += noise[1];
                    innerX += noise[0] * 0.6; // Less variation on inner line
                    innerZ += noise[1] * 0.6;
                }

                // Blend from outer to inner
                if (blendLineInward(outerX, outerZ, innerX, innerZ)) {
                    blendedCount++;
                }
            }

            // Post-processing: Apply shake effect to make transitions more organic
            if (shakeStrength > 0.01) {
                applyShakeEffect(outerCorner1, outerCorner2, innerCorner1, innerCorner2);
            }

            // Post-processing: Apply blur for smoother transitions
            if (blurRadius > 0) {
                applyBlurEffect(outerCorner1, outerCorner2, innerCorner1, innerCorner2);
            }
        }

        /**
         * Calculate organic noise offset for edge positions.
         * Combines multiple sine waves and position-based randomness for natural-looking irregular edges.
         *
         * @param x X position
         * @param z Z position
         * @param t Progress along edge (0.0 to 1.0)
         * @param side Which side we're blending
         * @return [offsetX, offsetZ] noise values
         */
        private double[] calculateOrganicNoise(double x, double z, double t, WHexGrid.SIDE side) {
            // Base frequency for wave patterns
            double freq1 = 0.15; // Large waves
            double freq2 = 0.4;  // Medium waves
            double freq3 = 0.8;  // Small details

            // Combine multiple sine waves for organic feel
            double wave1 = Math.sin(t * Math.PI * 2 * freq1 + noiseSeed * 0.001);
            double wave2 = Math.sin(t * Math.PI * 2 * freq2 + noiseSeed * 0.002);
            double wave3 = Math.sin(t * Math.PI * 2 * freq3 + noiseSeed * 0.003);

            // Position-based noise (pseudo-random but consistent per position)
            long posHash = (long) (x * 73856093) ^ (long) (z * 19349663) ^ (long) (t * 83492791);
            Random posRandom = new Random(noiseSeed + posHash);
            double posNoise = (posRandom.nextDouble() - 0.5) * 2.0;

            // Combine waves with different amplitudes (octaves)
            double combinedNoise = wave1 * 0.5 + wave2 * 0.3 + wave3 * 0.15 + posNoise * 0.05;

            // Scale by randomness parameter (0.0 = no noise, 1.0 = full noise)
            // Use stronger scale for more visible organic edges
            double scale = randomness * width * 0.8; // Max offset is 80% of blend width

            // Perpendicular direction to edge (for offsetting the line)
            // This depends on which side we're on
            double perpX = 0, perpZ = 0;
            switch (side) {
                case EAST:
                case WEST:
                    perpZ = 1.0; // Vertical sides get horizontal noise
                    break;
                case NORTH_EAST:
                case SOUTH_WEST:
                    perpX = 0.707;
                    perpZ = 0.707;
                    break;
                case NORTH_WEST:
                case SOUTH_EAST:
                    perpX = -0.707;
                    perpZ = 0.707;
                    break;
            }

            // Apply noise perpendicular to edge direction
            double offsetX = combinedNoise * scale * perpX;
            double offsetZ = combinedNoise * scale * perpZ;

            return new double[]{offsetX, offsetZ};
        }

        /**
         * Apply shake effect: randomly swap pixels within the blending region.
         * Creates a more organic, less uniform look by disturbing the blend pattern.
         *
         * @param outerCorner1 First corner of outer line
         * @param outerCorner2 Second corner of outer line
         * @param innerCorner1 First corner of inner line
         * @param innerCorner2 Second corner of inner line
         */
        private void applyShakeEffect(double[] outerCorner1, double[] outerCorner2,
                                      double[] innerCorner1, double[] innerCorner2) {
            // Calculate bounding box for the blend region
            double minX = Math.min(Math.min(outerCorner1[0], outerCorner2[0]),
                                  Math.min(innerCorner1[0], innerCorner2[0]));
            double maxX = Math.max(Math.max(outerCorner1[0], outerCorner2[0]),
                                  Math.max(innerCorner1[0], innerCorner2[0]));
            double minZ = Math.min(Math.min(outerCorner1[1], outerCorner2[1]),
                                  Math.min(innerCorner1[1], innerCorner2[1]));
            double maxZ = Math.max(Math.max(outerCorner1[1], outerCorner2[1]),
                                  Math.max(innerCorner1[1], innerCorner2[1]));

            // Clamp to flat bounds
            int startX = Math.max(0, (int) Math.floor(minX));
            int endX = Math.min(flat.getSizeX() - 1, (int) Math.ceil(maxX));
            int startZ = Math.max(0, (int) Math.floor(minZ));
            int endZ = Math.min(flat.getSizeZ() - 1, (int) Math.ceil(maxZ));

            // Calculate number of swaps based on shake strength
            int area = (endX - startX + 1) * (endZ - startZ + 1);
            int numSwaps = (int) (area * shakeStrength * 0.3); // 30% of pixels at max strength

            // Perform random pixel swaps
            for (int i = 0; i < numSwaps; i++) {
                // Pick two random points in the region
                int x1 = startX + random.nextInt(endX - startX + 1);
                int z1 = startZ + random.nextInt(endZ - startZ + 1);
                int x2 = startX + random.nextInt(endX - startX + 1);
                int z2 = startZ + random.nextInt(endZ - startZ + 1);

                // Swap their heights
                int height1 = flat.getLevel(x1, z1);
                int height2 = flat.getLevel(x2, z2);
                flat.setLevel(x1, z1, height2);
                flat.setLevel(x2, z2, height1);
            }
        }

        /**
         * Apply blur/smoothing effect to the blending region.
         * Uses a simple box blur for smooth transitions.
         *
         * @param outerCorner1 First corner of outer line
         * @param outerCorner2 Second corner of outer line
         * @param innerCorner1 First corner of inner line
         * @param innerCorner2 Second corner of inner line
         */
        private void applyBlurEffect(double[] outerCorner1, double[] outerCorner2,
                                     double[] innerCorner1, double[] innerCorner2) {
            // Calculate bounding box for the blend region
            double minX = Math.min(Math.min(outerCorner1[0], outerCorner2[0]),
                                  Math.min(innerCorner1[0], innerCorner2[0]));
            double maxX = Math.max(Math.max(outerCorner1[0], outerCorner2[0]),
                                  Math.max(innerCorner1[0], innerCorner2[0]));
            double minZ = Math.min(Math.min(outerCorner1[1], outerCorner2[1]),
                                  Math.min(innerCorner1[1], innerCorner2[1]));
            double maxZ = Math.max(Math.max(outerCorner1[1], outerCorner2[1]),
                                  Math.max(innerCorner1[1], innerCorner2[1]));

            // Expand region by blur radius
            int startX = Math.max(0, (int) Math.floor(minX) - blurRadius);
            int endX = Math.min(flat.getSizeX() - 1, (int) Math.ceil(maxX) + blurRadius);
            int startZ = Math.max(0, (int) Math.floor(minZ) - blurRadius);
            int endZ = Math.min(flat.getSizeZ() - 1, (int) Math.ceil(maxZ) + blurRadius);

            // Create temporary buffer for blur result
            int[][] blurred = new int[endX - startX + 1][endZ - startZ + 1];

            // Apply box blur
            for (int x = startX; x <= endX; x++) {
                for (int z = startZ; z <= endZ; z++) {
                    int sum = 0;
                    int count = 0;

                    // Sample neighbors within blur radius
                    for (int dx = -blurRadius; dx <= blurRadius; dx++) {
                        for (int dz = -blurRadius; dz <= blurRadius; dz++) {
                            int nx = x + dx;
                            int nz = z + dz;

                            if (nx >= 0 && nx < flat.getSizeX() &&
                                nz >= 0 && nz < flat.getSizeZ()) {
                                sum += flat.getLevel(nx, nz);
                                count++;
                            }
                        }
                    }

                    blurred[x - startX][z - startZ] = count > 0 ? sum / count : flat.getLevel(x, z);
                }
            }

            // Write blurred values back to flat
            for (int x = startX; x <= endX; x++) {
                for (int z = startZ; z <= endZ; z++) {
                    flat.setLevel(x, z, blurred[x - startX][z - startZ]);
                }
            }
        }

        /**
         * Get first corner of the hex side (in local flat coordinates).
         * Uses actual hexagon geometry based on radius.
         * Flat-top hexagon: EAST/WEST are vertical sides at ±30° angles.
         */
        private int[] getCorner1ForSide(WHexGrid.SIDE side) {
            int sizeX = flat.getSizeX();
            int sizeZ = flat.getSizeZ();
            double centerX = sizeX / 2.0;
            double centerZ = sizeZ / 2.0;
            double radius = sizeX / 2.0;

            // Pointy-top hexagon (EAST/WEST are vertical sides)
            // Image coordinates: X right, Z down
            // Corners at: 30° (right-down), 90° (down), 150° (left-down),
            //             210° (left-up), 270° (up), 330° (right-up)
            double angle;
            switch (side) {
                case NORTH_EAST:
                    angle = Math.toRadians(270); // Top corner
                    break;
                case EAST:
                    angle = Math.toRadians(330); // Right-upper corner
                    break;
                case SOUTH_EAST:
                    angle = Math.toRadians(30);  // Right-lower corner
                    break;
                case SOUTH_WEST:
                    angle = Math.toRadians(150); // Left-lower corner
                    break;
                case WEST:
                    angle = Math.toRadians(210); // Left-upper corner
                    break;
                case NORTH_WEST:
                    angle = Math.toRadians(270); // Top corner
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
         * Uses actual hexagon geometry based on radius.
         * Flat-top hexagon: EAST/WEST are vertical sides at ±30° angles.
         */
        private int[] getCorner2ForSide(WHexGrid.SIDE side) {
            int sizeX = flat.getSizeX();
            int sizeZ = flat.getSizeZ();
            double centerX = sizeX / 2.0;
            double centerZ = sizeZ / 2.0;
            double radius = sizeX / 2.0;

            // Second corner for each side
            double angle;
            switch (side) {
                case NORTH_EAST:
                    angle = Math.toRadians(330); // Right-upper corner
                    break;
                case EAST:
                    angle = Math.toRadians(30);  // Right-lower corner
                    break;
                case SOUTH_EAST:
                    angle = Math.toRadians(90);  // Bottom corner
                    break;
                case SOUTH_WEST:
                    angle = Math.toRadians(90);  // Bottom corner
                    break;
                case WEST:
                    angle = Math.toRadians(150); // Left-lower corner
                    break;
                case NORTH_WEST:
                    angle = Math.toRadians(210); // Left-upper corner
                    break;
                default:
                    return new int[]{0, 0};
            }

            int x = (int) Math.round(centerX + radius * Math.cos(angle));
            int z = (int) Math.round(centerZ + radius * Math.sin(angle));
            return new int[]{x, z};
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

            boolean loggedFirst = false;

            for (int dist = 1; dist <= maxSampleDist; dist++) {
                // Sample main direction
                int sampleX = edgeX + (int) Math.round(outwardDir[0] * dist);
                int sampleZ = edgeZ + (int) Math.round(outwardDir[1] * dist);

                // Get height at this sample point
                double height = getNeighborHeight(sampleX, sampleZ);
                if (height >= 0) {
                    sumHeight += height;
                    validSamples++;

                    if (!loggedFirst && validSamples == 1) {
                        log.debug("First sample at edge({},{}) + outward({},{}) * {} = sample({},{}) -> height={}",
                                edgeX, edgeZ,
                                String.format("%.2f", outwardDir[0]), String.format("%.2f", outwardDir[1]),
                                dist, sampleX, sampleZ, height);
                        loggedFirst = true;
                    }
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
         * The local coordinates can be outside our flat bounds (this is normal for sampling the neighbor).
         * Returns -1 if not available in neighbor flat.
         */
        private double getNeighborHeight(int localX, int localZ) {
            // Convert to world coordinates (can be outside our flat bounds)
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
         * Extend a point along a ray from center.
         * Returns new position at distance (currentDist + extension) from center.
         * The radius parameter is not used (was a bug) - we use the actual distance instead.
         */
        private double[] extendPointAlongRay(double centerX, double centerZ, double pointX, double pointZ,
                                              double radius, double extension) {
            // Direction from center to point
            double dx = pointX - centerX;
            double dz = pointZ - centerZ;
            double currentDist = Math.sqrt(dx * dx + dz * dz);

            if (currentDist == 0) {
                return new double[]{pointX, pointZ};
            }

            // Normalize direction
            double dirX = dx / currentDist;
            double dirZ = dz / currentDist;

            // New distance from center: use ACTUAL distance, not average radius
            double newDist = currentDist + extension;

            // Calculate new position
            double newX = centerX + dirX * newDist;
            double newZ = centerZ + dirZ * newDist;

            return new double[]{newX, newZ};
        }

        /**
         * Blend along a straight line from outer to inner point.
         * Samples neighbor height at outer point (may be outside our flat).
         * Clamps the blending line to stay within flat bounds.
         * Returns true if any blending was performed.
         */
        private boolean blendLineInward(double outerX, double outerZ, double innerX, double innerZ) {
            // Sample neighbor height at outer position (can be outside our flat)
            double neighborHeight = getNeighborHeight((int) Math.round(outerX), (int) Math.round(outerZ));

            if (neighborHeight < 0) {
                // No valid neighbor sample
                return false;
            }

            // Clamp outer position to flat bounds (so blending starts at the boundary, not outside)
            double clampedOuterX = Math.max(0, Math.min(flat.getSizeX() - 1, outerX));
            double clampedOuterZ = Math.max(0, Math.min(flat.getSizeZ() - 1, outerZ));

            // Calculate line length and direction (from clamped outer to inner)
            double dx = innerX - clampedOuterX;
            double dz = innerZ - clampedOuterZ;
            double lineLength = Math.sqrt(dx * dx + dz * dz);

            if (lineLength == 0) {
                // If clamped outer == inner, just set the single pixel
                int xi = (int) Math.round(clampedOuterX);
                int zi = (int) Math.round(clampedOuterZ);
                if (xi >= 0 && xi < flat.getSizeX() && zi >= 0 && zi < flat.getSizeZ()) {
                    flat.setLevel(xi, zi, (int) Math.round(neighborHeight));
                    return true;
                }
                return false;
            }

            // Number of steps along the line (at least 1 step per pixel)
            int steps = (int) Math.ceil(lineLength);

            int pixelsWritten = 0;

            // Walk the line from clamped outer to inner
            for (int step = 0; step <= steps; step++) {
                // Interpolate position along line (from clamped outer to inner)
                double t = steps > 0 ? step / (double) steps : 0.5;
                double x = clampedOuterX * (1 - t) + innerX * t;
                double z = clampedOuterZ * (1 - t) + innerZ * t;

                int xi = (int) Math.round(x);
                int zi = (int) Math.round(z);

                // Check bounds (should always be in bounds now, but double-check)
                if (xi < 0 || xi >= flat.getSizeX() || zi < 0 || zi >= flat.getSizeZ()) {
                    continue;
                }

                // Get current height at this position
                int currentHeight = flat.getLevel(xi, zi);

                // Calculate blend factor: 1.0 at clamped outer (step=0, t=0), 0.0 at inner (step=steps, t=1)
                // Use smoothstep for smooth transition
                double smoothT = t * t * (3.0 - 2.0 * t);

                pixelsWritten++;

                // Apply curve variation for organic feel (if randomness enabled)
                if (randomness > 0) {
                    double curveVar = 1.0 + (random.nextDouble() - 0.5) * randomness * 0.3;
                    smoothT = Math.pow(smoothT, curveVar);
                }

                float blendFactor = 1.0f - (float) smoothT;

                // Fade zone: In the last 40% of the blend (t > 0.6), gradually reduce blend strength
                // This creates a softer edge at the inner boundary
                if (t > 0.6) {
                    double fadeT = (t - 0.6) / 0.4;  // 0.0 at t=0.6, 1.0 at t=1.0
                    double fadeFactor = 1.0 - fadeT; // 1.0 at t=0.6, 0.0 at t=1.0
                    blendFactor *= fadeFactor;
                }

                // Add minimal random variation at high randomness
                if (randomness > 0.5) {
                    float randomVar = (random.nextFloat() - 0.5f) * 0.05f * (float) randomness;
                    blendFactor = Math.max(0.0f, Math.min(1.0f, blendFactor + randomVar));
                }

                // Interpolate height
                float interpolated = (float) (neighborHeight * blendFactor + currentHeight * (1.0f - blendFactor));

                // Add subtle height noise at high randomness
                int heightNoise = 0;
                if (randomness > 0.7 && step > 0 && step < steps) {
                    heightNoise = (random.nextInt(3) - 1);
                }

                int blendedHeight = Math.round(interpolated) + heightNoise;
                blendedHeight = Math.max(0, Math.min(255, blendedHeight));

                // Set the blended height
                flat.setLevel(xi, zi, blendedHeight);
            }

            return pixelsWritten > 0;
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
