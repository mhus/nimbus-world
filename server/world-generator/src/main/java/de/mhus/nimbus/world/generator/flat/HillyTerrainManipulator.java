package de.mhus.nimbus.world.generator.flat;

import de.mhus.nimbus.shared.utils.FastNoiseLite;
import de.mhus.nimbus.world.shared.generator.WFlat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Hilly terrain manipulator with pronounced hills.
 * Generates terrain with large height variations and steeper slopes.
 * Uses larger noise scales than NormalTerrainManipulator.
 * <p>
 * Parameters:
 * - baseHeight: Base terrain height (default: 64)
 * - hillHeight: Maximum hill height above/below base (default: 64)
 * - seed: Random seed for noise generation (default: current time)
 */
@Component
@Slf4j
public class HillyTerrainManipulator implements FlatManipulator {

    public static final String NAME = "hilly";
    public static final String PARAM_BASE_HEIGHT = "baseHeight";
    public static final String PARAM_HILL_HEIGHT = "hillHeight";
    public static final String PARAM_SEED = "seed";

    private static final int DEFAULT_BASE_HEIGHT = 64;
    private static final int DEFAULT_HILL_HEIGHT = 64;

    // Noise scales for multi-octave noise (larger scales = more pronounced features)
    private static final double SCALE_1 = 0.008;
    private static final double SCALE_2 = 0.04;
    private static final double SCALE_3 = 0.08;
    private static final double WEIGHT_1 = 0.7;
    private static final double WEIGHT_2 = 0.2;
    private static final double WEIGHT_3 = 0.1;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void manipulate(WFlat flat, int x, int z, int sizeX, int sizeZ, Map<String, String> parameters) {
        log.debug("Manipulating hilly terrain: region=({},{},{},{})", x, z, sizeX, sizeZ);

        // Parse parameters
        int baseHeight = parseIntParameter(parameters, PARAM_BASE_HEIGHT, DEFAULT_BASE_HEIGHT);
        int hillHeight = parseIntParameter(parameters, PARAM_HILL_HEIGHT, DEFAULT_HILL_HEIGHT);
        long seed = parseLongParameter(parameters, PARAM_SEED, System.currentTimeMillis());

        // Clamp values to valid ranges
        baseHeight = Math.max(0, Math.min(255, baseHeight));
        hillHeight = Math.max(0, Math.min(128, hillHeight));

        int oceanLevel = flat.getOceanLevel();

        // Initialize noise generator
        FastNoiseLite noise = new FastNoiseLite((int) seed);
        noise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2);
        noise.SetFrequency(1.0f);

        // Generate terrain with noise
        for (int localZ = 0; localZ < sizeZ; localZ++) {
            for (int localX = 0; localX < sizeX; localX++) {
                int flatX = x + localX;
                int flatZ = z + localZ;

                // Calculate world coordinates for consistent noise across regions
                int worldX = flatX + flat.getMountX();
                int worldZ = flatZ + flat.getMountZ();

                // Calculate terrain height using multi-octave noise
                int terrainHeight = calculateTerrainHeight(noise, worldX, worldZ, baseHeight, hillHeight);

                // Set level (height)
                flat.setLevel(flatX, flatZ, terrainHeight);

                // Set column (material based on height vs water level)
                int materialId = terrainHeight <= oceanLevel
                        ? FlatMaterialService.SAND
                        : FlatMaterialService.GRASS;
                flat.setColumn(flatX, flatZ, materialId);
            }
        }

        log.info("Hilly terrain manipulated: region=({},{},{},{}), base={}, hillHeight={}, seed={}",
                x, z, sizeX, sizeZ, baseHeight, hillHeight, seed);
    }

    private int calculateTerrainHeight(FastNoiseLite noise, int worldX, int worldZ,
                                       int baseHeight, int hillHeight) {
        // Multi-octave noise for natural-looking hilly terrain
        // Larger scales (smaller frequency multipliers) create bigger, smoother hills
        double noise1 = noise.GetNoise((float) (worldX * SCALE_1), (float) (worldZ * SCALE_1));
        double noise2 = noise.GetNoise((float) (worldX * SCALE_2), (float) (worldZ * SCALE_2));
        double noise3 = noise.GetNoise((float) (worldX * SCALE_3), (float) (worldZ * SCALE_3));

        double combined = noise1 * WEIGHT_1 + noise2 * WEIGHT_2 + noise3 * WEIGHT_3;
        double height = baseHeight + combined * hillHeight;

        return (int) Math.floor(height);
    }

    private int parseIntParameter(Map<String, String> parameters, String name, int defaultValue) {
        if (parameters == null || !parameters.containsKey(name)) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(parameters.get(name));
        } catch (NumberFormatException e) {
            log.warn("Invalid integer parameter '{}': {}, using default: {}", name, parameters.get(name), defaultValue);
            return defaultValue;
        }
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
