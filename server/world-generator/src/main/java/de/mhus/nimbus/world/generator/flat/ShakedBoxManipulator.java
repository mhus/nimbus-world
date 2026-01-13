package de.mhus.nimbus.world.generator.flat;

import de.mhus.nimbus.world.shared.generator.WFlat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Random;

/**
 * Shaked box manipulator.
 * Creates rectangles with randomly removed pixels at borders for natural edges.
 * Based on ShapeFactory.cpp Case 2.
 * <p>
 * Parameters:
 * - borderWidth: Width of border area (default: 3, range: 1-10, -1 = entire area)
 * - probability: Probability that pixel is removed (default: 0.5, range: 0.0-1.0)
 * - targetHeight: Height for non-removed pixels (default: 64, range: 0-255)
 * - seed: Random seed (default: System.currentTimeMillis())
 */
@Component
@Slf4j
public class ShakedBoxManipulator implements FlatManipulator {

    public static final String NAME = "shaked-box";
    public static final String PARAM_BORDER_WIDTH = "borderWidth";
    public static final String PARAM_PROBABILITY = "probability";
    public static final String PARAM_TARGET_HEIGHT = "targetHeight";
    public static final String PARAM_SEED = "seed";

    private static final int DEFAULT_BORDER_WIDTH = 3;
    private static final double DEFAULT_PROBABILITY = 0.5;
    private static final int DEFAULT_TARGET_HEIGHT = 64;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void manipulate(WFlat flat, int x, int z, int sizeX, int sizeZ,
                          Map<String, String> parameters) {
        log.debug("Starting shaked box manipulation: region=({},{},{},{})", x, z, sizeX, sizeZ);

        // Parse parameters
        int borderWidth = parseIntParameter(parameters, PARAM_BORDER_WIDTH, DEFAULT_BORDER_WIDTH);
        double probability = parseDoubleParameter(parameters, PARAM_PROBABILITY, DEFAULT_PROBABILITY);
        int targetHeight = parseIntParameter(parameters, PARAM_TARGET_HEIGHT, DEFAULT_TARGET_HEIGHT);
        long seed = parseLongParameter(parameters, PARAM_SEED, System.currentTimeMillis());

        // Validate and clamp parameters
        if (borderWidth != -1) {
            borderWidth = Math.max(1, Math.min(10, borderWidth));
        }
        probability = Math.max(0.0, Math.min(1.0, probability));
        targetHeight = Math.max(0, Math.min(255, targetHeight));

        // Initialize random generator
        Random random = new Random(seed);

        // Setup FlatPainter
        FlatPainter painter = new FlatPainter(flat);

        // Iterate over region
        int pixelsSet = 0;
        int pixelsSkipped = 0;

        for (int localZ = 0; localZ < sizeZ; localZ++) {
            for (int localX = 0; localX < sizeX; localX++) {
                int xi = x + localX;
                int zi = z + localZ;

                // Check if pixel is in border area
                boolean inBorder;
                if (borderWidth == -1) {
                    // Entire area is "border"
                    inBorder = true;
                } else {
                    // Border area: pixels within borderWidth of edges
                    inBorder = (localX < borderWidth) ||
                              (localZ < borderWidth) ||
                              (localX >= sizeX - borderWidth) ||
                              (localZ >= sizeZ - borderWidth);
                }

                if (inBorder) {
                    // Random check: skip pixel with given probability
                    if (random.nextDouble() < probability) {
                        // Skip this pixel (don't modify it)
                        pixelsSkipped++;
                        continue;
                    }
                }

                // Set pixel to target height
                painter.paint(xi, zi, targetHeight, FlatPainter.DEFAULT_PAINTER);
                pixelsSet++;
            }
        }

        log.info("Shaked box manipulation completed: borderWidth={}, probability={}, targetHeight={}, pixelsSet={}, pixelsSkipped={}",
                borderWidth, probability, targetHeight, pixelsSet, pixelsSkipped);
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
