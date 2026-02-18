package de.mhus.nimbus.world.generator.flat.hexgrid;

import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.util.HexMathUtil;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;

/**
 * Helper class for filling edges of hex grids with data from neighbors.
 * Fills empty edge points (material==0) to avoid gaps during export.
 * Uses integer-based hex dimensions from {@link HexMathUtil#getGridWidth(int)}
 * and {@link HexMathUtil#getCornersForSide(WHexGrid.EDGE, int)}.
 */
@Slf4j
public class HexGridEdgeFiller {

    private final WFlat flat;
    private final BuilderContext context;
    private final int groundLevel;

    public HexGridEdgeFiller(WFlat flat, BuilderContext context, int groundLevel) {
        this.flat = flat;
        this.context = context;
        this.groundLevel = groundLevel;
    }

    /**
     * Fill all edges of this hex grid with neighbor data or bedrock.
     */
    public void fillAllSides(HashMap<WHexGrid.EDGE, String> sideFlats) {
        log.debug("Starting edge filling for flat: {}", flat.getFlatId());

        for (var side : WHexGrid.EDGE.values()) {
            String neighborFlatId = sideFlats.get(side);
            if (neighborFlatId != null) {
                var neighborFlat = context.getFlatService().findByWorldAndFlatId(
                        context.getWorld().getWorldId(), neighborFlatId);
                if (neighborFlat != null) {
                    fillSideWithNeighbor(side, neighborFlat);
                } else {
                    log.warn("Neighbor flat not found: {} for side {}, trying chunk data", neighborFlatId, side);
                    fillSideWithChunkOrBedrock(side);
                }
            } else {
                fillSideWithChunkOrBedrock(side);
            }
        }

        log.debug("Edge filling completed for flat: {}", flat.getFlatId());
    }

    /**
     * Fill a side with data from neighboring flat.
     * Only fills points where material==0.
     */
    private void fillSideWithNeighbor(WHexGrid.EDGE direction, WFlat neighborFlat) {
        log.trace("Filling side {} with neighbor flat {}", direction, neighborFlat.getFlatId());
        EdgeFiller edgeFiller = new EdgeFiller(flat, context, direction, neighborFlat);
        edgeFiller.fill();
    }

    /**
     * Try to fill a side with chunk data. Falls back to bedrock if no chunk data available.
     */
    private void fillSideWithChunkOrBedrock(WHexGrid.EDGE direction) {
        if (context.getChunkService() != null) {
            var worldId = de.mhus.nimbus.shared.types.WorldId.of(context.getWorld().getWorldId()).orElse(null);
            if (worldId != null) {
                WFlat chunkFlat = HexFlatUtil.createChunkBackedFlat(
                        flat, direction, context.getChunkService(), worldId, context.getWorld());
                if (chunkFlat != null) {
                    fillSideWithNeighbor(direction, chunkFlat);
                    return;
                }
            }
        }
        fillSideWithBedrock(direction);
    }

    /**
     * Fill a side with bedrock at ground level.
     * Used when there's no neighboring grid and no chunk data.
     */
    private void fillSideWithBedrock(WHexGrid.EDGE direction) {
        log.trace("Filling side {} with bedrock", direction);

        int hexGridSize = context.getHexGridSize();

        int[][] sideCorners = HexFlatUtil.getHexSideCornersLocal(direction, flat.getSizeX(), flat.getSizeZ(), hexGridSize);
        int x1 = sideCorners[0][0];
        int z1 = sideCorners[0][1];
        int x2 = sideCorners[1][0];
        int z2 = sideCorners[1][1];

        // Walk along the edge line
        int dx = x2 - x1;
        int dz = z2 - z1;
        double edgeLength = Math.sqrt((double) dx * dx + (double) dz * dz);
        int steps = (int) Math.ceil(edgeLength);

        if (steps == 0) {
            log.warn("Edge length is 0 for side {}", direction);
            return;
        }

        int filledCount = 0;

        for (int step = 0; step <= steps; step++) {
            double t = step / (double) steps;
            int xi = (int) Math.round(x1 + t * dx);
            int zi = (int) Math.round(z1 + t * dz);

            if (xi < 0 || xi >= flat.getSizeX() || zi < 0 || zi >= flat.getSizeZ()) {
                continue;
            }

            int currentMaterial = flat.getColumn(xi, zi);
            if (currentMaterial == WFlat.MATERIAL_NOT_SET) {
                flat.setLevel(xi, zi, groundLevel);
                filledCount++;
            }
        }

        log.debug("Filled {} points with bedrock on side {}", filledCount, direction);
    }

    /**
     * Helper class for filling a complete side with neighbor data.
     */
    private static class EdgeFiller {
        private final WFlat flat;
        private final BuilderContext context;
        private final WHexGrid.EDGE direction;
        private final WFlat neighborFlat;

        public EdgeFiller(WFlat flat, BuilderContext context, WHexGrid.EDGE direction,
                          WFlat neighborFlat) {
            this.flat = flat;
            this.context = context;
            this.direction = direction;
            this.neighborFlat = neighborFlat;
        }

        /**
         * Fill the edge with data from the neighbor.
         */
        public void fill() {
            log.debug("Filling side {} with neighbor flat {}", direction, neighborFlat.getFlatId());
            int hexGridSize = context.getHexGridSize();
            int gridWidth = HexMathUtil.getGridWidth(hexGridSize);
            int gapX = (flat.getSizeX() - gridWidth) / 2;
            int gapZ = (flat.getSizeZ() - hexGridSize) / 2;

            int[] area = getAreaForSide(direction);
            int[] neighborArea = getAreaForSide(direction.getOpposite());

            int startGapX = direction == WHexGrid.EDGE.EAST ? gapX : 0;
            int endGapX = direction == WHexGrid.EDGE.EAST ? gapX : 0;

            int filledCount = 0;
            for (int z = area[1] - startGapX; z <= area[3] + endGapX; z++) {
                for (int x = area[0]; x <= area[2]; x++) {
                    var neighborX = neighborArea[0] + (x - area[0]);
                    var neighborZ = neighborArea[1] + (z - area[1]);

                    // Offset to map from our border area to the corresponding position
                    // in the neighbor flat. With half-open hex boundary, gaps are symmetric
                    // (gapX on both EAST and WEST sides), so no -1 correction needed.
                    switch (direction) {
                        case NORTH_EAST:
                            neighborX += gapX;
                            neighborZ += gapZ;
                            break;
                        case EAST:
                            neighborX += gapX;
                            break;
                        case SOUTH_EAST:
                            neighborX += gapX;
                            neighborZ -= gapZ;
                            break;
                        case SOUTH_WEST:
                            neighborX -= gapX;
                            neighborZ -= gapZ;
                            break;
                        case WEST:
                            neighborX -= gapX;
                            break;
                        case NORTH_WEST:
                            neighborX -= gapX;
                            neighborZ += gapZ;
                            break;
                    }

                    if (neighborX < 0 || neighborX >= neighborFlat.getSizeX() ||
                        neighborZ < 0 || neighborZ >= neighborFlat.getSizeZ()) {
                        continue;
                    }
                    int currentMaterial = flat.getColumnRobust(x, z);
                    if (currentMaterial == WFlat.MATERIAL_NOT_SET || currentMaterial == WFlat.MATERIAL_NOT_SET_MUTABLE) {
                        var neighborLevel = neighborFlat.getLevelRobust(neighborX, neighborZ);
                        if (neighborLevel > 0) {
                            flat.setLevel(x, z, neighborLevel);
                            filledCount++;
                        }
                    }
                }
            }

            log.debug("Filled {} points from neighbor on side {}", filledCount, direction);
        }

        /**
         * Get bounding box [x1, z1, x2, z2] for the border region of a hex side.
         * The area extends from the hex edge outward to the flat boundary.
         */
        private int[] getAreaForSide(WHexGrid.EDGE direction) {
            int sizeX = flat.getSizeX();
            int sizeZ = flat.getSizeZ();
            int hexGridSize = context.getHexGridSize();

            int[][] sideCorners = HexFlatUtil.getHexSideCornersLocal(direction, sizeX, sizeZ, hexGridSize);
            int cx1 = sideCorners[0][0];
            int cz1 = sideCorners[0][1];
            int cx2 = sideCorners[1][0];
            int cz2 = sideCorners[1][1];

            switch (direction) {
                case NORTH_EAST:
                    return new int[]{Math.min(cx1, cx2), Math.min(cz1, cz2), sizeX, sizeZ};
                case EAST:
                    return new int[]{Math.min(cx1, cx2), Math.min(cz1, cz2), sizeX, Math.max(cz1, cz2)};
                case SOUTH_EAST:
                    return new int[]{Math.min(cx1, cx2), 0, sizeX, Math.max(cz1, cz2)};
                case SOUTH_WEST:
                    return new int[]{0, 0, Math.max(cx1, cx2), Math.max(cz1, cz2)};
                case WEST:
                    return new int[]{0, Math.min(cz1, cz2), Math.max(cx1, cx2), Math.max(cz1, cz2)};
                case NORTH_WEST:
                    return new int[]{0, Math.min(cz1, cz2), Math.max(cx1, cx2), sizeZ};
                default:
                    return new int[]{0, 0, sizeX, sizeZ};
            }
        }
    }
}
