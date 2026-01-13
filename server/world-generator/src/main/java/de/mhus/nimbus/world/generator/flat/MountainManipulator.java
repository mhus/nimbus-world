package de.mhus.nimbus.world.generator.flat;

import de.mhus.nimbus.world.shared.generator.WFlat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Random;

/**
 * Mountain manipulator.
 * Creates fractal mountain ranges using recursive midpoint displacement technique.
 * Based on ShapeFactory.cpp Case 13-16 + doMountain().
 * <p>
 * Parameters:
 * - peakHeight: Height of mountain peak above baseHeight (default: 100, range: 10-150)
 * - baseHeight: Starting height for mountain base (default: 64, range: 0-200)
 * - seed: Random seed for reproducibility (default: System.currentTimeMillis())
 * - branches: Number of child branches (default: 3, range: 1-5)
 * - roughness: Height variation factor (default: 0.5, range: 0.0-1.0)
 * - direction: Starting direction - "center", "corner", "left-right", "top-bottom" (default: "center")
 */
@Component
@Slf4j
public class MountainManipulator implements FlatManipulator {

    public static final String NAME = "mountain";
    public static final String PARAM_PEAK_HEIGHT = "peakHeight";
    public static final String PARAM_BASE_HEIGHT = "baseHeight";
    public static final String PARAM_SEED = "seed";
    public static final String PARAM_BRANCHES = "branches";
    public static final String PARAM_ROUGHNESS = "roughness";
    public static final String PARAM_DIRECTION = "direction";

    private static final int DEFAULT_PEAK_HEIGHT = 100;
    private static final int DEFAULT_BASE_HEIGHT = 64;
    private static final int DEFAULT_BRANCHES = 3;
    private static final double DEFAULT_ROUGHNESS = 0.5;
    private static final String DEFAULT_DIRECTION = "center";
    private static final int MAX_RECURSION_DEPTH = 8;
    private static final int MIN_HEIGHT = 5;

    private Random random;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void manipulate(WFlat flat, int x, int z, int sizeX, int sizeZ,
                          Map<String, String> parameters) {
        log.debug("Starting mountain manipulation: region=({},{},{},{})", x, z, sizeX, sizeZ);

        // Parse parameters
        int peakHeight = parseIntParameter(parameters, PARAM_PEAK_HEIGHT, DEFAULT_PEAK_HEIGHT);
        int baseHeight = parseIntParameter(parameters, PARAM_BASE_HEIGHT, DEFAULT_BASE_HEIGHT);
        long seed = parseLongParameter(parameters, PARAM_SEED, System.currentTimeMillis());
        int branches = parseIntParameter(parameters, PARAM_BRANCHES, DEFAULT_BRANCHES);
        double roughness = parseDoubleParameter(parameters, PARAM_ROUGHNESS, DEFAULT_ROUGHNESS);
        String direction = parameters.getOrDefault(PARAM_DIRECTION, DEFAULT_DIRECTION);

        // Validate and clamp parameters
        peakHeight = Math.max(10, Math.min(150, peakHeight));
        baseHeight = Math.max(0, Math.min(200, baseHeight));
        branches = Math.max(1, Math.min(5, branches));
        roughness = Math.max(0.0, Math.min(1.0, roughness));

        // Initialize random generator
        random = new Random(seed);

        // Setup FlatPainter
        FlatPainter painter = new FlatPainter(flat);

        // Determine start and end points based on direction
        int startX, startZ, endX, endZ;
        switch (direction.toLowerCase()) {
            case "corner":
                startX = x;
                startZ = z;
                endX = x + sizeX - 1;
                endZ = z + sizeZ - 1;
                break;
            case "left-right":
                startX = x;
                startZ = z + sizeZ / 2;
                endX = x + sizeX - 1;
                endZ = z + sizeZ / 2;
                break;
            case "top-bottom":
                startX = x + sizeX / 2;
                startZ = z;
                endX = x + sizeX / 2;
                endZ = z + sizeZ - 1;
                break;
            case "center":
            default:
                startX = x + sizeX / 2;
                startZ = z + sizeZ / 2;
                // Random end point for more natural appearance
                endX = x + random.nextInt(sizeX);
                endZ = z + random.nextInt(sizeZ);
                break;
        }

        // Recursive fractal mountain generation
        doMountain(painter, startX, startZ, endX, endZ,
                  baseHeight, peakHeight, branches, roughness, 0);

        // Apply smoothing to blend with existing terrain
        painter.soften(x, z, x + sizeX - 1, z + sizeZ - 1, 0.3);

        log.info("Mountain manipulation completed: peakHeight={}, baseHeight={}, branches={}, roughness={}, direction={}",
                peakHeight, baseHeight, branches, roughness, direction);
    }

    /**
     * Recursive mountain generation using midpoint displacement.
     * Creates natural-looking mountain ridges with branching.
     *
     * @param painter FlatPainter for drawing
     * @param x1 Start X coordinate
     * @param z1 Start Z coordinate
     * @param x2 End X coordinate
     * @param z2 End Z coordinate
     * @param baseHeight Base height level
     * @param height Current height above base
     * @param childBranches Number of child branches to create
     * @param roughness Variation factor for randomness
     * @param depth Current recursion depth
     */
    private void doMountain(FlatPainter painter, int x1, int z1, int x2, int z2,
                           int baseHeight, int height, int childBranches,
                           double roughness, int depth) {
        // Termination conditions
        if (depth > MAX_RECURSION_DEPTH || height < MIN_HEIGHT) {
            return;
        }

        // Calculate midpoint
        int midX = (x1 + x2) / 2;
        int midZ = (z1 + z2) / 2;

        // Random displacement at midpoint using Gaussian distribution
        // This creates more natural variation than uniform random
        int displacement = (int) (random.nextGaussian() * height * roughness);
        int midHeight = baseHeight + height / 2 + displacement;

        // Clamp height to valid range
        midHeight = Math.max(0, Math.min(255, midHeight));

        // Draw mountain ridge from start to midpoint using HIGHER painter
        // This ensures we only raise terrain, never lower it
        painter.line(x1, z1, midX, midZ, midHeight, FlatPainter.HIGHER);

        // Create child branches radiating from midpoint
        for (int i = 0; i < childBranches; i++) {
            // Random direction variation for each branch
            // Creates more organic, tree-like structure
            int angleVariation = random.nextInt(height) - height / 2;
            int branchX = midX + angleVariation;
            int branchZ = midZ + angleVariation;

            // Recursive call with reduced height and fewer branches
            doMountain(painter, midX, midZ, branchX, branchZ,
                      midHeight, height / 2,
                      Math.max(1, childBranches - 1),
                      roughness, depth + 1);
        }

        // Continue main ridge if we haven't reached the end
        if (x1 != x2 || z1 != z2) {
            doMountain(painter, midX, midZ, x2, z2,
                      midHeight, height / 2,
                      childBranches, roughness, depth + 1);
        }
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
