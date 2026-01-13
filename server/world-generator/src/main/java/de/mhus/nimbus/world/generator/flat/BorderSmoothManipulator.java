package de.mhus.nimbus.world.generator.flat;

import de.mhus.nimbus.world.shared.generator.WFlat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Border smooth manipulator that smooths terrain heights at the edges of a flat.
 * The outermost border pixels are not modified, allowing inner regions to blend
 * smoothly with the outer edge. Corner areas receive additional smoothing to avoid
 * sharp angular transitions.
 * <p>
 * Parameters:
 * - depth: How many pixels inward to smooth (default: 3)
 * - strength: Smoothing strength from 0.0 to 1.0 (default: 1.0)
 * - cornerDepth: Additional smoothing depth for corners (default: 2)
 */
@Component
@Slf4j
public class BorderSmoothManipulator implements FlatManipulator {

    public static final String NAME = "border-smooth";
    public static final String PARAM_DEPTH = "depth";
    public static final String PARAM_STRENGTH = "strength";
    public static final String PARAM_CORNER_DEPTH = "cornerDepth";

    private static final int DEFAULT_DEPTH = 3;
    private static final double DEFAULT_STRENGTH = 1.0;
    private static final int DEFAULT_CORNER_DEPTH = 2;

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public void manipulate(WFlat flat, int x, int z, int sizeX, int sizeZ, Map<String, String> parameters) {
        log.debug("Smoothing borders: region=({},{},{},{})", x, z, sizeX, sizeZ);

        // Parse parameters
        int depth = parseIntParameter(parameters, PARAM_DEPTH, DEFAULT_DEPTH);
        double strength = parseDoubleParameter(parameters, PARAM_STRENGTH, DEFAULT_STRENGTH);
        int cornerDepth = parseIntParameter(parameters, PARAM_CORNER_DEPTH, DEFAULT_CORNER_DEPTH);

        // Clamp values
        depth = Math.max(1, Math.min(Math.min(sizeX, sizeZ) / 2, depth));
        strength = Math.max(0.0, Math.min(1.0, strength));
        cornerDepth = Math.max(0, Math.min(depth, cornerDepth));

        // Store original heights for interpolation
        int[][] originalHeights = new int[sizeX][sizeZ];
        for (int localZ = 0; localZ < sizeZ; localZ++) {
            for (int localX = 0; localX < sizeX; localX++) {
                int flatX = x + localX;
                int flatZ = z + localZ;
                originalHeights[localX][localZ] = flat.getLevel(flatX, flatZ);
            }
        }

        // Smooth top and bottom edges
        for (int localX = 0; localX < sizeX; localX++) {
            // Top edge (skip corner regions for now)
            if (localX >= depth + cornerDepth && localX < sizeX - depth - cornerDepth) {
                smoothVerticalBorder(flat, x, z, localX, 0, depth, strength, originalHeights, true);
            }

            // Bottom edge (skip corner regions for now)
            if (localX >= depth + cornerDepth && localX < sizeX - depth - cornerDepth) {
                smoothVerticalBorder(flat, x, z, localX, sizeZ - 1, depth, strength, originalHeights, false);
            }
        }

        // Smooth left and right edges
        for (int localZ = 0; localZ < sizeZ; localZ++) {
            // Left edge (skip corner regions for now)
            if (localZ >= depth + cornerDepth && localZ < sizeZ - depth - cornerDepth) {
                smoothHorizontalBorder(flat, x, z, 0, localZ, depth, strength, originalHeights, true);
            }

            // Right edge (skip corner regions for now)
            if (localZ >= depth + cornerDepth && localZ < sizeZ - depth - cornerDepth) {
                smoothHorizontalBorder(flat, x, z, sizeX - 1, localZ, depth, strength, originalHeights, false);
            }
        }

        // Smooth corner regions with additional depth
        int totalCornerDepth = depth + cornerDepth;

        // Top-left corner
        smoothCorner(flat, x, z, 0, 0, totalCornerDepth, strength, originalHeights);

        // Top-right corner
        smoothCorner(flat, x, z, sizeX - 1, 0, totalCornerDepth, strength, originalHeights);

        // Bottom-left corner
        smoothCorner(flat, x, z, 0, sizeZ - 1, totalCornerDepth, strength, originalHeights);

        // Bottom-right corner
        smoothCorner(flat, x, z, sizeX - 1, sizeZ - 1, totalCornerDepth, strength, originalHeights);

        log.info("Border smoothed: region=({},{},{},{}), depth={}, strength={}, cornerDepth={}",
                x, z, sizeX, sizeZ, depth, strength, cornerDepth);
    }

    /**
     * Smooth vertical border (top or bottom edge).
     */
    private void smoothVerticalBorder(WFlat flat, int offsetX, int offsetZ, int localX, int edgeZ,
                                      int depth, double strength, int[][] originalHeights, boolean isTop) {
        int flatX = offsetX + localX;

        // Get edge height (this won't be modified)
        int edgeHeight = originalHeights[localX][edgeZ];

        // Smooth inward from edge
        for (int d = 1; d <= depth; d++) {
            int localZ = isTop ? edgeZ + d : edgeZ - d;
            if (localZ < 0 || localZ >= originalHeights[0].length) continue;

            int flatZ = offsetZ + localZ;
            int currentHeight = originalHeights[localX][localZ];

            // Calculate interpolation factor (closer to edge = stronger influence)
            double factor = (double) (depth - d + 1) / (depth + 1);
            factor *= strength;

            // Interpolate between current height and edge height
            int newHeight = (int) Math.round(currentHeight * (1.0 - factor) + edgeHeight * factor);
            flat.setLevel(flatX, flatZ, newHeight);
        }
    }

    /**
     * Smooth horizontal border (left or right edge).
     */
    private void smoothHorizontalBorder(WFlat flat, int offsetX, int offsetZ, int edgeX, int localZ,
                                        int depth, double strength, int[][] originalHeights, boolean isLeft) {
        int flatZ = offsetZ + localZ;

        // Get edge height (this won't be modified)
        int edgeHeight = originalHeights[edgeX][localZ];

        // Smooth inward from edge
        for (int d = 1; d <= depth; d++) {
            int localX = isLeft ? edgeX + d : edgeX - d;
            if (localX < 0 || localX >= originalHeights.length) continue;

            int flatX = offsetX + localX;
            int currentHeight = originalHeights[localX][localZ];

            // Calculate interpolation factor (closer to edge = stronger influence)
            double factor = (double) (depth - d + 1) / (depth + 1);
            factor *= strength;

            // Interpolate between current height and edge height
            int newHeight = (int) Math.round(currentHeight * (1.0 - factor) + edgeHeight * factor);
            flat.setLevel(flatX, flatZ, newHeight);
        }
    }

    /**
     * Smooth corner region with additional depth for smoother transitions.
     */
    private void smoothCorner(WFlat flat, int offsetX, int offsetZ, int cornerX, int cornerZ,
                             int totalDepth, double strength, int[][] originalHeights) {
        int sizeX = originalHeights.length;
        int sizeZ = originalHeights[0].length;

        // Determine corner direction
        int xDir = (cornerX == 0) ? 1 : -1;
        int zDir = (cornerZ == 0) ? 1 : -1;

        // Get corner edge heights for interpolation
        int cornerHeight = originalHeights[cornerX][cornerZ];

        // Smooth diagonal area from corner
        for (int d = 1; d <= totalDepth; d++) {
            int localX = cornerX + d * xDir;
            int localZ = cornerZ + d * zDir;

            if (localX < 0 || localX >= sizeX || localZ < 0 || localZ >= sizeZ) continue;

            int flatX = offsetX + localX;
            int flatZ = offsetZ + localZ;
            int currentHeight = originalHeights[localX][localZ];

            // Diagonal smoothing with stronger influence near corner
            double factor = (double) (totalDepth - d + 1) / (totalDepth + 1);
            factor *= strength;

            int newHeight = (int) Math.round(currentHeight * (1.0 - factor) + cornerHeight * factor);
            flat.setLevel(flatX, flatZ, newHeight);
        }

        // Additional smoothing for rectangular area around corner
        for (int dx = 1; dx <= totalDepth; dx++) {
            for (int dz = 1; dz <= totalDepth; dz++) {
                if (dx == dz) continue; // Skip diagonal (already handled)

                int localX = cornerX + dx * xDir;
                int localZ = cornerZ + dz * zDir;

                if (localX < 0 || localX >= sizeX || localZ < 0 || localZ >= sizeZ) continue;

                int flatX = offsetX + localX;
                int flatZ = offsetZ + localZ;
                int currentHeight = originalHeights[localX][localZ];

                // Calculate distance from corner
                double distance = Math.sqrt(dx * dx + dz * dz);
                double maxDistance = Math.sqrt(2 * totalDepth * totalDepth);

                // Interpolation factor based on distance
                double factor = (maxDistance - distance) / maxDistance;
                factor *= strength;
                factor = Math.max(0.0, factor);

                int newHeight = (int) Math.round(currentHeight * (1.0 - factor) + cornerHeight * factor);
                flat.setLevel(flatX, flatZ, newHeight);
            }
        }
    }

    private int parseIntParameter(Map<String, String> parameters, String name, int defaultValue) {
        if (parameters == null || !parameters.containsKey(name)) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(parameters.get(name));
        } catch (NumberFormatException e) {
            log.warn("Invalid integer parameter '{}': {}, using default: {}", name, parameters.get(name), defaultValue);
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
            log.warn("Invalid double parameter '{}': {}, using default: {}", name, parameters.get(name), defaultValue);
            return defaultValue;
        }
    }
}
