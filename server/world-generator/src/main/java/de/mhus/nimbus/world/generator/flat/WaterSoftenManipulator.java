package de.mhus.nimbus.world.generator.flat;

import de.mhus.nimbus.world.shared.generator.WFlat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Random;

/**
 * Water soften manipulator.
 * Special smoothing for water areas that preserves water/land boundaries.
 * Based on ShapeFactory.cpp Case 17/18.
 * <p>
 * Parameters:
 * - passes: Number of smoothing passes (default: 4, range: 1-8)
 * - waterThreshold: Threshold for water detection (default: oceanLevel)
 * - probability: Inverse probability for boundary decisions (default: 5, range: 2-20)
 */
@Component
@Slf4j
public class WaterSoftenManipulator implements FlatManipulator {

    public static final String NAME = "water-soften";
    public static final String PARAM_PASSES = "passes";
    public static final String PARAM_WATER_THRESHOLD = "waterThreshold";
    public static final String PARAM_PROBABILITY = "probability";

    private static final int DEFAULT_PASSES = 4;
    private static final int DEFAULT_PROBABILITY = 5;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void manipulate(WFlat flat, int x, int z, int sizeX, int sizeZ,
                          Map<String, String> parameters) {
        log.debug("Starting water soften manipulation: region=({},{},{},{})", x, z, sizeX, sizeZ);

        // Parse parameters
        int passes = parseIntParameter(parameters, PARAM_PASSES, DEFAULT_PASSES);
        int waterThreshold = parseIntParameter(parameters, PARAM_WATER_THRESHOLD, flat.getOceanLevel());
        int probability = parseIntParameter(parameters, PARAM_PROBABILITY, DEFAULT_PROBABILITY);

        // Validate and clamp parameters
        passes = Math.max(1, Math.min(8, passes));
        waterThreshold = Math.max(0, Math.min(255, waterThreshold));
        probability = Math.max(2, Math.min(20, probability));

        // Initialize random generator
        Random random = new Random();

        // 4-pass raster smoothing with offsets
        // Similar to SoftenRaster but with water-specific logic
        for (int pass = 0; pass < passes; pass++) {
            int offsetX = (pass % 2);
            int offsetZ = ((pass / 2) % 2);

            for (int localZ = offsetZ; localZ < sizeZ; localZ += 2) {
                for (int localX = offsetX; localX < sizeX; localX += 2) {
                    int xi = x + localX;
                    int zi = z + localZ;

                    int currentLevel = flat.getLevel(xi, zi);

                    // Count water neighbors (neighbors with level <= waterThreshold)
                    int waterNeighbors = 0;
                    int sum = 0;
                    int count = 0;

                    // Check 8 neighbors
                    for (int dz = -1; dz <= 1; dz++) {
                        for (int dx = -1; dx <= 1; dx++) {
                            if (dx == 0 && dz == 0) continue; // Skip center

                            int nxi = xi + dx;
                            int nzi = zi + dz;

                            // Bounds check
                            if (nxi >= x && nxi < x + sizeX &&
                                nzi >= z && nzi < z + sizeZ) {
                                int neighborLevel = flat.getLevel(nxi, nzi);
                                sum += neighborLevel;
                                count++;

                                if (neighborLevel <= waterThreshold) {
                                    waterNeighbors++;
                                }
                            }
                        }
                    }

                    // Calculate mean of neighbors
                    int mean = count > 0 ? sum / count : currentLevel;

                    // Water boundary logic
                    if (waterNeighbors > 0 && waterNeighbors < count) {
                        // Mixed area (some water, some land)
                        if (waterNeighbors < 4) {
                            // More land than water: random decision
                            if (random.nextInt(probability) == 0) {
                                flat.setLevel(xi, zi, mean / 15);
                            } else {
                                // Set to water level (0 or very low)
                                flat.setLevel(xi, zi, 0);
                            }
                        } else {
                            // More water than land: set to water
                            flat.setLevel(xi, zi, 0);
                        }
                    }
                    // Pure water or pure land: no change
                }
            }
        }

        // Final standard smoothing for blend
        FlatPainter painter = new FlatPainter(flat);
        painter.soften(x, z, x + sizeX - 1, z + sizeZ - 1, 0.3);

        log.info("Water soften manipulation completed: passes={}, waterThreshold={}, probability={}",
                passes, waterThreshold, probability);
    }

    // Parameter parsing helper methods

    private int parseIntParameter(Map<String, String> parameters, String name, int defaultValue) {
        if (parameters == null || !parameters.containsKey(name)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(parameters.get(name));
        } catch (NumberFormatException e) {
            log.warn("Invalid integer parameter '{}': {}, using default: {}",
                    name, parameters.get(name), defaultValue);
            return defaultValue;
        }
    }
}
