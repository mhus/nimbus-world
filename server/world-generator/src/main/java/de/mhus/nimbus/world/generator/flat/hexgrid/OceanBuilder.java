package de.mhus.nimbus.world.generator.flat.hexgrid;

import de.mhus.nimbus.world.generator.flat.FlatMaterialService;
import de.mhus.nimbus.world.generator.flat.HillyTerrainManipulator;
import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Ocean scenario builder.
 * Creates deep ocean with hilly ocean floor using HillyTerrainManipulator.
 */
@Slf4j
public class OceanBuilder extends HexGridBuilder {

    @Override
    public void buildFlat() {
        WFlat flat = context.getFlat();

        log.info("Building ocean scenario for flat: {}",
                flat.getFlatId());

        int oceanLevel = flat.getOceanLevel();

        // Use getHexGridLevel() as baseHeight (PARAM_BASE_HEIGHT in HillyTerrainManipulator)
        // Use getLandOffset() as hillHeight (PARAM_HILL_HEIGHT in HillyTerrainManipulator)
        int hillHeight = getLandOffset();
        int baseHeight = Math.min(getHexGridLevel(), oceanLevel - hillHeight + 2); // Ensure ocean floor is below ocean level

        long seed = parseLongParameter(parameters, "seed", System.currentTimeMillis());

        log.debug("Ocean floor generation: baseHeight={}, hillHeight={}, oceanLevel={}, seed={}",
                baseHeight, hillHeight, oceanLevel, seed);

        // Build parameters for HillyTerrainManipulator
        Map<String, String> hillyParams = new HashMap<>();
        hillyParams.put(HillyTerrainManipulator.PARAM_BASE_HEIGHT, String.valueOf(baseHeight));
        hillyParams.put(HillyTerrainManipulator.PARAM_HILL_HEIGHT, String.valueOf(hillHeight));
        hillyParams.put(HillyTerrainManipulator.PARAM_SEED, String.valueOf(seed));

        // Use HillyTerrainManipulator to generate ocean floor terrain
        context.getManipulatorService().executeManipulator(
                HillyTerrainManipulator.NAME,
                flat,
                0, 0,
                flat.getSizeX(), flat.getSizeZ(),
                hillyParams
        );

        // Set all to sand material for ocean floor
        for (int z = 0; z < flat.getSizeZ(); z++) {
            for (int x = 0; x < flat.getSizeX(); x++) {
                flat.setColumn(x, z, FlatMaterialService.SAND);
            }
        }

        // Blend edges with default noise where neighbors don't exist
        blendEdgesWithDefaultNoise();

        log.info("Ocean scenario completed: baseHeight={}, hillHeight={}, oceanLevel={}",
                baseHeight, hillHeight, oceanLevel);
    }

    @Override
    protected int getDefaultLandOffset() {
        return 7;  // OCEAN: medium variation for ocean floor
    }

    @Override
    protected int getDefaultLandLevel() {
        return -10;  // OCEAN: below ocean level
    }

    private long parseLongParameter(Map<String, String> parameters, String name, long defaultValue) {
        if (parameters == null || !parameters.containsKey(name)) {
            return defaultValue;
        }
        try {
            return Long.parseLong(parameters.get(name));
        } catch (NumberFormatException e) {
            log.warn("Invalid long parameter '{}': {}, using default: {}", name, parameters.get(name), defaultValue);
            return defaultValue;
        }
    }

    public int getLandSideLevel(WHexGrid.SIDE side) {
        return getLandCenterLevel();
    }

    /**
     * Blend edges with default noise for edges where no neighbor exists.
     * Uses a 30-block border for smooth interpolation.
     */
    private void blendEdgesWithDefaultNoise() {
        WFlat flat = context.getFlat();
        int groundLevel = context.getWorld().getGroundLevel();

        log.debug("Blending edges with default noise for flat: {}", flat.getFlatId());

        // Check all 6 hex edges for missing neighbors
        for (WHexGrid.SIDE side : WHexGrid.SIDE.values()) {
            WHexGrid neighbor = context.getNeighborGrids().get(side);

            // Only blend if neighbor doesn't exist
            if (neighbor == null) {
                log.debug("Blending edge {} with default noise (no neighbor found)", side);
                blendEdgeWithNoise(side, groundLevel);
            }
        }
    }

    /**
     * Blend a single edge with default noise.
     * Creates smooth transition from ocean terrain to noise-based terrain over 30 blocks.
     *
     * @param side The hex edge to blend
     * @param groundLevel The ground level for noise calculation
     */
    private void blendEdgeWithNoise(WHexGrid.SIDE side, int groundLevel) {
        WFlat flat = context.getFlat();
        int blendDepth = 30;  // Depth of blending zone in blocks

        // Calculate edge coordinates based on hex side
        EdgeCoordinates edgeCoords = getEdgeCoordinates(side, flat.getSizeX(), flat.getSizeZ());

        // Iterate over the edge and blend
        for (int step = 0; step < edgeCoords.length; step++) {
            int[] pos = edgeCoords.getPosition(step);
            int localX = pos[0];
            int localZ = pos[1];

            // Calculate world coordinates for noise
            int worldX = flat.getMountX() + localX;
            int worldZ = flat.getMountZ() + localZ;

            // Get noise-based height from WChunkService
            int noiseHeight = context.getChunkService().getNoiseHeight(worldX, worldZ, groundLevel);

            // Blend from inner edge (distance = blendDepth) to outer edge (distance = 0)
            for (int distance = 0; distance < blendDepth; distance++) {
                int[] innerPos = edgeCoords.getPositionAtDistance(step, distance);
                int innerX = innerPos[0];
                int innerZ = innerPos[1];

                // Check bounds
                if (innerX < 0 || innerX >= flat.getSizeX() || innerZ < 0 || innerZ >= flat.getSizeZ()) {
                    continue;
                }

                // Get current height at this position
                int currentHeight = flat.getLevel(innerX, innerZ);

                // Calculate interpolation factor (0.0 at edge, 1.0 at blendDepth distance)
                float t = (float) distance / blendDepth;

                // Interpolate between noise height (outer) and current height (inner)
                int blendedHeight = (int) Math.round(noiseHeight * (1 - t) + currentHeight * t);

                // Set blended height
                flat.setLevel(innerX, innerZ, blendedHeight);
            }
        }

        log.trace("Blended edge {} with noise over {} blocks depth", side, blendDepth);
    }

    /**
     * Helper class to calculate edge coordinates for a hex side.
     */
    private EdgeCoordinates getEdgeCoordinates(WHexGrid.SIDE side, int sizeX, int sizeZ) {
        if (side == WHexGrid.SIDE.NORTH_EAST) {
            return new EdgeCoordinates(sizeX / 2, 0, sizeX - 1, sizeZ / 4, 1, 1);
        } else if (side == WHexGrid.SIDE.EAST) {
            return new EdgeCoordinates(sizeX - 1, sizeZ / 4, sizeX - 1, 3 * sizeZ / 4, 0, 1);
        } else if (side == WHexGrid.SIDE.SOUTH_EAST) {
            return new EdgeCoordinates(sizeX - 1, 3 * sizeZ / 4, sizeX / 2, sizeZ - 1, -1, 1);
        } else if (side == WHexGrid.SIDE.SOUTH_WEST) {
            return new EdgeCoordinates(sizeX / 2, sizeZ - 1, 0, 3 * sizeZ / 4, -1, -1);
        } else if (side == WHexGrid.SIDE.WEST) {
            return new EdgeCoordinates(0, 3 * sizeZ / 4, 0, sizeZ / 4, 0, -1);
        } else if (side == WHexGrid.SIDE.NORTH_WEST) {
            return new EdgeCoordinates(0, sizeZ / 4, sizeX / 2, 0, 1, -1);
        } else {
            throw new IllegalArgumentException("Unknown side: " + side);
        }
    }

    /**
     * Helper class for edge coordinate calculations.
     */
    private static class EdgeCoordinates {
        private final int startX, startZ, endX, endZ, dirX, dirZ;
        public final int length;

        public EdgeCoordinates(int startX, int startZ, int endX, int endZ, int dirX, int dirZ) {
            this.startX = startX;
            this.startZ = startZ;
            this.endX = endX;
            this.endZ = endZ;
            this.dirX = dirX;
            this.dirZ = dirZ;
            this.length = Math.max(Math.abs(endX - startX), Math.abs(endZ - startZ)) + 1;
        }

        public int[] getPosition(int step) {
            float t = (float) step / (length - 1);
            int x = Math.round(startX + (endX - startX) * t);
            int z = Math.round(startZ + (endZ - startZ) * t);
            return new int[]{x, z};
        }

        public int[] getPositionAtDistance(int step, int distance) {
            int[] edgePos = getPosition(step);
            // Move inward perpendicular to the edge
            // Perpendicular direction is (-dirZ, dirX) for moving inward
            int perpX = -dirZ;
            int perpZ = dirX;
            return new int[]{edgePos[0] + perpX * distance, edgePos[1] + perpZ * distance};
        }
    }

}
