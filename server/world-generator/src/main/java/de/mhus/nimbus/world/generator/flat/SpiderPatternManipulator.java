package de.mhus.nimbus.world.generator.flat;

import de.mhus.nimbus.world.shared.generator.WFlat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Random;

/**
 * Spider pattern manipulator.
 * Creates recursive branching patterns radiating from center.
 * Useful for river systems, canyon networks, or erosion patterns.
 * Based on ShapeFactory.cpp Case 12 + doBackgroundSpider.
 * <p>
 * Parameters:
 * - centerX: Start X position (default: sizeX/2)
 * - centerZ: Start Z position (default: sizeZ/2)
 * - branches: Number of main branches (default: 6, range: 3-12)
 * - length: Length of main branches (default: 40, range: 10-min(sizeX,sizeZ)/2)
 * - heightDelta: Height change along branches (default: -10, range: -50 to 50)
 *   Negative=carve (rivers/canyons), Positive=raise (ridges)
 * - subBranches: Sub-branches per main branch (default: 3, range: 0-5)
 * - recursionDepth: How many levels deep (default: 2, range: 1-4)
 * - seed: Random seed (default: System.currentTimeMillis())
 */
@Component
@Slf4j
public class SpiderPatternManipulator implements FlatManipulator {

    public static final String NAME = "spider";
    public static final String PARAM_CENTER_X = "centerX";
    public static final String PARAM_CENTER_Z = "centerZ";
    public static final String PARAM_BRANCHES = "branches";
    public static final String PARAM_LENGTH = "length";
    public static final String PARAM_HEIGHT_DELTA = "heightDelta";
    public static final String PARAM_SUB_BRANCHES = "subBranches";
    public static final String PARAM_RECURSION_DEPTH = "recursionDepth";
    public static final String PARAM_SEED = "seed";

    private static final int DEFAULT_BRANCHES = 6;
    private static final int DEFAULT_LENGTH = 40;
    private static final int DEFAULT_HEIGHT_DELTA = -10;
    private static final int DEFAULT_SUB_BRANCHES = 3;
    private static final int DEFAULT_RECURSION_DEPTH = 2;
    private static final int MAX_RECURSION_DEPTH = 4;

    private Random random;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void manipulate(WFlat flat, int x, int z, int sizeX, int sizeZ,
                          Map<String, String> parameters) {
        log.debug("Starting spider pattern manipulation: region=({},{},{},{})", x, z, sizeX, sizeZ);

        // Parse parameters
        int centerX = parseIntParameter(parameters, PARAM_CENTER_X, sizeX / 2);
        int centerZ = parseIntParameter(parameters, PARAM_CENTER_Z, sizeZ / 2);
        int branches = parseIntParameter(parameters, PARAM_BRANCHES, DEFAULT_BRANCHES);
        int length = parseIntParameter(parameters, PARAM_LENGTH, DEFAULT_LENGTH);
        int heightDelta = parseIntParameter(parameters, PARAM_HEIGHT_DELTA, DEFAULT_HEIGHT_DELTA);
        int subBranches = parseIntParameter(parameters, PARAM_SUB_BRANCHES, DEFAULT_SUB_BRANCHES);
        int depth = parseIntParameter(parameters, PARAM_RECURSION_DEPTH, DEFAULT_RECURSION_DEPTH);
        long seed = parseLongParameter(parameters, PARAM_SEED, System.currentTimeMillis());

        // Validate and clamp parameters
        branches = Math.max(3, Math.min(12, branches));
        int maxLength = Math.min(sizeX, sizeZ) / 2;
        length = Math.max(10, Math.min(maxLength, length));
        heightDelta = Math.max(-50, Math.min(50, heightDelta));
        subBranches = Math.max(0, Math.min(5, subBranches));
        depth = Math.max(1, Math.min(MAX_RECURSION_DEPTH, depth));

        // Initialize random generator
        random = new Random(seed);

        // Setup FlatPainter
        FlatPainter painter = new FlatPainter(flat);

        // Calculate absolute center coordinates
        int absoluteCenterX = x + centerX;
        int absoluteCenterZ = z + centerZ;

        // Get center level
        int centerLevel = flat.getLevel(absoluteCenterX, absoluteCenterZ);

        // Choose painter based on height delta
        // Negative delta = carve (LOWER), Positive delta = raise (HIGHER)
        FlatPainter.Painter linePainter = heightDelta < 0 ?
                                         FlatPainter.LOWER :
                                         FlatPainter.HIGHER;

        // Draw main branches radiating from center
        // Distribute evenly around 360 degrees
        for (int i = 0; i < branches; i++) {
            double angle = (2 * Math.PI * i) / branches;

            // Add random variation to angle for more organic look
            angle += random.nextGaussian() * 0.2;

            // Calculate end point of main branch
            int endX = absoluteCenterX + (int) (Math.cos(angle) * length);
            int endZ = absoluteCenterZ + (int) (Math.sin(angle) * length);

            // Target level at end of branch
            int targetLevel = centerLevel + heightDelta;

            // Draw main branch
            painter.line(absoluteCenterX, absoluteCenterZ, endX, endZ,
                        targetLevel, linePainter);

            // Recursive sub-branches
            if (depth > 1) {
                drawSubBranches(painter, flat, endX, endZ, angle,
                              targetLevel, length / 2, heightDelta / 2,
                              subBranches, depth - 1, linePainter);
            }
        }

        // Smooth pattern edges for natural appearance
        painter.soften(x, z, x + sizeX - 1, z + sizeZ - 1, 0.3);

        log.info("Spider pattern manipulation completed: branches={}, length={}, heightDelta={}, subBranches={}, depth={}",
                branches, length, heightDelta, subBranches, depth);
    }

    /**
     * Recursively draw sub-branches.
     *
     * @param painter FlatPainter instance
     * @param flat WFlat instance
     * @param startX Start X coordinate
     * @param startZ Start Z coordinate
     * @param baseAngle Base angle direction
     * @param startLevel Starting height level
     * @param length Branch length
     * @param heightDelta Height change
     * @param branchCount Number of sub-branches
     * @param depth Remaining recursion depth
     * @param linePainter Painter to use (HIGHER or LOWER)
     */
    private void drawSubBranches(FlatPainter painter, WFlat flat,
                                int startX, int startZ, double baseAngle,
                                int startLevel, int length, int heightDelta,
                                int branchCount, int depth,
                                FlatPainter.Painter linePainter) {
        // Termination conditions
        if (depth <= 0 || length < 5) {
            return;
        }

        // Create sub-branches with angle variation around base direction
        for (int i = 0; i < branchCount; i++) {
            // Random angle variation (±45 degrees)
            double angleVariation = (random.nextDouble() - 0.5) * Math.PI / 2;
            double angle = baseAngle + angleVariation;

            // Calculate end point
            int endX = startX + (int) (Math.cos(angle) * length);
            int endZ = startZ + (int) (Math.sin(angle) * length);
            int targetLevel = startLevel + heightDelta;

            // Draw sub-branch
            painter.line(startX, startZ, endX, endZ, targetLevel, linePainter);

            // Recursive call with reduced parameters
            // 30% chance to skip recursion for more varied appearance
            if (depth > 1 && random.nextDouble() > 0.3) {
                drawSubBranches(painter, flat, endX, endZ, angle,
                              targetLevel, length / 2, heightDelta / 2,
                              Math.max(1, branchCount - 1), depth - 1,
                              linePainter);
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
