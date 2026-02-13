package de.mhus.nimbus.world.generator.flat.manipulator;

import de.mhus.nimbus.world.generator.flat.FlatManipulator;
import de.mhus.nimbus.world.generator.flat.FlatMaterialService;
import de.mhus.nimbus.world.generator.flat.FlatPainter;
import de.mhus.nimbus.world.shared.generator.WFlat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Random;

/**
 * Lakes manipulator.
 * Creates a main lake with multiple smaller lakes on the existing terrain.
 * Lakes are placed at the lowest point in their area and extend downward.
 * Water blocks (extra blocks) are placed one level below the water surface.
 * Based on ShapeFactory.cpp Case 46 + Case 42.
 * <p>
 * Parameters:
 * - mainLakeRadius: Radius of main lake (default: 35, range: 10-min(sizeX,sizeZ)/2)
 * - mainLakeDepth: Depth of lake depression (default: 25, range: 5-50)
 * - smallLakes: Number of small lakes (default: 6, range: 2-15)
 * - smallLakeMinRadius: Min radius of small lakes (default: 8, range: 5-mainRadius/2)
 * - smallLakeMaxRadius: Max radius of small lakes (default: 15, range: smallMinRadius-mainRadius)
 * - scatterDistance: How far small lakes scatter (default: 50, range: mainRadius-sizeX)
 * - seed: Random seed (default: System.currentTimeMillis())
 *
 * Lake placement rules:
 * - Lakes are never below sea level (if placement would be below seaLevel, lake is skipped)
 * - Lake surface is at the lowest point within the lake area
 * - Lake extends at least 1 block down from surface for water volume
 * - Water extra blocks are placed one level below the lake surface
 */
@Component
@Slf4j
public class LakesManipulator implements FlatManipulator {

    public static final String NAME = "lakes";
    public static final String PARAM_MAIN_LAKE_RADIUS = "mainLakeRadius";
    public static final String PARAM_MAIN_LAKE_DEPTH = "mainLakeDepth";
    public static final String PARAM_SMALL_LAKES = "smallLakes";
    public static final String PARAM_SMALL_LAKE_MIN_RADIUS = "smallLakeMinRadius";
    public static final String PARAM_SMALL_LAKE_MAX_RADIUS = "smallLakeMaxRadius";
    public static final String PARAM_SCATTER_DISTANCE = "scatterDistance";
    public static final String PARAM_SEED = "seed";

    private static final int DEFAULT_MAIN_RADIUS = 35;
    private static final int DEFAULT_MAIN_DEPTH = 25;
    private static final int DEFAULT_SMALL_COUNT = 6;
    private static final int DEFAULT_SMALL_MIN_RADIUS = 8;
    private static final int DEFAULT_SMALL_MAX_RADIUS = 15;
    private static final int DEFAULT_SCATTER_DISTANCE = 50;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void manipulate(WFlat flat, int x, int z, int sizeX, int sizeZ,
                          Map<String, String> parameters) {
        log.debug("Starting lakes manipulation: region=({},{},{},{})", x, z, sizeX, sizeZ);

        // Parse parameters
        int mainRadius = parseIntParameter(parameters, PARAM_MAIN_LAKE_RADIUS, DEFAULT_MAIN_RADIUS);
        int mainDepth = parseIntParameter(parameters, PARAM_MAIN_LAKE_DEPTH, DEFAULT_MAIN_DEPTH);
        int smallCount = parseIntParameter(parameters, PARAM_SMALL_LAKES, DEFAULT_SMALL_COUNT);
        int smallMinRadius = parseIntParameter(parameters, PARAM_SMALL_LAKE_MIN_RADIUS, DEFAULT_SMALL_MIN_RADIUS);
        int smallMaxRadius = parseIntParameter(parameters, PARAM_SMALL_LAKE_MAX_RADIUS, DEFAULT_SMALL_MAX_RADIUS);
        int scatterDist = parseIntParameter(parameters, PARAM_SCATTER_DISTANCE, DEFAULT_SCATTER_DISTANCE);
        long seed = parseLongParameter(parameters, PARAM_SEED, System.currentTimeMillis());

        // Validate and clamp parameters
        int maxRadius = Math.min(sizeX, sizeZ) / 2;
        mainRadius = Math.max(10, Math.min(maxRadius, mainRadius));
        mainDepth = Math.max(5, Math.min(50, mainDepth));
        smallCount = Math.max(2, Math.min(15, smallCount));
        smallMinRadius = Math.max(5, Math.min(mainRadius / 2, smallMinRadius));
        smallMaxRadius = Math.max(smallMinRadius, Math.min(mainRadius, smallMaxRadius));
        scatterDist = Math.max(mainRadius, Math.min(sizeX, scatterDist));

        // Initialize random generator
        Random random = new Random(seed);

        // Setup FlatPainter
        FlatPainter painter = new FlatPainter(flat);

        // Get sea level
        int seaLevel = flat.getSeaLevel();

        // Calculate center coordinates
        int centerX = x + sizeX / 2;
        int centerZ = z + sizeZ / 2;

        // Draw main lake with new terrain-based logic
        boolean mainLakeCreated = drawLake(painter, flat, centerX, centerZ, mainRadius, mainDepth, seaLevel);

        if (mainLakeCreated) {
            log.debug("Main lake created at ({}, {})", centerX, centerZ);
        } else {
            log.debug("Main lake skipped (would be below sea level)");
        }

        // Draw small lakes scattered around
        int smallLakesCreated = 0;
        for (int i = 0; i < smallCount; i++) {
            // Use polar coordinates for natural scattering
            double angle = random.nextDouble() * 2 * Math.PI;
            int distance = mainRadius + random.nextInt(scatterDist);
            int lakeX = centerX + (int) (Math.cos(angle) * distance);
            int lakeZ = centerZ + (int) (Math.sin(angle) * distance);

            // Check bounds
            if (lakeX < x || lakeX >= x + sizeX || lakeZ < z || lakeZ >= z + sizeZ) {
                continue;
            }

            // Random radius and depth for each small lake
            int smallRadius = smallMinRadius + random.nextInt(smallMaxRadius - smallMinRadius + 1);
            int smallDepth = (mainDepth / 2) + random.nextInt(mainDepth / 3);

            boolean created = drawLake(painter, flat, lakeX, lakeZ, smallRadius, smallDepth, seaLevel);
            if (created) {
                smallLakesCreated++;
            }
        }

        log.debug("Created {} small lakes out of {} attempts", smallLakesCreated, smallCount);

        // Smooth lake edges for natural appearance
        painter.soften(x, z, x + sizeX - 1, z + sizeZ - 1, 1, 0.5);

        log.info("Lakes manipulation completed: mainRadius={}, mainDepth={}, smallLakes created={}/{}, " +
                "mainLake={}", mainRadius, mainDepth, smallLakesCreated, smallCount,
                mainLakeCreated ? "created" : "skipped");
    }

    /**
     * Draw a single lake on existing terrain.
     *
     * Algorithm:
     * 1. Find the lowest point within the lake area (this becomes the water surface)
     * 2. Check if this point is above sea level (if not, skip lake)
     * 3. Create depression extending downward from the lowest point
     * 4. Place water extra blocks one level below the water surface
     *
     * @param painter FlatPainter instance
     * @param flat WFlat instance
     * @param centerX Center X coordinate
     * @param centerZ Center Z coordinate
     * @param radius Lake radius
     * @param depth Maximum depth of lake depression
     * @param seaLevel Sea level (lakes must be above this)
     * @return true if lake was created, false if skipped (would be below sea level)
     */
    private boolean drawLake(FlatPainter painter, WFlat flat,
                            int centerX, int centerZ, int radius,
                            int depth, int seaLevel) {

        // Step 1: Find the lowest point in the lake area
        int lowestLevel = Integer.MAX_VALUE;
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance <= radius) {
                    int xi = centerX + dx;
                    int zi = centerZ + dz;

                    // Check bounds
                    if (xi >= 0 && xi < flat.getSizeX() && zi >= 0 && zi < flat.getSizeZ()) {
                        int level = flat.getLevel(xi, zi);
                        lowestLevel = Math.min(lowestLevel, level);
                    }
                }
            }
        }

        // If no valid point found
        if (lowestLevel == Integer.MAX_VALUE) {
            return false;
        }

        // Step 2: Check if lowest point is above sea level
        if (lowestLevel <= seaLevel) {
            log.debug("Lake at ({}, {}) would be at or below sea level ({}), skipping",
                centerX, centerZ, lowestLevel);
            return false;
        }

        // The water surface will be at the lowest point
        int waterSurfaceLevel = lowestLevel;

        // Ensure we dig at least 1 block down for water volume
        int minLakeBottom = waterSurfaceLevel - 1;

        // Step 3: Create lake depression with quadratic falloff
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                double distance = Math.sqrt(dx * dx + dz * dz);

                if (distance <= radius) {
                    int xi = centerX + dx;
                    int zi = centerZ + dz;

                    // Check bounds
                    if (xi < 0 || xi >= flat.getSizeX() || zi < 0 || zi >= flat.getSizeZ()) {
                        continue;
                    }

                    // Quadratic falloff: depth is maximum at center, zero at edge
                    double depthFactor = Math.pow(1.0 - distance / radius, 2);
                    int targetDepth = (int) (depth * depthFactor);

                    // Calculate target level (dig down from water surface)
                    int targetLevel = waterSurfaceLevel - targetDepth;

                    // Ensure at least 1 block deep in center
                    if (distance < radius * 0.3) {
                        targetLevel = Math.min(targetLevel, minLakeBottom);
                    }

                    // Don't go below sea level
                    targetLevel = Math.max(targetLevel, seaLevel + 1);

                    // Use LOWER painter to only lower terrain (create depression)
                    painter.paint(xi, zi, targetLevel, FlatPainter.LOWER);

                    // Set sand material for lake bottom
                    flat.setColumn(xi, zi, FlatMaterialService.SAND);
                }
            }
        }

        // Step 4: Place water extra blocks one level below water surface
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                double distance = Math.sqrt(dx * dx + dz * dz);

                if (distance <= radius) {
                    int xi = centerX + dx;
                    int zi = centerZ + dz;

                    // Check bounds
                    if (xi < 0 || xi >= flat.getSizeX() || zi < 0 || zi >= flat.getSizeZ()) {
                        continue;
                    }

                    // Check if this position is below water surface (i.e., is part of the lake)
                    int currentLevel = flat.getLevel(xi, zi);
                    if (currentLevel < waterSurfaceLevel) {
                        // Place water extra block at water surface level
                        flat.setExtraBlock(xi, zi, waterSurfaceLevel, "WATER");
                    }
                }
            }
        }

        log.debug("Created lake at ({}, {}) with surface level {} (radius: {}, depth: {})",
            centerX, centerZ, waterSurfaceLevel, radius, depth);

        return true;
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
