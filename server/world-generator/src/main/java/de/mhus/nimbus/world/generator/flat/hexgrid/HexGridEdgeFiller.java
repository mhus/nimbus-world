package de.mhus.nimbus.world.generator.flat.hexgrid;

import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;

/**
 * Helper class for filling edges of hex grids with data from neighbors.
 * Fills empty edge points (material==0) to avoid gaps during export.
 */
@Slf4j
public class HexGridEdgeFiller {

    private static final int BEDROCK_MATERIAL = 6;

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

    /**
     * Get first corner of the hex side (in local flat coordinates).
     */
    private int[] getCorner1ForSide(WHexGrid.EDGE side) {
        int sizeX = flat.getSizeX();
        int sizeZ = flat.getSizeZ();
        double centerX = sizeX / 2.0;
        double centerZ = sizeZ / 2.0;
        double radius = sizeX / 2.0;

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
     */
    private int[] getCorner2ForSide(WHexGrid.EDGE side) {
        int sizeX = flat.getSizeX();
        int sizeZ = flat.getSizeZ();
        double centerX = sizeX / 2.0;
        double centerZ = sizeZ / 2.0;
        double radius = sizeX / 2.0;

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

            // Calculate the two corners of this hex side
            int[] corner1 = getCorner1ForSide(direction);
            int[] corner2 = getCorner2ForSide(direction);

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
                    // Get corresponding point from neighbor
                    int[] neighborPoint = getCorrespondingNeighborPoint(xi, zi);
                    if (neighborPoint != null) {
                        int neighborMaterial = neighborFlat.getColumn(neighborPoint[0], neighborPoint[1]);
                        if (neighborMaterial != WFlat.MATERIAL_NOT_SET &&
                            neighborMaterial != WFlat.MATERIAL_NOT_SET_MUTABLE) {
                            // Only copy level from neighbor, not material
                            int neighborLevel = neighborFlat.getLevel(neighborPoint[0], neighborPoint[1]);
                            flat.setLevel(xi, zi, neighborLevel);
                            filledCount++;
                        }
                    }
                }
            }

            log.debug("Filled {} points from neighbor on side {}", filledCount, direction);
        }

        /**
         * Get corresponding point in neighbor flat for a point in our flat.
         * Converts local coordinates to world coordinates, then to neighbor coordinates.
         * Returns null if the point is outside neighbor bounds.
         */
        private int[] getCorrespondingNeighborPoint(int localX, int localZ) {
            // Convert to world coordinates
            int worldX = flat.getMountX() + localX;
            int worldZ = flat.getMountZ() + localZ;

            // Convert to neighbor coordinates
            int neighborX = worldX - neighborFlat.getMountX();
            int neighborZ = worldZ - neighborFlat.getMountZ();

            // Check bounds in neighbor flat
            if (neighborX < 0 || neighborX >= neighborFlat.getSizeX() ||
                neighborZ < 0 || neighborZ >= neighborFlat.getSizeZ()) {
                return null;
            }

            return new int[]{neighborX, neighborZ};
        }

        /**
         * Get first corner of the hex side (in local flat coordinates).
         */
        private int[] getCorner1ForSide(WHexGrid.EDGE side) {
            int sizeX = flat.getSizeX();
            int sizeZ = flat.getSizeZ();
            double centerX = sizeX / 2.0;
            double centerZ = sizeZ / 2.0;
            double radius = sizeX / 2.0;

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
         */
        private int[] getCorner2ForSide(WHexGrid.EDGE side) {
            int sizeX = flat.getSizeX();
            int sizeZ = flat.getSizeZ();
            double centerX = sizeX / 2.0;
            double centerZ = sizeZ / 2.0;
            double radius = sizeX / 2.0;

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
    }
}
