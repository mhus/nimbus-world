package de.mhus.nimbus.world.generator.flat;

import de.mhus.nimbus.world.shared.generator.WFlat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Random;

/**
 * Lakes manipulator.
 * Creates a main lake with multiple smaller lakes using quadratic depression.
 * Based on ShapeFactory.cpp Case 46 + Case 42.
 * <p>
 * Parameters:
 * - mainLakeRadius: Radius of main lake (default: 35, range: 10-min(sizeX,sizeZ)/2)
 * - mainLakeDepth: Depth below ocean level (default: 25, range: 5-50)
 * - smallLakes: Number of small lakes (default: 6, range: 2-15)
 * - smallLakeMinRadius: Min radius of small lakes (default: 8, range: 5-mainRadius/2)
 * - smallLakeMaxRadius: Max radius of small lakes (default: 15, range: smallMinRadius-mainRadius)
 * - scatterDistance: How far small lakes scatter (default: 50, range: mainRadius-sizeX)
 * - seed: Random seed (default: System.currentTimeMillis())
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

        // Get ocean level
        int oceanLevel = flat.getOceanLevel();

        // Calculate center coordinates
        int centerX = x + sizeX / 2;
        int centerZ = z + sizeZ / 2;

        // Draw main lake with quadratic depression
        drawLake(painter, flat, centerX, centerZ, mainRadius, mainDepth, oceanLevel);

        // Draw small lakes scattered around
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

            drawLake(painter, flat, lakeX, lakeZ, smallRadius, smallDepth, oceanLevel);
        }

        // Smooth lake edges for natural appearance
        painter.soften(x, z, x + sizeX - 1, z + sizeZ - 1, 0.5);

        // Set water material for areas below ocean level
        for (int localZ = 0; localZ < sizeZ; localZ++) {
            for (int localX = 0; localX < sizeX; localX++) {
                int xi = x + localX;
                int zi = z + localZ;
                int level = flat.getLevel(xi, zi);

                if (level <= oceanLevel) {
                    flat.setColumn(xi, zi, FlatMaterialService.SAND);
                }
            }
        }

        log.info("Lakes manipulation completed: mainRadius={}, mainDepth={}, smallLakes={}",
                mainRadius, mainDepth, smallCount);
    }

    /**
     * Draw a single lake with quadratic depth falloff.
     * Depression is deeper in center, gradually becoming shallower towards edges.
     *
     * @param painter FlatPainter instance
     * @param flat WFlat instance
     * @param centerX Center X coordinate
     * @param centerZ Center Z coordinate
     * @param radius Lake radius
     * @param depth Maximum depth below ocean level
     * @param oceanLevel Ocean level
     */
    private void drawLake(FlatPainter painter, WFlat flat,
                         int centerX, int centerZ, int radius,
                         int depth, int oceanLevel) {
        // Quadratic falloff - depth decreases with square of distance
        // Formula: lakeLevel = oceanLevel - depth * (1 - distance/radius)^2
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                double distance = Math.sqrt(dx * dx + dz * dz);

                if (distance <= radius) {
                    int xi = centerX + dx;
                    int zi = centerZ + dz;

                    // Quadratic falloff: depth is maximum at center, zero at edge
                    double depthFactor = Math.pow(1.0 - distance / radius, 2);
                    int lakeLevel = oceanLevel - (int) (depth * depthFactor);

                    // Clamp to valid range
                    lakeLevel = Math.max(0, Math.min(255, lakeLevel));

                    // Use LOWER painter to only lower terrain (create depression)
                    painter.paint(xi, zi, lakeLevel, FlatPainter.LOWER);
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
