package de.mhus.nimbus.world.generator.flat;

import de.mhus.nimbus.world.shared.generator.WFlat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Random;

/**
 * Random pixel manipulator.
 * Sets random pixels to a specific height with variation.
 * Based on ShapeFactory.cpp Case 19.
 * <p>
 * Parameters:
 * - pixelCount: Number of random pixels to modify (default: 100, range: 1-sizeX*sizeZ)
 * - targetHeight: Height to set pixels to (default: 64, range: 0-255)
 * - heightVariation: Random variation around target height (default: 5, range: 0-50)
 * - mode: Modification mode (default: "set") - "set", "add", "higher", "lower"
 * - seed: Random seed (default: System.currentTimeMillis())
 */
@Component
@Slf4j
public class RandomPixelManipulator implements FlatManipulator {

    public static final String NAME = "random-pixels";
    public static final String PARAM_PIXEL_COUNT = "pixelCount";
    public static final String PARAM_TARGET_HEIGHT = "targetHeight";
    public static final String PARAM_HEIGHT_VARIATION = "heightVariation";
    public static final String PARAM_MODE = "mode";
    public static final String PARAM_SEED = "seed";

    private static final int DEFAULT_PIXEL_COUNT = 100;
    private static final int DEFAULT_TARGET_HEIGHT = 64;
    private static final int DEFAULT_HEIGHT_VARIATION = 5;
    private static final String DEFAULT_MODE = "set";

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void manipulate(WFlat flat, int x, int z, int sizeX, int sizeZ,
                          Map<String, String> parameters) {
        log.debug("Starting random pixels manipulation: region=({},{},{},{})", x, z, sizeX, sizeZ);

        // Parse parameters
        int pixelCount = parseIntParameter(parameters, PARAM_PIXEL_COUNT, DEFAULT_PIXEL_COUNT);
        int targetHeight = parseIntParameter(parameters, PARAM_TARGET_HEIGHT, DEFAULT_TARGET_HEIGHT);
        int variation = parseIntParameter(parameters, PARAM_HEIGHT_VARIATION, DEFAULT_HEIGHT_VARIATION);
        String mode = parameters.getOrDefault(PARAM_MODE, DEFAULT_MODE);
        long seed = parseLongParameter(parameters, PARAM_SEED, System.currentTimeMillis());

        // Validate and clamp parameters
        int maxPixels = sizeX * sizeZ;
        pixelCount = Math.max(1, Math.min(maxPixels, pixelCount));
        targetHeight = Math.max(0, Math.min(255, targetHeight));
        variation = Math.max(0, Math.min(50, variation));

        // Initialize random generator
        Random random = new Random(seed);

        // Setup FlatPainter
        FlatPainter painter = new FlatPainter(flat);

        // Select painter based on mode
        FlatPainter.Painter selectedPainter;
        switch (mode.toLowerCase()) {
            case "add":
                selectedPainter = FlatPainter.ADDITIVE;
                break;
            case "higher":
                selectedPainter = FlatPainter.HIGHER;
                break;
            case "lower":
                selectedPainter = FlatPainter.LOWER;
                break;
            case "set":
            default:
                selectedPainter = FlatPainter.DEFAULT_PAINTER;
                break;
        }

        // Set random pixels
        for (int i = 0; i < pixelCount; i++) {
            // Random position within region
            int localX = random.nextInt(sizeX);
            int localZ = random.nextInt(sizeZ);
            int xi = x + localX;
            int zi = z + localZ;

            // Calculate height with Gaussian variation
            int height = targetHeight + (int) (random.nextGaussian() * variation);

            // Clamp to valid range
            height = Math.max(0, Math.min(255, height));

            // Apply pixel
            painter.paint(xi, zi, height, selectedPainter);
        }

        log.info("Random pixels manipulation completed: pixelCount={}, targetHeight={}, variation={}, mode={}",
                pixelCount, targetHeight, variation, mode);
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
