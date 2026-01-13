package de.mhus.nimbus.world.generator.flat;

import de.mhus.nimbus.world.shared.generator.WFlat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Random;

/**
 * Crater manipulator.
 * Creates circular craters with raised rims and optional smaller craters inside.
 * Based on ShapeFactory.cpp Case 44.
 * <p>
 * Parameters:
 * - centerX: Relative X position of crater center (default: sizeX/2)
 * - centerZ: Relative Z position (default: sizeZ/2)
 * - outerRadius: Outer radius of crater rim (default: 30, range: 5-min(sizeX,sizeZ)/2)
 * - innerRadius: Inner radius of depression (default: 20, range: 3-outerRadius-2)
 * - rimHeight: Height of rim above surroundings (default: 15, range: 5-50)
 * - depth: Depth below surroundings (default: 20, range: 5-50)
 * - smallCraters: Number of small craters inside (default: 5, range: 0-20)
 * - seed: Random seed (default: System.currentTimeMillis())
 */
@Component
@Slf4j
public class CraterManipulator implements FlatManipulator {

    public static final String NAME = "crater";
    public static final String PARAM_CENTER_X = "centerX";
    public static final String PARAM_CENTER_Z = "centerZ";
    public static final String PARAM_OUTER_RADIUS = "outerRadius";
    public static final String PARAM_INNER_RADIUS = "innerRadius";
    public static final String PARAM_RIM_HEIGHT = "rimHeight";
    public static final String PARAM_DEPTH = "depth";
    public static final String PARAM_SMALL_CRATERS = "smallCraters";
    public static final String PARAM_SEED = "seed";

    private static final int DEFAULT_OUTER_RADIUS = 30;
    private static final int DEFAULT_INNER_RADIUS = 20;
    private static final int DEFAULT_RIM_HEIGHT = 15;
    private static final int DEFAULT_DEPTH = 20;
    private static final int DEFAULT_SMALL_CRATERS = 5;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void manipulate(WFlat flat, int x, int z, int sizeX, int sizeZ,
                          Map<String, String> parameters) {
        log.debug("Starting crater manipulation: region=({},{},{},{})", x, z, sizeX, sizeZ);

        // Parse parameters
        int centerX = parseIntParameter(parameters, PARAM_CENTER_X, sizeX / 2);
        int centerZ = parseIntParameter(parameters, PARAM_CENTER_Z, sizeZ / 2);
        int outerRadius = parseIntParameter(parameters, PARAM_OUTER_RADIUS, DEFAULT_OUTER_RADIUS);
        int innerRadius = parseIntParameter(parameters, PARAM_INNER_RADIUS, DEFAULT_INNER_RADIUS);
        int rimHeight = parseIntParameter(parameters, PARAM_RIM_HEIGHT, DEFAULT_RIM_HEIGHT);
        int depth = parseIntParameter(parameters, PARAM_DEPTH, DEFAULT_DEPTH);
        int smallCraters = parseIntParameter(parameters, PARAM_SMALL_CRATERS, DEFAULT_SMALL_CRATERS);
        long seed = parseLongParameter(parameters, PARAM_SEED, System.currentTimeMillis());

        // Validate and clamp parameters
        int maxRadius = Math.min(sizeX, sizeZ) / 2;
        outerRadius = Math.max(5, Math.min(maxRadius, outerRadius));
        innerRadius = Math.max(3, Math.min(outerRadius - 2, innerRadius));
        rimHeight = Math.max(5, Math.min(50, rimHeight));
        depth = Math.max(5, Math.min(50, depth));
        smallCraters = Math.max(0, Math.min(20, smallCraters));

        // Initialize random generator
        Random random = new Random(seed);

        // Setup FlatPainter
        FlatPainter painter = new FlatPainter(flat);

        // Calculate absolute center coordinates
        int absoluteCenterX = x + centerX;
        int absoluteCenterZ = z + centerZ;

        // Get base level at center
        int baseLevel = flat.getLevel(absoluteCenterX, absoluteCenterZ);

        // Draw main crater
        drawCrater(painter, flat, absoluteCenterX, absoluteCenterZ,
                  outerRadius, innerRadius, rimHeight, depth, baseLevel);

        // Draw small craters inside
        for (int i = 0; i < smallCraters; i++) {
            // Random position within inner radius (with some margin)
            double angle = random.nextDouble() * 2 * Math.PI;
            int distance = random.nextInt(Math.max(1, innerRadius - 5));
            int smallX = absoluteCenterX + (int) (Math.cos(angle) * distance);
            int smallZ = absoluteCenterZ + (int) (Math.sin(angle) * distance);

            // Random radius for small crater (2-7)
            int smallRadius = 2 + random.nextInt(6);

            // Draw small crater with reduced height and depth
            drawCrater(painter, flat, smallX, smallZ,
                      smallRadius + 1, smallRadius, 3, 5, baseLevel);
        }

        // Smooth edges for natural appearance
        painter.soften(x, z, x + sizeX - 1, z + sizeZ - 1, 0.2);

        log.info("Crater manipulation completed: outerRadius={}, innerRadius={}, rimHeight={}, depth={}, smallCraters={}",
                outerRadius, innerRadius, rimHeight, depth, smallCraters);
    }

    /**
     * Draw a single crater with raised rim and depression.
     *
     * @param painter FlatPainter instance
     * @param flat WFlat instance
     * @param centerX Center X coordinate
     * @param centerZ Center Z coordinate
     * @param outerRadius Outer radius (rim)
     * @param innerRadius Inner radius (depression)
     * @param rimHeight Height of rim above base
     * @param depth Depth of depression below base
     * @param baseLevel Base height level
     */
    private void drawCrater(FlatPainter painter, WFlat flat,
                           int centerX, int centerZ, int outerRadius, int innerRadius,
                           int rimHeight, int depth, int baseLevel) {
        // Draw crater in two zones: rim (elevated) and inner (depressed)
        for (int dz = -outerRadius; dz <= outerRadius; dz++) {
            for (int dx = -outerRadius; dx <= outerRadius; dx++) {
                double distance = Math.sqrt(dx * dx + dz * dz);

                if (distance <= outerRadius) {
                    int xi = centerX + dx;
                    int zi = centerZ + dz;

                    int newLevel;
                    if (distance >= innerRadius) {
                        // Rim area: elevated, linear interpolation from inner to outer
                        // Height decreases from innerRadius (max) to outerRadius (base)
                        double rimFactor = (outerRadius - distance) / (outerRadius - innerRadius);
                        newLevel = baseLevel + (int) (rimHeight * rimFactor);

                        // Clamp and apply using HIGHER (only raise terrain)
                        newLevel = Math.max(0, Math.min(255, newLevel));
                        painter.paint(xi, zi, newLevel, FlatPainter.HIGHER);
                    } else {
                        // Inner area: depressed, quadratic falloff
                        // Depth is maximum at center, zero at innerRadius
                        double depthFactor = Math.pow(distance / innerRadius, 2);
                        newLevel = baseLevel - (int) (depth * (1 - depthFactor));

                        // Clamp and apply using LOWER (only lower terrain)
                        newLevel = Math.max(0, Math.min(255, newLevel));
                        painter.paint(xi, zi, newLevel, FlatPainter.LOWER);
                    }
                }
            }
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
