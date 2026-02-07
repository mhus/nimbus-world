package de.mhus.nimbus.world.generator.flat.hexgrid;

import de.mhus.nimbus.shared.utils.FastNoiseLite;
import de.mhus.nimbus.world.generator.flat.FlatMaterialService;
import de.mhus.nimbus.world.generator.flat.manipulator.HillyTerrainManipulator;
import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * Coast scenario builder.
 * Creates varied coast terrain with noise-based ocean floor.
 * Adjusts sides based on neighbor types:
 * - Island/Coast neighbors: Shallow ocean edge (like IslandBuilder)
 * - Ocean/No neighbors: Deep ocean edge (like OceanBuilder)
 * - Land neighbors: Coast strip with landLevel=0, landOffset=1
 */
@Slf4j
public class CoastBuilder extends HexGridBuilder {

    private static final int COAST_STRIP_WIDTH = 30;  // Width of coastal strip along land sides

    @Override
    public void buildFlat() {
        WFlat flat = context.getFlat();

        log.debug("Building coast scenario for flat: {}", flat.getFlatId());

        int oceanLevel = flat.getSeaLevel();
        long seed = context.getWorld().getNoiseSeed();  // Use seed from world
        float frequency = (float) context.getWorld().getNoiseFrequency();  // Use frequency from world

        log.debug("Coast generation: oceanLevel={}, seed={}, frequency={}", oceanLevel, seed, frequency);

        // Step 1: Create base noise terrain using HillyTerrainManipulator (like OceanBuilder)
        createBaseNoiseTerrain(flat, oceanLevel, seed);

        // Step 2: Adjust sides based on neighbor types
        adjustSidesBasedOnNeighbors(flat, oceanLevel, seed, frequency);

        log.debug("Coast scenario completed");
    }

    /**
     * Create base noise terrain using HillyTerrainManipulator (like OceanBuilder).
     * This creates a hilly ocean floor as the foundation.
     */
    private void createBaseNoiseTerrain(WFlat flat, int oceanLevel, long seed) {
        // Use same approach as OceanBuilder
        int hillHeight = getOffset();
        int baseHeight = Math.min(getHexGridAsl(), oceanLevel - hillHeight + 2);

        log.debug("Creating base noise terrain: baseHeight={}, hillHeight={}", baseHeight, hillHeight);

        // Build parameters for HillyTerrainManipulator
        Map<String, String> hillyParams = new HashMap<>();
        hillyParams.put(HillyTerrainManipulator.PARAM_BASE_HEIGHT, String.valueOf(baseHeight));
        hillyParams.put(HillyTerrainManipulator.PARAM_HILL_HEIGHT, String.valueOf(hillHeight));
        hillyParams.put(HillyTerrainManipulator.PARAM_SEED, String.valueOf(seed));

        // Generate base terrain with noise
        context.getManipulatorService().executeManipulator(
                HillyTerrainManipulator.NAME,
                flat,
                0, 0,
                flat.getSizeX(), flat.getSizeZ(),
                hillyParams
        );

        // Set all to sand material
        for (int z = 0; z < flat.getSizeZ(); z++) {
            for (int x = 0; x < flat.getSizeX(); x++) {
                flat.setColumn(x, z, FlatMaterialService.SAND);
            }
        }
    }

    /**
     * Adjust sides based on neighbor types.
     * After base noise is created, "bend" the grid surface towards target heights at each side.
     * - Island/Coast neighbors: Shallow ocean (oceanLevel - 2)
     * - Ocean neighbors: Keep deep ocean (no adjustment)
     * - Land neighbors: Coast strip (oceanLevel)
     * - No neighbor: Will be blended later with default noise
     */
    private void adjustSidesBasedOnNeighbors(WFlat flat, int oceanLevel, long seed, float frequency) {
        int sizeX = flat.getSizeX();
        int sizeZ = flat.getSizeZ();

        // Initialize noise for variation
        FastNoiseLite noise = new FastNoiseLite((int) seed);
        noise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        noise.SetFrequency(frequency);

        // Determine target level for each side
        Map<WHexGrid.EDGE, Integer> sideTargetLevels = new HashMap<>();

        for (WHexGrid.EDGE side : WHexGrid.EDGE.values()) {
            WHexGrid neighbor = context.getNeighborGrids().get(side);

            if (neighbor == null) {
                // No neighbor - will be handled by blendEdgesWithDefaultNoise
                sideTargetLevels.put(side, null);
                log.debug("Side {}: No neighbor - will blend with default noise later", side);
                continue;
            }

            String neighborBuilder = neighbor.getParameters() != null
                ? neighbor.getParameters().get("g_builder")
                : null;

            if (neighborBuilder == null) {
                sideTargetLevels.put(side, null);
                log.debug("Side {}: Unknown neighbor - will blend with default noise later", side);
            } else if ("island".equals(neighborBuilder) || "coast".equals(neighborBuilder)) {
                // Shallow ocean edge
                sideTargetLevels.put(side, oceanLevel - 2);
                log.debug("Side {}: Island/Coast neighbor - target level {}", side, oceanLevel - 2);
            } else if ("ocean".equals(neighborBuilder)) {
                // Keep base noise
                sideTargetLevels.put(side, null);
                log.debug("Side {}: Ocean neighbor - keeping base noise", side);
            } else {
                // Land neighbor - coast strip at sea level
                sideTargetLevels.put(side, oceanLevel);
                log.debug("Side {}: Land neighbor ({}) - target level {}", side, neighborBuilder, oceanLevel);
            }
        }

        // Now "bend" the grid surface towards target levels
        bendGridTowardsSides(flat, sideTargetLevels, noise);
    }

    /**
     * "Bend" the grid surface towards target levels at each side.
     * Treats the grid as a surface that is gradually adjusted from center to edges.
     * For each point, calculate distance to each side and interpolate height.
     */
    private void bendGridTowardsSides(WFlat flat, Map<WHexGrid.EDGE, Integer> sideTargetLevels, FastNoiseLite noise) {
        int sizeX = flat.getSizeX();
        int sizeZ = flat.getSizeZ();

        // Iterate over all points in the grid
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                int baseLevel = flat.getLevel(x, z);  // Original noise level

                // Calculate distance to each edge (rectangular approximation)
                int distToWest = x;
                int distToEast = sizeX - 1 - x;
                int distToNorth = z;
                int distToSouth = sizeZ - 1 - z;

                // For each side with a target level, calculate influence
                Integer targetLevel = null;
                double minDistance = COAST_STRIP_WIDTH;

                // Check WEST
                if (distToWest < COAST_STRIP_WIDTH && sideTargetLevels.get(WHexGrid.EDGE.WEST) != null) {
                    if (distToWest < minDistance) {
                        minDistance = distToWest;
                        targetLevel = sideTargetLevels.get(WHexGrid.EDGE.WEST);
                    }
                }

                // Check EAST
                if (distToEast < COAST_STRIP_WIDTH && sideTargetLevels.get(WHexGrid.EDGE.EAST) != null) {
                    if (distToEast < minDistance) {
                        minDistance = distToEast;
                        targetLevel = sideTargetLevels.get(WHexGrid.EDGE.EAST);
                    }
                }

                // Check NORTH (top in Z)
                if (distToNorth < COAST_STRIP_WIDTH) {
                    // North can be NW, NE depending on position
                    Integer nwTarget = sideTargetLevels.get(WHexGrid.EDGE.NORTH_WEST);
                    Integer neTarget = sideTargetLevels.get(WHexGrid.EDGE.NORTH_EAST);

                    if (nwTarget != null || neTarget != null) {
                        // Interpolate between NW and NE based on X position
                        Integer northTarget = (nwTarget != null && neTarget != null)
                            ? (int)((nwTarget + neTarget) / 2.0)
                            : (nwTarget != null ? nwTarget : neTarget);

                        if (northTarget != null && distToNorth < minDistance) {
                            minDistance = distToNorth;
                            targetLevel = northTarget;
                        }
                    }
                }

                // Check SOUTH (bottom in Z)
                if (distToSouth < COAST_STRIP_WIDTH) {
                    // South can be SW, SE depending on position
                    Integer swTarget = sideTargetLevels.get(WHexGrid.EDGE.SOUTH_WEST);
                    Integer seTarget = sideTargetLevels.get(WHexGrid.EDGE.SOUTH_EAST);

                    if (swTarget != null || seTarget != null) {
                        // Interpolate between SW and SE based on X position
                        Integer southTarget = (swTarget != null && seTarget != null)
                            ? (int)((swTarget + seTarget) / 2.0)
                            : (swTarget != null ? swTarget : seTarget);

                        if (southTarget != null && distToSouth < minDistance) {
                            minDistance = distToSouth;
                            targetLevel = southTarget;
                        }
                    }
                }

                // If we found a target level, interpolate based on distance
                if (targetLevel != null) {
                    double blendFactor = minDistance / COAST_STRIP_WIDTH;  // 0.0 at edge, 1.0 at center

                    // Add noise variation
                    float noiseValue = noise.GetNoise((float) x, (float) z);
                    int noiseVariation = (int) (noiseValue * 2);  // ±2 variation

                    // Interpolate: edge = targetLevel, center = baseLevel
                    int newLevel = (int) Math.round(targetLevel * (1.0 - blendFactor) + baseLevel * blendFactor) + noiseVariation;

                    flat.setLevel(x, z, newLevel);

                    // Update material for land areas
                    if (newLevel >= flat.getSeaLevel()) {
                        float grassChance = noise.GetNoise((float) x * 2, (float) z * 2);
                        if (grassChance > 0.3) {
                            flat.setColumn(x, z, FlatMaterialService.GRASS);
                        } else {
                            flat.setColumn(x, z, FlatMaterialService.SAND);
                        }
                    }
                }
            }
        }
    }

    @Override
    protected int getDefaultOffset() {
        return 5;  // COAST: medium variation for base noise
    }

    @Override
    protected int getDefaultAsl() {
        return -5;  // COAST: below ocean level (for base noise)
    }

    @Override
    public int getLandSideLevel(WHexGrid.EDGE side) {
        return getCenterAsl();
    }
}
