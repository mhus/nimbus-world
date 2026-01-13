package de.mhus.nimbus.world.generator.flat;

import de.mhus.nimbus.world.shared.generator.WFlat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Random;

/**
 * Islands manipulator.
 * Creates a main island with multiple smaller islands for archipelago generation.
 * Based on ShapeFactory.cpp Case 45.
 * <p>
 * Parameters:
 * - mainIslandSize: Radius of main island (default: 40, range: 10-min(sizeX,sizeZ)/2)
 * - mainIslandHeight: Peak height above ocean (default: 30, range: 5-100)
 * - smallIslands: Number of small islands (default: 8, range: 3-20)
 * - smallIslandMinRadius: Min radius of small islands (default: 8, range: 3-mainSize/2)
 * - smallIslandMaxRadius: Max radius of small islands (default: 15, range: smallMinRadius-mainSize)
 * - scatterDistance: How far small islands scatter (default: 60, range: mainSize-sizeX)
 * - seed: Random seed (default: System.currentTimeMillis())
 * - underwater: Create underwater mountains instead (default: false)
 */
@Component
@Slf4j
public class IslandsManipulator implements FlatManipulator {

    public static final String NAME = "islands";
    public static final String PARAM_MAIN_ISLAND_SIZE = "mainIslandSize";
    public static final String PARAM_MAIN_ISLAND_HEIGHT = "mainIslandHeight";
    public static final String PARAM_SMALL_ISLANDS = "smallIslands";
    public static final String PARAM_SMALL_ISLAND_MIN_RADIUS = "smallIslandMinRadius";
    public static final String PARAM_SMALL_ISLAND_MAX_RADIUS = "smallIslandMaxRadius";
    public static final String PARAM_SCATTER_DISTANCE = "scatterDistance";
    public static final String PARAM_SEED = "seed";
    public static final String PARAM_UNDERWATER = "underwater";

    private static final int DEFAULT_MAIN_SIZE = 40;
    private static final int DEFAULT_MAIN_HEIGHT = 30;
    private static final int DEFAULT_SMALL_COUNT = 8;
    private static final int DEFAULT_SMALL_MIN_RADIUS = 8;
    private static final int DEFAULT_SMALL_MAX_RADIUS = 15;
    private static final int DEFAULT_SCATTER_DISTANCE = 60;
    private static final double FALLOFF_FACTOR = -2.0;
    private static final int RANDOM_VARIATION = 3;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void manipulate(WFlat flat, int x, int z, int sizeX, int sizeZ,
                          Map<String, String> parameters) {
        log.debug("Starting islands manipulation: region=({},{},{},{})", x, z, sizeX, sizeZ);

        // Parse parameters
        int mainSize = parseIntParameter(parameters, PARAM_MAIN_ISLAND_SIZE, DEFAULT_MAIN_SIZE);
        int mainHeight = parseIntParameter(parameters, PARAM_MAIN_ISLAND_HEIGHT, DEFAULT_MAIN_HEIGHT);
        int smallCount = parseIntParameter(parameters, PARAM_SMALL_ISLANDS, DEFAULT_SMALL_COUNT);
        int smallMinRadius = parseIntParameter(parameters, PARAM_SMALL_ISLAND_MIN_RADIUS, DEFAULT_SMALL_MIN_RADIUS);
        int smallMaxRadius = parseIntParameter(parameters, PARAM_SMALL_ISLAND_MAX_RADIUS, DEFAULT_SMALL_MAX_RADIUS);
        int scatterDist = parseIntParameter(parameters, PARAM_SCATTER_DISTANCE, DEFAULT_SCATTER_DISTANCE);
        long seed = parseLongParameter(parameters, PARAM_SEED, System.currentTimeMillis());
        boolean underwater = parseBooleanParameter(parameters, PARAM_UNDERWATER, false);

        // Validate and clamp parameters
        int maxRadius = Math.min(sizeX, sizeZ) / 2;
        mainSize = Math.max(10, Math.min(maxRadius, mainSize));
        mainHeight = Math.max(5, Math.min(100, mainHeight));
        smallCount = Math.max(3, Math.min(20, smallCount));
        smallMinRadius = Math.max(3, Math.min(mainSize / 2, smallMinRadius));
        smallMaxRadius = Math.max(smallMinRadius, Math.min(mainSize, smallMaxRadius));
        scatterDist = Math.max(mainSize, Math.min(sizeX, scatterDist));

        // Initialize random generator
        Random random = new Random(seed);

        // Setup FlatPainter
        FlatPainter painter = new FlatPainter(flat);

        // Get ocean level
        int oceanLevel = flat.getOceanLevel();

        // Calculate center coordinates
        int centerX = x + sizeX / 2;
        int centerZ = z + sizeZ / 2;

        // Calculate base and target heights
        int baseLevel = underwater ? oceanLevel - mainHeight : oceanLevel;
        int targetHeight = underwater ? oceanLevel - 5 : oceanLevel + mainHeight;

        // Draw main island
        drawIsland(painter, flat, centerX, centerZ, mainSize, baseLevel, targetHeight);

        // Draw small islands scattered around
        for (int i = 0; i < smallCount; i++) {
            // Use polar coordinates for natural scattering
            double angle = random.nextDouble() * 2 * Math.PI;
            int distance = mainSize + random.nextInt(scatterDist);
            int smallX = centerX + (int) (Math.cos(angle) * distance);
            int smallZ = centerZ + (int) (Math.sin(angle) * distance);

            // Check bounds
            if (smallX < x || smallX >= x + sizeX || smallZ < z || smallZ >= z + sizeZ) {
                continue;
            }

            // Random radius and height for each small island
            int smallRadius = smallMinRadius + random.nextInt(smallMaxRadius - smallMinRadius + 1);
            int smallHeight = (mainHeight / 3) + random.nextInt(mainHeight / 2);
            int smallTargetHeight = underwater ?
                                  oceanLevel - smallHeight :
                                  oceanLevel + smallHeight;

            drawIsland(painter, flat, smallX, smallZ, smallRadius, baseLevel, smallTargetHeight);
        }

        // Apply random variation for natural texture
        painter.setPainter(FlatPainter.RANDOM_MODIFIER);
        painter.fillRectangle(x, z, x + sizeX - 1, z + sizeZ - 1, RANDOM_VARIATION);

        // Smooth for natural appearance
        painter.soften(x, z, x + sizeX - 1, z + sizeZ - 1, 0.4);

        // Set materials based on height vs ocean level
        for (int localZ = 0; localZ < sizeZ; localZ++) {
            for (int localX = 0; localX < sizeX; localX++) {
                int xi = x + localX;
                int zi = z + localZ;
                int level = flat.getLevel(xi, zi);

                if (level <= oceanLevel) {
                    flat.setColumn(xi, zi, FlatMaterialService.SAND);
                } else {
                    flat.setColumn(xi, zi, FlatMaterialService.GRASS);
                }
            }
        }

        log.info("Islands manipulation completed: mainSize={}, mainHeight={}, smallIslands={}, underwater={}",
                mainSize, mainHeight, smallCount, underwater);
    }

    /**
     * Draw a single island with exponential height falloff.
     *
     * @param painter FlatPainter instance
     * @param flat WFlat instance
     * @param centerX Center X coordinate
     * @param centerZ Center Z coordinate
     * @param radius Island radius
     * @param baseLevel Base height level
     * @param peakHeight Peak height
     */
    private void drawIsland(FlatPainter painter, WFlat flat,
                           int centerX, int centerZ, int radius,
                           int baseLevel, int peakHeight) {
        // Exponential falloff from center to edges
        // Formula: height = baseLevel + (peakHeight - baseLevel) * exp(-2.0 * distance / radius)
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                double distance = Math.sqrt(dx * dx + dz * dz);

                if (distance <= radius) {
                    int xi = centerX + dx;
                    int zi = centerZ + dz;

                    // Exponential height falloff creates natural island shape
                    double heightFactor = Math.exp(FALLOFF_FACTOR * distance / radius);
                    int height = baseLevel + (int) ((peakHeight - baseLevel) * heightFactor);

                    // Clamp to valid range
                    height = Math.max(0, Math.min(255, height));

                    // Use HIGHER painter to only raise terrain (for islands)
                    painter.paint(xi, zi, height, FlatPainter.HIGHER);
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

    private boolean parseBooleanParameter(Map<String, String> parameters, String name, boolean defaultValue) {
        if (parameters == null || !parameters.containsKey(name)) {
            return defaultValue;
        }
        String value = parameters.get(name).toLowerCase();
        return "true".equals(value) || "1".equals(value) || "yes".equals(value);
    }
}
