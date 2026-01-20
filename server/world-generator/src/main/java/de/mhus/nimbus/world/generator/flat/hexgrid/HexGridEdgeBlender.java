package de.mhus.nimbus.world.generator.flat.hexgrid;

import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Helper class for blending edges between neighboring hex grids.
 * Ensures smooth transitions at hex boundaries by calculating corner heights
 * and interpolating along edges.
 */
@Slf4j
public class HexGridEdgeBlender {

    private static final int BLEND_DEPTH = 10;  // Depth of blending into the grid (in pixels)

    private final WFlat flat;
    private final BuilderContext context;

    public HexGridEdgeBlender(WFlat flat, BuilderContext context) {
        this.flat = flat;
        this.context = context;
    }

    /**
     * Blend all edges of this hex grid with its neighbors.
     */
    public void blendAllEdges() {
        log.debug("Starting edge blending for flat: {}", flat.getFlatId());

        // Calculate corner heights first (needed for edge blending)
        Map<Corner, Integer> cornerHeights = calculateCornerHeights();

        // Blend each edge
        for (WHexGrid.NEIGHBOR direction : WHexGrid.NEIGHBOR.values()) {
            WHexGrid neighbor = context.getNeighborGrids().get(direction);
            if (neighbor != null) {
                blendEdge(direction, cornerHeights);
            }
        }

        log.info("Edge blending completed for flat: {}", flat.getFlatId());
    }

    /**
     * Calculate the average height for each corner from neighboring grids.
     */
    private Map<Corner, Integer> calculateCornerHeights() {
        Map<Corner, Integer> cornerHeights = new java.util.EnumMap<>(Corner.class);

        for (Corner corner : Corner.values()) {
            int height = calculateCornerHeight(corner);
            cornerHeights.put(corner, height);
            log.trace("Corner {} height: {}", corner, height);
        }

        return cornerHeights;
    }

    /**
     * Calculate height for a specific corner by averaging neighbor values.
     */
    private int calculateCornerHeight(Corner corner) {
        // Get neighbors that share this corner
        WHexGrid.NEIGHBOR[] adjacentNeighbors = corner.getAdjacentNeighbors();

        int sum = 0;
        int count = 0;

        // Add own corner height
        int ownHeight = getOwnCornerHeight(corner);
        sum += ownHeight;
        count++;

        // Add neighbor corner heights
        for (WHexGrid.NEIGHBOR direction : adjacentNeighbors) {
            WHexGrid neighbor = context.getNeighborGrids().get(direction);
            if (neighbor != null) {
                int neighborHeight = getNeighborCornerHeight(neighbor, corner, direction);
                sum += neighborHeight;
                count++;
            }
        }

        return count > 0 ? sum / count : ownHeight;
    }

    /**
     * Get height at a corner of the current grid.
     */
    private int getOwnCornerHeight(Corner corner) {
        int[] coords = corner.getLocalCoordinates(flat.getSizeX(), flat.getSizeZ());
        int x = coords[0];
        int z = coords[1];

        // Clamp to valid range
        x = Math.max(0, Math.min(flat.getSizeX() - 1, x));
        z = Math.max(0, Math.min(flat.getSizeZ() - 1, z));

        return flat.getLevel(x, z);
    }

    /**
     * Get height at corresponding corner of a neighbor grid.
     */
    private int getNeighborCornerHeight(WHexGrid neighbor, Corner corner, WHexGrid.NEIGHBOR direction) {
        // Get the builder for the neighbor to access its flat
        return context.getBuilderFor(direction)
                .map(builder -> {
                    WFlat neighborFlat = builder.getContext().getFlat();
                    Corner neighborCorner = corner.getMirroredCorner(direction);
                    int[] coords = neighborCorner.getLocalCoordinates(neighborFlat.getSizeX(), neighborFlat.getSizeZ());
                    int x = Math.max(0, Math.min(neighborFlat.getSizeX() - 1, coords[0]));
                    int z = Math.max(0, Math.min(neighborFlat.getSizeZ() - 1, coords[1]));
                    return neighborFlat.getLevel(x, z);
                })
                .orElse(getOwnCornerHeight(corner));
    }

    /**
     * Blend a single edge with its neighbor.
     */
    private void blendEdge(WHexGrid.NEIGHBOR direction, Map<Corner, Integer> cornerHeights) {
        log.trace("Blending edge: {}", direction);

        EdgeBlender edgeBlender = new EdgeBlender(flat, context, direction, cornerHeights);
        edgeBlender.blend();
    }

    /**
     * Enum representing the 6 corners of a hex grid.
     */
    public enum Corner {
        TOP,
        TOP_RIGHT,
        BOTTOM_RIGHT,
        BOTTOM,
        BOTTOM_LEFT,
        TOP_LEFT;

        /**
         * Get local coordinates for this corner in the flat.
         */
        public int[] getLocalCoordinates(int sizeX, int sizeZ) {
            switch (this) {
                case TOP:
                    return new int[]{sizeX / 2, 0};
                case TOP_RIGHT:
                    return new int[]{sizeX - 1, 0};
                case BOTTOM_RIGHT:
                    return new int[]{sizeX - 1, sizeZ - 1};
                case BOTTOM:
                    return new int[]{sizeX / 2, sizeZ - 1};
                case BOTTOM_LEFT:
                    return new int[]{0, sizeZ - 1};
                case TOP_LEFT:
                    return new int[]{0, 0};
                default:
                    return new int[]{0, 0};
            }
        }

        /**
         * Get neighbors that share this corner.
         */
        public WHexGrid.NEIGHBOR[] getAdjacentNeighbors() {
            switch (this) {
                case TOP:
                    return new WHexGrid.NEIGHBOR[]{WHexGrid.NEIGHBOR.TOP_LEFT, WHexGrid.NEIGHBOR.TOP_RIGHT};
                case TOP_RIGHT:
                    return new WHexGrid.NEIGHBOR[]{WHexGrid.NEIGHBOR.TOP_RIGHT, WHexGrid.NEIGHBOR.RIGHT};
                case BOTTOM_RIGHT:
                    return new WHexGrid.NEIGHBOR[]{WHexGrid.NEIGHBOR.RIGHT, WHexGrid.NEIGHBOR.BOTTOM_RIGHT};
                case BOTTOM:
                    return new WHexGrid.NEIGHBOR[]{WHexGrid.NEIGHBOR.BOTTOM_RIGHT, WHexGrid.NEIGHBOR.BOTTOM_LEFT};
                case BOTTOM_LEFT:
                    return new WHexGrid.NEIGHBOR[]{WHexGrid.NEIGHBOR.BOTTOM_LEFT, WHexGrid.NEIGHBOR.LEFT};
                case TOP_LEFT:
                    return new WHexGrid.NEIGHBOR[]{WHexGrid.NEIGHBOR.LEFT, WHexGrid.NEIGHBOR.TOP_LEFT};
                default:
                    return new WHexGrid.NEIGHBOR[]{};
            }
        }

        /**
         * Get the mirrored corner on the neighbor grid.
         */
        public Corner getMirroredCorner(WHexGrid.NEIGHBOR direction) {
            // This is simplified - would need proper hex geometry mapping
            switch (direction) {
                case TOP_LEFT:
                case TOP_RIGHT:
                    return this == TOP ? BOTTOM : this;
                case BOTTOM_LEFT:
                case BOTTOM_RIGHT:
                    return this == BOTTOM ? TOP : this;
                case LEFT:
                    return this == TOP_LEFT ? TOP_RIGHT : (this == BOTTOM_LEFT ? BOTTOM_RIGHT : this);
                case RIGHT:
                    return this == TOP_RIGHT ? TOP_LEFT : (this == BOTTOM_RIGHT ? BOTTOM_LEFT : this);
                default:
                    return this;
            }
        }
    }

    /**
     * Helper class for blending a single edge.
     * Transforms the edge into a straight line for easier processing.
     */
    private static class EdgeBlender {
        private final WFlat flat;
        private final BuilderContext context;
        private final WHexGrid.NEIGHBOR direction;
        private final Map<Corner, Integer> cornerHeights;

        public EdgeBlender(WFlat flat, BuilderContext context, WHexGrid.NEIGHBOR direction,
                          Map<Corner, Integer> cornerHeights) {
            this.flat = flat;
            this.context = context;
            this.direction = direction;
            this.cornerHeights = cornerHeights;
        }

        /**
         * Blend this edge with the neighbor.
         */
        public void blend() {
            // Get edge endpoints (corners)
            Corner[] edgeCorners = getEdgeCorners(direction);
            Corner cornerA = edgeCorners[0];
            Corner cornerB = edgeCorners[1];

            // Get corner heights
            int heightA = cornerHeights.get(cornerA);
            int heightB = cornerHeights.get(cornerB);

            // Get neighbor height along edge
            int neighborHeight = getNeighborEdgeHeight(direction);

            // Calculate edge length
            int[] coordsA = cornerA.getLocalCoordinates(flat.getSizeX(), flat.getSizeZ());
            int[] coordsB = cornerB.getLocalCoordinates(flat.getSizeX(), flat.getSizeZ());
            int edgeLength = calculateEdgeLength(coordsA, coordsB);

            log.trace("Edge {}: cornerA={}, cornerB={}, heightA={}, heightB={}, neighborHeight={}, length={}",
                    direction, cornerA, cornerB, heightA, heightB, neighborHeight, edgeLength);

            // Blend along the edge
            blendAlongEdge(coordsA, coordsB, heightA, heightB, neighborHeight, edgeLength);
        }

        /**
         * Get the two corners that define this edge.
         */
        private Corner[] getEdgeCorners(WHexGrid.NEIGHBOR direction) {
            switch (direction) {
                case TOP_LEFT:
                    return new Corner[]{Corner.TOP_LEFT, Corner.TOP};
                case TOP_RIGHT:
                    return new Corner[]{Corner.TOP, Corner.TOP_RIGHT};
                case RIGHT:
                    return new Corner[]{Corner.TOP_RIGHT, Corner.BOTTOM_RIGHT};
                case BOTTOM_RIGHT:
                    return new Corner[]{Corner.BOTTOM_RIGHT, Corner.BOTTOM};
                case BOTTOM_LEFT:
                    return new Corner[]{Corner.BOTTOM, Corner.BOTTOM_LEFT};
                case LEFT:
                    return new Corner[]{Corner.BOTTOM_LEFT, Corner.TOP_LEFT};
                default:
                    return new Corner[]{Corner.TOP, Corner.BOTTOM};
            }
        }

        /**
         * Get average height along the neighbor's corresponding edge.
         */
        private int getNeighborEdgeHeight(WHexGrid.NEIGHBOR direction) {
            return context.getBuilderFor(direction)
                    .map(builder -> {
                        WFlat neighborFlat = builder.getContext().getFlat();
                        // Sample a few points along the neighbor edge
                        int sum = 0;
                        int count = 0;

                        // Get neighbor edge corners
                        Corner[] neighborEdgeCorners = getMirroredEdgeCorners(direction);
                        int[] coordsA = neighborEdgeCorners[0].getLocalCoordinates(neighborFlat.getSizeX(), neighborFlat.getSizeZ());
                        int[] coordsB = neighborEdgeCorners[1].getLocalCoordinates(neighborFlat.getSizeX(), neighborFlat.getSizeZ());

                        // Sample 5 points along the edge
                        for (int i = 0; i <= 4; i++) {
                            double t = i / 4.0;
                            int x = (int) (coordsA[0] + t * (coordsB[0] - coordsA[0]));
                            int z = (int) (coordsA[1] + t * (coordsB[1] - coordsA[1]));
                            x = Math.max(0, Math.min(neighborFlat.getSizeX() - 1, x));
                            z = Math.max(0, Math.min(neighborFlat.getSizeZ() - 1, z));
                            sum += neighborFlat.getLevel(x, z);
                            count++;
                        }

                        return count > 0 ? sum / count : 64;
                    })
                    .orElse(64);  // Default height if no neighbor
        }

        /**
         * Get the mirrored edge corners on the neighbor.
         */
        private Corner[] getMirroredEdgeCorners(WHexGrid.NEIGHBOR direction) {
            Corner[] ownCorners = getEdgeCorners(direction);
            return new Corner[]{
                    ownCorners[0].getMirroredCorner(direction),
                    ownCorners[1].getMirroredCorner(direction)
            };
        }

        /**
         * Calculate edge length.
         */
        private int calculateEdgeLength(int[] coordsA, int[] coordsB) {
            int dx = coordsB[0] - coordsA[0];
            int dz = coordsB[1] - coordsA[1];
            return (int) Math.ceil(Math.sqrt(dx * dx + dz * dz));
        }

        /**
         * Blend heights along the edge and BLEND_DEPTH pixels inward.
         */
        private void blendAlongEdge(int[] coordsA, int[] coordsB, int heightA, int heightB,
                                   int neighborHeight, int edgeLength) {
            if (edgeLength == 0) return;

            // Iterate along the edge
            for (int step = 0; step <= edgeLength; step++) {
                double t = edgeLength > 0 ? (double) step / edgeLength : 0.0;

                // Calculate position along edge
                int edgeX = (int) (coordsA[0] + t * (coordsB[0] - coordsA[0]));
                int edgeZ = (int) (coordsA[1] + t * (coordsB[1] - coordsA[1]));

                // Calculate target height at this point along edge (interpolate between corners)
                int edgeTargetHeight = (int) (heightA + t * (heightB - heightA));

                // Blend inward from edge
                blendInward(edgeX, edgeZ, edgeTargetHeight, neighborHeight);
            }
        }

        /**
         * Blend from edge point inward for BLEND_DEPTH pixels.
         */
        private void blendInward(int edgeX, int edgeZ, int edgeHeight, int neighborHeight) {
            // Calculate inward direction (perpendicular to edge, towards center)
            int centerX = flat.getSizeX() / 2;
            int centerZ = flat.getSizeZ() / 2;

            double dx = centerX - edgeX;
            double dz = centerZ - edgeZ;
            double length = Math.sqrt(dx * dx + dz * dz);

            if (length == 0) return;

            // Normalize direction
            dx /= length;
            dz /= length;

            // Blend inward
            for (int depth = 0; depth < BLEND_DEPTH; depth++) {
                int x = edgeX + (int) (dx * depth);
                int z = edgeZ + (int) (dz * depth);

                // Check bounds
                if (x < 0 || x >= flat.getSizeX() || z < 0 || z >= flat.getSizeZ()) {
                    continue;
                }

                // Calculate blend factor (1.0 at edge, 0.0 at BLEND_DEPTH)
                double blendFactor = 1.0 - ((double) depth / BLEND_DEPTH);

                // Get current height
                int currentHeight = flat.getLevel(x, z);

                // Calculate target height (blend between neighbor and edge target)
                int targetHeight = (int) (edgeHeight * blendFactor + currentHeight * (1.0 - blendFactor));

                // Set blended height
                flat.setLevel(x, z, Math.max(0, Math.min(255, targetHeight)));
            }
        }
    }
}
