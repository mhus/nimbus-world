package de.mhus.nimbus.world.generator.flat;

import de.mhus.nimbus.world.shared.generator.WFlat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Random;

/**
 * Sharp peak manipulator.
 * Creates conical mountains with exponential height falloff for dramatic sharp peaks.
 * Based on ShapeFactory.cpp Case 41.
 * <p>
 * Parameters:
 * - centerX: X position of peak (default: sizeX/2)
 * - centerZ: Z position of peak (default: sizeZ/2)
 * - radius: Base radius of peak (default: 30, range: 5-min(sizeX,sizeZ)/2)
 * - height: Height of peak above base (default: 80, range: 10-150)
 * - steepness: Exponential falloff factor (default: 2.0, range: 1.0-5.0)
 * - seed: Random seed (default: System.currentTimeMillis())
 */
@Component
@Slf4j
public class SharpPeakManipulator implements FlatManipulator {

    public static final String NAME = "sharp-peak";
    public static final String PARAM_CENTER_X = "centerX";
    public static final String PARAM_CENTER_Z = "centerZ";
    public static final String PARAM_RADIUS = "radius";
    public static final String PARAM_HEIGHT = "height";
    public static final String PARAM_STEEPNESS = "steepness";
    public static final String PARAM_SEED = "seed";

    private static final int DEFAULT_RADIUS = 30;
    private static final int DEFAULT_HEIGHT = 80;
    private static final double DEFAULT_STEEPNESS = 2.0;
    private static final double NOISE_STDDEV = 2.0;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void manipulate(WFlat flat, int x, int z, int sizeX, int sizeZ,
                          Map<String, String> parameters) {
        log.debug("Starting sharp peak manipulation: region=({},{},{},{})", x, z, sizeX, sizeZ);

        // Parse parameters
        int centerX = parseIntParameter(parameters, PARAM_CENTER_X, sizeX / 2);
        int centerZ = parseIntParameter(parameters, PARAM_CENTER_Z, sizeZ / 2);
        int radius = parseIntParameter(parameters, PARAM_RADIUS, DEFAULT_RADIUS);
        int height = parseIntParameter(parameters, PARAM_HEIGHT, DEFAULT_HEIGHT);
        double steepness = parseDoubleParameter(parameters, PARAM_STEEPNESS, DEFAULT_STEEPNESS);
        long seed = parseLongParameter(parameters, PARAM_SEED, System.currentTimeMillis());

        // Validate and clamp parameters
        int maxRadius = Math.min(sizeX, sizeZ) / 2;
        radius = Math.max(5, Math.min(maxRadius, radius));
        height = Math.max(10, Math.min(150, height));
        steepness = Math.max(1.0, Math.min(5.0, steepness));

        // Initialize random generator
        Random random = new Random(seed);

        // Setup FlatPainter
        FlatPainter painter = new FlatPainter(flat);

        // Calculate absolute center coordinates
        int absoluteCenterX = x + centerX;
        int absoluteCenterZ = z + centerZ;

        // Get base level at center
        int baseLevel = flat.getLevel(absoluteCenterX, absoluteCenterZ);

        // Draw conical peak with exponential falloff
        // Formula: height = baseLevel + peakHeight * exp(-steepness * distance / radius)
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                double distance = Math.sqrt(dx * dx + dz * dz);

                if (distance <= radius) {
                    int xi = absoluteCenterX + dx;
                    int zi = absoluteCenterZ + dz;

                    // Exponential falloff for sharp peak
                    // Higher steepness = steeper slopes
                    double heightFactor = Math.exp(-steepness * distance / radius);
                    int peakLevel = baseLevel + (int) (height * heightFactor);

                    // Add slight random variation using Gaussian noise
                    int variation = (int) (random.nextGaussian() * NOISE_STDDEV);
                    peakLevel += variation;

                    // Clamp to valid range
                    peakLevel = Math.max(0, Math.min(255, peakLevel));

                    // Use HIGHER painter to only raise terrain
                    painter.paint(xi, zi, peakLevel, FlatPainter.HIGHER);
                }
            }
        }

        // Optional: light smoothing at base only for subtle blend
        // Factor 0.1 means very light smoothing
        painter.soften(x, z, x + sizeX - 1, z + sizeZ - 1, 0.1);

        log.info("Sharp peak manipulation completed: centerX={}, centerZ={}, radius={}, height={}, steepness={}",
                centerX, centerZ, radius, height, steepness);
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

    private long parseLongParameter(Map<String, String> parameters, String name, long defaultValue) {
        if (parameters == null || !parameters.containsKey(name)) {
            return defaultValue;
        }
        try {
            return Long.parseLong(parameters.get(name));
        } catch (NumberFormatException e) {
            log.warn("Invalid long parameter '{}': {}, using default: {}",
                    name, parameters.get(name), defaultValue);
            return defaultValue;
        }
    }
}
