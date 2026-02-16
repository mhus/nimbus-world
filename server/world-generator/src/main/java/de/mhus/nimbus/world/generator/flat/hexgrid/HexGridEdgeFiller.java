package de.mhus.nimbus.world.generator.flat.hexgrid;

import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.util.HexMathUtil;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;

/**
 * Helper class for filling edges of hex grids with data from neighbors.
 * Fills empty edge points (material==0) to avoid gaps during export.
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

        // Fill each side
        for (var side : WHexGrid.EDGE.values()) {
            String neighborFlatId = sideFlats.get(side);
            if (neighborFlatId != null) {
                var neighborFlat = context.getFlatService().findByWorldAndFlatId(
                        context.getWorld().getWorldId(), neighborFlatId);
                if (neighborFlat != null) {
                    fillSideWithNeighbor(side, neighborFlat);
                } else {
                    log.warn("Neighbor flat not found: {} for side {}", neighborFlatId, side);
                    fillSideWithBedrock(side);
                }
            } else {
                // No neighbor defined, fill with bedrock
                fillSideWithBedrock(side);
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
     * Fill a side with bedrock at ground level.
     * Used when there's no neighboring grid.
     */
    private void fillSideWithBedrock(WHexGrid.EDGE direction) {
        log.trace("Filling side {} with bedrock", direction);

        // Calculate the two corners of this hex side
        int[] corner1 = getCorner1ForSide(direction);
        int[] corner2 = getCorner2ForSide(direction);

        // Get hex center
        double centerX = flat.getSizeX() / 2.0;
        double centerZ = flat.getSizeZ() / 2.0;

        // Calculate radius (approximate)
        double dist1 = Math.sqrt(Math.pow(corner1[0] - centerX, 2) + Math.pow(corner1[1] - centerZ, 2));
        double dist2 = Math.sqrt(Math.pow(corner2[0] - centerX, 2) + Math.pow(corner2[1] - centerZ, 2));
        double radius = (dist1 + dist2) / 2.0;

        // Walk along the edge line (from corner1 to corner2)
        double dx = corner2[0] - corner1[0];
        double dz = corner2[1] - corner1[1];
        double edgeLength = Math.sqrt(dx * dx + dz * dz);
        int steps = (int) Math.ceil(edgeLength);

        if (steps == 0) {
            log.warn("Edge length is 0 for side {}", direction);
            return;
        }

        int filledCount = 0;

        // Walk along the edge
        for (int step = 0; step <= steps; step++) {
            double t = steps > 0 ? step / (double) steps : 0.5;
            double x = corner1[0] * (1 - t) + corner2[0] * t;
            double z = corner1[1] * (1 - t) + corner2[1] * t;

            int xi = (int) Math.round(x);
            int zi = (int) Math.round(z);

            // Check bounds
            if (xi < 0 || xi >= flat.getSizeX() || zi < 0 || zi >= flat.getSizeZ()) {
                continue;
            }

            // Only fill if material is not set (==0)
            int currentMaterial = flat.getColumn(xi, zi);
            if (currentMaterial == WFlat.MATERIAL_NOT_SET) {
                // Only set level, not material
                flat.setLevel(xi, zi, groundLevel);
                filledCount++;
            }
        }

        log.debug("Filled {} points with bedrock on side {}", filledCount, direction);
    }

    // Pointy-top hex corner angles (North = Z+, East = X+):
    //   N=90°, NE=30°, SE=330°, S=270°, SW=210°, NW=150°
    // Each EDGE side connects two adjacent corners (clockwise):
    //   NORTH_EAST: N→NE, EAST: NE→SE, SOUTH_EAST: SE→S
    //   SOUTH_WEST: SW→S, WEST: NW→SW, NORTH_WEST: N→NW

    /**
     * Get first corner of the hex side (in local flat coordinates).
     */
    private int[] getCorner1ForSide(WHexGrid.EDGE side) {
        int sizeX = flat.getSizeX();
        double centerX = sizeX / 2.0;
        double centerZ = flat.getSizeZ() / 2.0;
        double radius = sizeX / 2.0;

        double angle;
        switch (side) {
            case NORTH_EAST: angle = Math.toRadians(90);  break; // N
            case EAST:       angle = Math.toRadians(30);  break; // NE
            case SOUTH_EAST: angle = Math.toRadians(330); break; // SE
            case SOUTH_WEST: angle = Math.toRadians(210); break; // SW
            case WEST:       angle = Math.toRadians(150); break; // NW
            case NORTH_WEST: angle = Math.toRadians(90);  break; // N
            default: return new int[]{0, 0};
        }

        int x = (int) Math.round(centerX + radius * Math.cos(angle));
        int z = (int) Math.round(centerZ + radius * Math.sin(angle));
        return new int[]{x, z};
    }

    /**
     * Get second corner of the hex side (in local flat coordinates).
     */
    private int[] getCorner2ForSide(WHexGrid.EDGE side) {
        int sizeX = flat.getSizeX();
        double centerX = sizeX / 2.0;
        double centerZ = flat.getSizeZ() / 2.0;
        double radius = sizeX / 2.0;

        double angle;
        switch (side) {
            case NORTH_EAST: angle = Math.toRadians(30);  break; // NE
            case EAST:       angle = Math.toRadians(330); break; // SE
            case SOUTH_EAST: angle = Math.toRadians(270); break; // S
            case SOUTH_WEST: angle = Math.toRadians(270); break; // S
            case WEST:       angle = Math.toRadians(210); break; // SW
            case NORTH_WEST: angle = Math.toRadians(150); break; // NW
            default: return new int[]{0, 0};
        }

        int x = (int) Math.round(centerX + radius * Math.cos(angle));
        int z = (int) Math.round(centerZ + radius * Math.sin(angle));
        return new int[]{x, z};
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
         * Algorithm:
         * 1. Calculate the two corner points of the hex side
         * 2. Walk along the edge line
         * 3. For each point on the edge:
         *    - Check if it's empty (material==0)
         *    - If empty, copy the value from the corresponding neighbor point
         */
        public void fill() {
            log.debug("Filling side {} with neighbor flat {}", direction, neighborFlat.getFlatId());
            int gapX = (flat.getSizeX() - (int)Math.floor(context.getHexGridSize()* HexMathUtil.SQRT_3/2.0) ) / 2;
            int gapZ = (flat.getSizeZ() - context.getHexGridSize()) / 2;

            // Calculate the two corners of this hex side
            int[] area = getAreaForSide(direction);
            int[] neighborArea = getAreaForSide(direction.getOpposite());

            int startGapX = direction == WHexGrid.EDGE.EAST ? gapX : 0;
            int endGapX = direction == WHexGrid.EDGE.EAST ? gapX : 0;

            int filledCount = 0;
            for (int z = area[1]-startGapX; z <= area[3]+endGapX; z++) {
                for (int x = area[0]; x <= area[2]; x++) {
                    // Direct mapping (no mirroring)
                    var neighborX = neighborArea[0] + (x - area[0]);
                    var neighborZ = neighborArea[1] + (z - area[1]);

                    // Apply offset to compensate for 15-pixel border gap
                    // North = +Z, South = -Z in local flat coordinates
                    switch (direction) {
                        case NORTH_EAST:
                            neighborX += gapX;
                            neighborZ += gapZ;
                            break;
                        case EAST:
                            neighborX += gapX - 1;
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
                            neighborX -= gapX - 1;
                            break;
                        case NORTH_WEST:
                            neighborX -= gapX;
                            neighborZ += gapZ;
                            break;
                    }

                    // check neighbor bounds
                    if (neighborX < 0 || neighborX >= neighborFlat.getSizeX() ||
                        neighborZ < 0 || neighborZ >= neighborFlat.getSizeZ()) {
                        continue;
                    }
                    // Only fill if material is not set (==0)
                    int currentMaterial = flat.getColumnRobust(x, z);
                    if (currentMaterial == WFlat.MATERIAL_NOT_SET || currentMaterial == WFlat.MATERIAL_NOT_SET_MUTABLE) {
                        // flat.setLevel(x, z, 200); // Set some level to mark as filled
                        // Get corresponding point from neighbor
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
         * North = +Z (high localZ), South = -Z (low localZ).
         */
        private int[] getAreaForSide(WHexGrid.EDGE direction) {
            int sizeX = flat.getSizeX();
            int sizeZ = flat.getSizeZ();
            int[] corner1 = getCorner1ForSide(direction);
            int[] corner2 = getCorner2ForSide(direction);

            switch (direction) {
                case NORTH_EAST:
                    // Upper-right: from corners to (sizeX, sizeZ)
                    return new int[]{Math.min(corner1[0], corner2[0]), Math.min(corner1[1], corner2[1]), sizeX, sizeZ};
                case EAST:
                    // Right side: between NE and SE corners to sizeX
                    return new int[]{Math.min(corner1[0], corner2[0]), Math.min(corner1[1], corner2[1]), sizeX, Math.max(corner1[1], corner2[1])};
                case SOUTH_EAST:
                    // Lower-right: from (minCornerX, 0) to (sizeX, maxCornerZ)
                    return new int[]{Math.min(corner1[0], corner2[0]), 0, sizeX, Math.max(corner1[1], corner2[1])};
                case SOUTH_WEST:
                    // Lower-left: from (0, 0) to corners
                    return new int[]{0, 0, Math.max(corner1[0], corner2[0]), Math.max(corner1[1], corner2[1])};
                case WEST:
                    // Left side: between NW and SW corners from 0
                    return new int[]{0, Math.min(corner1[1], corner2[1]), Math.max(corner1[0], corner2[0]), Math.max(corner1[1], corner2[1])};
                case NORTH_WEST:
                    // Upper-left: from (0, minCornerZ) to (maxCornerX, sizeZ)
                    return new int[]{0, Math.min(corner1[1], corner2[1]), Math.max(corner1[0], corner2[0]), sizeZ};
                default:
                    return new int[]{0, 0, sizeX, sizeZ};
            }
        }

        // Pointy-top hex corner angles (North = Z+, East = X+):
        //   N=90°, NE=30°, SE=330°, S=270°, SW=210°, NW=150°

        private int[] getCorner1ForSide(WHexGrid.EDGE side) {
            double centerX = flat.getSizeX() / 2.0;
            double centerZ = flat.getSizeZ() / 2.0;
            double radius = context.getWorld().getPublicData().getHexGridSize() / 2.0;

            double angle;
            switch (side) {
                case NORTH_EAST: angle = Math.toRadians(90);  break; // N
                case EAST:       angle = Math.toRadians(30);  break; // NE
                case SOUTH_EAST: angle = Math.toRadians(330); break; // SE
                case SOUTH_WEST: angle = Math.toRadians(210); break; // SW
                case WEST:       angle = Math.toRadians(150); break; // NW
                case NORTH_WEST: angle = Math.toRadians(90);  break; // N
                default: return new int[]{0, 0};
            }

            int x = (int) Math.round(centerX + radius * Math.cos(angle));
            int z = (int) Math.round(centerZ + radius * Math.sin(angle));
            return new int[]{x, z};
        }

        private int[] getCorner2ForSide(WHexGrid.EDGE side) {
            double centerX = flat.getSizeX() / 2.0;
            double centerZ = flat.getSizeZ() / 2.0;
            double radius = context.getWorld().getPublicData().getHexGridSize() / 2.0;

            double angle;
            switch (side) {
                case NORTH_EAST: angle = Math.toRadians(30);  break; // NE
                case EAST:       angle = Math.toRadians(330); break; // SE
                case SOUTH_EAST: angle = Math.toRadians(270); break; // S
                case SOUTH_WEST: angle = Math.toRadians(270); break; // S
                case WEST:       angle = Math.toRadians(210); break; // SW
                case NORTH_WEST: angle = Math.toRadians(150); break; // NW
                default: return new int[]{0, 0};
            }

            int x = (int) Math.round(centerX + radius * Math.cos(angle));
            int z = (int) Math.round(centerZ + radius * Math.sin(angle));
            return new int[]{x, z};
        }
    }
}
