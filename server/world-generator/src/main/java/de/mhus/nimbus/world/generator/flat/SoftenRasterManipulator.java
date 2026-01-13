package de.mhus.nimbus.world.generator.flat;

import de.mhus.nimbus.world.shared.generator.WFlat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Soften raster manipulator.
 * Performance-optimized smoothing using offset raster pattern.
 * Processes pixels in checkerboard pattern for speed.
 * Based on ShapeFactory.cpp Case 10.
 * <p>
 * Parameters:
 * - passes: Number of smoothing passes (default: 4, range: 1-8)
 * - factor: Smoothing strength per pass (default: 0.5, range: 0.0-1.0)
 */
@Component
@Slf4j
public class SoftenRasterManipulator implements FlatManipulator {

    public static final String NAME = "soften-raster";
    public static final String PARAM_PASSES = "passes";
    public static final String PARAM_FACTOR = "factor";

    private static final int DEFAULT_PASSES = 4;
    private static final double DEFAULT_FACTOR = 0.5;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void manipulate(WFlat flat, int x, int z, int sizeX, int sizeZ,
                          Map<String, String> parameters) {
        log.debug("Starting soften raster manipulation: region=({},{},{},{})", x, z, sizeX, sizeZ);

        // Parse parameters
        int passes = parseIntParameter(parameters, PARAM_PASSES, DEFAULT_PASSES);
        double factor = parseDoubleParameter(parameters, PARAM_FACTOR, DEFAULT_FACTOR);

        // Validate and clamp parameters
        passes = Math.max(1, Math.min(8, passes));
        factor = Math.max(0.0, Math.min(1.0, factor));

        // Multi-pass smoothing with offset pattern for performance
        // Each pass processes every other pixel in a checkerboard pattern
        for (int pass = 0; pass < passes; pass++) {
            // Calculate offset for this pass
            // Pass 0: (0,0), Pass 1: (1,0), Pass 2: (1,1), Pass 3: (0,1), then repeat
            int offsetX = (pass % 2);
            int offsetZ = ((pass / 2) % 2);

            for (int localZ = offsetZ; localZ < sizeZ; localZ += 2) {
                for (int localX = offsetX; localX < sizeX; localX += 2) {
                    int xi = x + localX;
                    int zi = z + localZ;

                    // Average with 4 orthogonal neighbors (not diagonal)
                    int sum = flat.getLevel(xi, zi);
                    int count = 1;

                    // Check 4 orthogonal neighbors
                    // North
                    if (localZ > 0) {
                        sum += flat.getLevel(xi, zi - 1);
                        count++;
                    }
                    // South
                    if (localZ < sizeZ - 1) {
                        sum += flat.getLevel(xi, zi + 1);
                        count++;
                    }
                    // West
                    if (localX > 0) {
                        sum += flat.getLevel(xi - 1, zi);
                        count++;
                    }
                    // East
                    if (localX < sizeX - 1) {
                        sum += flat.getLevel(xi + 1, zi);
                        count++;
                    }

                    // Calculate new level
                    int original = flat.getLevel(xi, zi);
                    int mean = sum / count;
                    int newLevel = (int) Math.round(factor * mean + (1 - factor) * original);

                    // Clamp to valid range
                    newLevel = Math.max(0, Math.min(255, newLevel));

                    flat.setLevel(xi, zi, newLevel);
                }
            }
        }

        log.info("Soften raster manipulation completed: passes={}, factor={}",
                passes, factor);
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

    private double parseDoubleParameter(Map<String, String> parameters, String name, double defaultValue) {
        if (parameters == null || !parameters.containsKey(name)) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(parameters.get(name));
        } catch (NumberFormatException e) {
            log.warn("Invalid double parameter '{}': {}, using default: {}",
                    name, parameters.get(name), defaultValue);
            return defaultValue;
        }
    }
}
