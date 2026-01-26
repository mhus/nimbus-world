package de.mhus.nimbus.world.generator.flat.hexgrid;

import de.mhus.nimbus.shared.utils.FastNoiseLite;
import de.mhus.nimbus.world.generator.flat.FlatMaterialService;
import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Coast scenario builder.
 * Creates flat ocean floor (1 below oceanLevel) and coastlines with sand/grass.
 * Coastlines are irregular using noise for natural appearance.
 */
@Slf4j
public class CoastBuilder extends HexGridBuilder {

    private static final int COAST_WIDTH = 20;  // Width of coastal zone

    @Override
    public void buildFlat() {
        WFlat flat = context.getFlat();

        log.info("Building coast scenario for flat: {}", flat.getFlatId());

        int oceanLevel = flat.getSeaLevel();
        long seed = parseLongParameter(parameters, "seed", System.currentTimeMillis());

        log.debug("Coast generation: oceanLevel={}, seed={}", oceanLevel, seed);

        // Step 1: Fill entire flat with flat ocean (1 below oceanLevel)
        fillWithOcean(flat, oceanLevel);

        // Step 2: Determine which sides are NOT ocean/islands (these are land sides)
        Set<WHexGrid.SIDE> landSides = determineLandSides();

        log.debug("Land sides detected: {}", landSides);

        // Step 3: Create coastlines along land sides with noise
        if (!landSides.isEmpty()) {
            createCoastlines(flat, landSides, oceanLevel, seed);
        }

        log.info("Coast scenario completed: landSides={}", landSides);
    }

    /**
     * Fill entire flat with flat ocean floor (1 below oceanLevel).
     */
    private void fillWithOcean(WFlat flat, int oceanLevel) {
        int oceanFloorLevel = oceanLevel - 1;
        int sizeX = flat.getSizeX();
        int sizeZ = flat.getSizeZ();

        for (int z = 0; z < sizeZ; z++) {
            for (int x = 0; x < sizeX; x++) {
                flat.setLevel(x, z, oceanFloorLevel);
                flat.setColumn(x, z, FlatMaterialService.SAND);
            }
        }

        log.debug("Filled flat with ocean floor at level {}", oceanFloorLevel);
    }

    /**
     * Determine which sides are NOT ocean or islands (= land sides).
     * These are the sides where we create coastlines.
     */
    private Set<WHexGrid.SIDE> determineLandSides() {
        Set<WHexGrid.SIDE> landSides = new HashSet<>();

        for (WHexGrid.SIDE direction : WHexGrid.SIDE.values()) {
            // Check if neighbor is NOT ocean or island
            boolean isLand = context.getBuilderFor(direction)
                    .map(builder -> !(builder instanceof OceanBuilder) && !(builder instanceof IslandBuilder))
                    .orElse(false);

            if (isLand) {
                landSides.add(direction);
            }
        }

        return landSides;
    }

    /**
     * Create irregular coastlines with sand and grass along land sides.
     * Uses noise for natural, wavy coastline.
     */
    private void createCoastlines(WFlat flat, Set<WHexGrid.SIDE> landSides, int oceanLevel, long seed) {
        int sizeX = flat.getSizeX();
        int sizeZ = flat.getSizeZ();

        // Initialize noise generator for irregular coastline
        FastNoiseLite noise = new FastNoiseLite((int) seed);
        noise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        noise.SetFrequency(0.1f);  // Frequency for coastline variation

        for (int z = 0; z < sizeZ; z++) {
            for (int x = 0; x < sizeX; x++) {
                // Calculate distance to nearest land side
                double minDistance = Double.MAX_VALUE;

                for (WHexGrid.SIDE landSide : landSides) {
                    double distance = calculateDistanceToSide(x, z, sizeX, sizeZ, landSide);
                    minDistance = Math.min(minDistance, distance);
                }

                // If within coast zone, create coastline
                if (minDistance < COAST_WIDTH) {
                    // Add noise to distance for irregular coastline
                    float noiseValue = noise.GetNoise((float) x, (float) z);
                    double noisyDistance = minDistance + noiseValue * 5;  // ±5 pixels variation

                    // Determine height and material based on noisy distance
                    if (noisyDistance < COAST_WIDTH * 0.3) {
                        // Close to land: oceanLevel or oceanLevel+1
                        int height = oceanLevel + (noisyDistance < COAST_WIDTH * 0.15 ? 1 : 0);
                        flat.setLevel(x, z, height);

                        // Mix of sand and grass (grass appears occasionally)
                        float grassChance = noise.GetNoise((float) x * 2, (float) z * 2);
                        if (grassChance > 0.3) {
                            flat.setColumn(x, z, FlatMaterialService.GRASS);
                        } else {
                            flat.setColumn(x, z, FlatMaterialService.SAND);
                        }
                    } else if (noisyDistance < COAST_WIDTH * 0.6) {
                        // Mid coast: mainly sand at oceanLevel
                        flat.setLevel(x, z, oceanLevel);
                        flat.setColumn(x, z, FlatMaterialService.SAND);
                    }
                    // Else: keep ocean floor (already set in fillWithOcean)
                }
            }
        }

        log.debug("Coastlines created with irregular edges");
    }

    /**
     * Calculate distance from a point to a specific side of the hex grid.
     */
    private double calculateDistanceToSide(int x, int z, int sizeX, int sizeZ, WHexGrid.SIDE side) {
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

    @Override
    protected int getDefaultLandOffset() {
        return 0;  // COAST: flat
    }

    @Override
    protected int getDefaultLandLevel() {
        return -1;  // COAST: 1 below ocean level
    }

    @Override
    public int getLandSideLevel(WHexGrid.SIDE side) {
        return getLandCenterLevel();
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
