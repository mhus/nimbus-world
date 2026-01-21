package de.mhus.nimbus.world.generator.flat.hexgrid;

import de.mhus.nimbus.world.generator.flat.FlatMaterialService;
import de.mhus.nimbus.world.generator.flat.HillyTerrainManipulator;
import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Slf4j
public class CoastBuilder extends HexGridBuilder {

    // Coast transition parameters
    private static final int COAST_WIDTH = 15;  // Width of coastal transition zone
    private static final int SAND_WIDTH = 5;    // Width of sand beach

    @Override
    public void buildFlat() {
        WFlat flat = context.getFlat();

        log.info("Building coast scenario for flat: {}", flat.getFlatId());

        int oceanLevel = flat.getOceanLevel();

        // Step 1: Create hilly terrain base using HillyTerrainManipulator
        int hillHeight = getLandOffset();
        int baseHeight = getHexGridLevel();

        long seed = parseLongParameter(parameters, "seed", System.currentTimeMillis());

        log.debug("Coast terrain generation: baseHeight={}, hillHeight={}, oceanLevel={}, seed={}",
                baseHeight, hillHeight, oceanLevel, seed);

        // Build parameters for HillyTerrainManipulator
        Map<String, String> hillyParams = new HashMap<>();
        hillyParams.put(HillyTerrainManipulator.PARAM_BASE_HEIGHT, String.valueOf(baseHeight));
        hillyParams.put(HillyTerrainManipulator.PARAM_HILL_HEIGHT, String.valueOf(hillHeight));
        hillyParams.put(HillyTerrainManipulator.PARAM_SEED, String.valueOf(seed));

        // Use HillyTerrainManipulator to generate base terrain
        context.getManipulatorService().executeManipulator(
                HillyTerrainManipulator.NAME,
                flat,
                0, 0,
                flat.getSizeX(), flat.getSizeZ(),
                hillyParams
        );

        // Step 2: Determine which sides have ocean neighbors
        Set<WHexGrid.NEIGHBOR> oceanSides = determineOceanSides();

        log.debug("Ocean sides detected: {}", oceanSides);

        // Step 3: Create coastline towards ocean sides
        if (!oceanSides.isEmpty()) {
            createCoastline(flat, oceanSides, oceanLevel);
        }

        // Step 4: Set materials (SAND at coastline, GRASS inland)
        setCoastMaterials(flat, oceanSides, oceanLevel);

        // Step 5: Blend edges with neighbors for smooth transitions
        HexGridEdgeBlender edgeBlender = new HexGridEdgeBlender(flat, context);
        edgeBlender.blendAllEdges();

        log.info("Coast scenario completed: oceanSides={}, baseHeight={}, hillHeight={}",
                oceanSides, baseHeight, hillHeight);
    }

    /**
     * Determine which neighboring grids are ocean.
     */
    private Set<WHexGrid.NEIGHBOR> determineOceanSides() {
        Set<WHexGrid.NEIGHBOR> oceanSides = new HashSet<>();

        for (WHexGrid.NEIGHBOR direction : WHexGrid.NEIGHBOR.values()) {
            // Check if neighbor has an OceanBuilder
            context.getBuilderFor(direction).ifPresent(builder -> {
                if (builder instanceof OceanBuilder || builder instanceof IslandBuilder) {
                    oceanSides.add(direction);
                }
            });
        }

        return oceanSides;
    }

    /**
     * Create coastline that slopes down to ocean level towards ocean sides.
     */
    private void createCoastline(WFlat flat, Set<WHexGrid.NEIGHBOR> oceanSides, int oceanLevel) {
        int sizeX = flat.getSizeX();
        int sizeZ = flat.getSizeZ();

        for (int z = 0; z < sizeZ; z++) {
            for (int x = 0; x < sizeX; x++) {
                // Calculate distance to each ocean side
                double minDistance = Double.MAX_VALUE;

                for (WHexGrid.NEIGHBOR oceanSide : oceanSides) {
                    double distance = calculateDistanceToSide(x, z, sizeX, sizeZ, oceanSide);
                    minDistance = Math.min(minDistance, distance);
                }

                // If within coast transition zone, slope down to ocean level
                if (minDistance < COAST_WIDTH) {
                    int currentLevel = flat.getLevel(x, z);

                    // Calculate slope factor (1.0 at land edge, 0.0 at ocean edge)
                    double slopeFactor = minDistance / COAST_WIDTH;

                    // Target height: ocean level at coast, current height inland
                    int targetHeight = (int) (oceanLevel + (currentLevel - oceanLevel) * slopeFactor);

                    // Ensure coastal area dips below ocean level
                    if (minDistance < 3) {
                        targetHeight = oceanLevel - 2;
                    }

                    flat.setLevel(x, z, Math.max(0, Math.min(255, targetHeight)));
                }
            }
        }
    }

    /**
     * Calculate distance from a point to a specific side of the hex grid.
     */
    private double calculateDistanceToSide(int x, int z, int sizeX, int sizeZ, WHexGrid.NEIGHBOR side) {
        switch (side) {
            case NORTH_WEST:
            case NORTH_EAST:
                // Top sides - distance to top edge
                return z;
            case SOUTH_WEST:
            case SOUTH_EAST:
                // Bottom sides - distance to bottom edge
                return sizeZ - z - 1;
            case WEST:
                // Left side - distance to left edge
                return x;
            case EAST:
                // Right side - distance to right edge
                return sizeX - x - 1;
            default:
                return Double.MAX_VALUE;
        }
    }

    /**
     * Set materials: SAND near coastline, GRASS inland.
     */
    private void setCoastMaterials(WFlat flat, Set<WHexGrid.NEIGHBOR> oceanSides, int oceanLevel) {
        int sizeX = flat.getSizeX();
        int sizeZ = flat.getSizeZ();

        for (int z = 0; z < sizeZ; z++) {
            for (int x = 0; x < sizeX; x++) {
                int level = flat.getLevel(x, z);

                // Calculate distance to nearest ocean side
                double minDistance = Double.MAX_VALUE;
                for (WHexGrid.NEIGHBOR oceanSide : oceanSides) {
                    double distance = calculateDistanceToSide(x, z, sizeX, sizeZ, oceanSide);
                    minDistance = Math.min(minDistance, distance);
                }

                // Set material based on distance and height
                if (level <= oceanLevel || minDistance < SAND_WIDTH) {
                    flat.setColumn(x, z, FlatMaterialService.SAND);
                } else {
                    flat.setColumn(x, z, FlatMaterialService.GRASS);
                }
            }
        }
    }

    @Override
    protected int getDefaultLandOffset() {
        return 5;  // COAST: normal variation for coastal area
    }

    @Override
    protected int getDefaultLandLevel() {
        return 5;  // COAST: at ocean level
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
}
