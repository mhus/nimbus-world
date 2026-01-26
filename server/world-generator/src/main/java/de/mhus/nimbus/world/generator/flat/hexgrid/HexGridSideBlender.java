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

    public HexGridSideBlender(WFlat flat, int width, BuilderContext context) {
        this.flat = flat;
        this.context = context;
        this.width = width;
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

        SideBlender sideBlender = new SideBlender(flat, context, direction, neighborFlat, width);
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
        private final Random random;

        public SideBlender(WFlat flat, BuilderContext context, WHexGrid.SIDE direction,
                           WFlat neighborFlat, int width) {
            this.flat = flat;
            this.context = context;
            this.direction = direction;
            this.neighborFlat = neighborFlat;
            this.width = width;
            // Use flat position for reproducible random, but add direction for variation
            this.random = new Random(flat.getFlatId().hashCode() + direction.ordinal());
        }

        /**
         * Blend the entire neighbor side into our flat.
         * Scans the neighbor's matching side, copies data into our border,
         * and blends inward for smooth transitions.
         */
        public void blend() {
            log.debug("Blending side {} with neighbor flat {}, width={}",
                    direction, neighborFlat.getFlatId(), width);

            // Get the mirrored side on the neighbor (e.g., if we're on EAST, neighbor's WEST faces us)
            WHexGrid.SIDE mirroredSide = getMirroredSide(direction);

            int blendedCount = 0;

            // Scan all points in the neighbor flat
            for (int nz = 0; nz < neighborFlat.getSizeZ(); nz++) {
                for (int nx = 0; nx < neighborFlat.getSizeX(); nx++) {
                    // Check if this point is on the neighbor's side that faces us
                    if (!isOnNeighborSide(nx, nz, mirroredSide)) {
                        continue;
                    }

                    // Get height and material from neighbor
                    int neighborHeight = neighborFlat.getLevel(nx, nz);
                    int neighborMaterial = neighborFlat.getColumn(nx, nz);

                    // Skip empty/unset points in neighbor
                    if (neighborMaterial == WFlat.MATERIAL_NOT_SET ||
                        neighborMaterial == WFlat.MATERIAL_NOT_SET_MUTABLE) {
                        continue;
                    }

                    // Convert neighbor local coordinates to world coordinates
                    int worldX = neighborFlat.getMountX() + nx;
                    int worldZ = neighborFlat.getMountZ() + nz;

                    // Convert to our local coordinates
                    int localX = worldX - flat.getMountX();
                    int localZ = worldZ - flat.getMountZ();

                    // Check if point is within our bounds
                    if (localX < 0 || localX >= flat.getSizeX() ||
                        localZ < 0 || localZ >= flat.getSizeZ()) {
                        continue;
                    }

                    // Transfer the neighbor's data to our border and blend inward
                    blendPoint(localX, localZ, neighborHeight, neighborMaterial);
                    blendedCount++;
                }
            }

            log.debug("Blended {} points from neighbor side {}", blendedCount, direction);
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
