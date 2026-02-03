package de.mhus.nimbus.world.generator.flat.hexgrid;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * StraightPathFinder creates more realistic straight paths with probabilistic curves.
 * Used for roads that are elevated or deep (not terrain-adaptive).
 * - Moves toward target with configurable probability
 * - Creates natural-looking curves without pure sine waves
 * - Respects maximum slope constraint
 */
@Slf4j
public class StraightPathFinder {

    private final int maxSlopePerBlock;
    private final double straightness;  // 0.0 = very curvy, 1.0 = perfectly straight
    private final int maxLateralOffset;  // Maximum pixels of lateral deviation
    private final Random random;

    /**
     * @param maxSlopePerBlock Maximum elevation change per block (typically 1)
     * @param straightness How straight the path should be (0.0-1.0, default 0.7)
     * @param maxLateralOffset Maximum lateral offset from straight line (default 10)
     * @param seed Random seed for reproducibility
     */
    public StraightPathFinder(int maxSlopePerBlock, double straightness, int maxLateralOffset, long seed) {
        this.maxSlopePerBlock = maxSlopePerBlock;
        this.straightness = Math.max(0.0, Math.min(1.0, straightness));
        this.maxLateralOffset = maxLateralOffset;
        this.random = new Random(seed);
    }

    /**
     * Finds a straight-ish path from start to end with natural curves.
     *
     * @param startX Start X coordinate
     * @param startZ Start Z coordinate
     * @param startLevel Required start elevation
     * @param endX End X coordinate
     * @param endZ End Z coordinate
     * @param endLevel Required end elevation
     * @return List of PathPoints
     */
    public List<TerrainPathFinder.PathPoint> findPath(int startX, int startZ, int startLevel,
                                                        int endX, int endZ, int endLevel) {
        List<TerrainPathFinder.PathPoint> path = new ArrayList<>();

        // Calculate direct distance and direction
        int dx = endX - startX;
        int dz = endZ - startZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        int steps = Math.max(1, (int) Math.ceil(distance));

        // Calculate perpendicular direction for lateral offset
        double[] perpDir = calculatePerpendicularDirection(dx, dz);

        // Track cumulative lateral offset for smooth curves
        double cumulativeOffset = 0.0;
        double offsetVelocity = 0.0;  // Rate of change of offset

        for (int step = 0; step <= steps; step++) {
            double t = steps > 0 ? (double) step / steps : 0.0;

            // Base position (straight line)
            double baseX = startX + t * dx;
            double baseZ = startZ + t * dz;

            // Update lateral offset with probabilistic movement
            // Random walk with tendency toward center
            double randomPush = (random.nextDouble() - 0.5) * 2.0;  // -1.0 to 1.0
            double centeringForce = -cumulativeOffset * 0.1;  // Pull toward center

            offsetVelocity += randomPush * (1.0 - straightness) + centeringForce;
            offsetVelocity *= 0.8;  // Damping for smoother curves

            cumulativeOffset += offsetVelocity;

            // Clamp offset to max
            cumulativeOffset = Math.max(-maxLateralOffset, Math.min(maxLateralOffset, cumulativeOffset));

            // Apply lateral offset perpendicular to road direction
            int x = (int) Math.round(baseX + cumulativeOffset * perpDir[0]);
            int z = (int) Math.round(baseZ + cumulativeOffset * perpDir[1]);

            // Interpolate level linearly (respecting max slope)
            int level = (int) Math.round(startLevel + t * (endLevel - startLevel));

            // Clamp level change per step
            if (step > 0) {
                TerrainPathFinder.PathPoint prevPoint = path.get(path.size() - 1);
                int levelDiff = level - prevPoint.level;
                if (Math.abs(levelDiff) > maxSlopePerBlock) {
                    level = prevPoint.level + (levelDiff > 0 ? maxSlopePerBlock : -maxSlopePerBlock);
                }
            }

            path.add(new TerrainPathFinder.PathPoint(x, z, level));
        }

        // Ensure end point is exact
        if (!path.isEmpty()) {
            TerrainPathFinder.PathPoint lastPoint = path.get(path.size() - 1);
            lastPoint.level = endLevel;
        }

        log.debug("StraightPathFinder: Created path with {} points", path.size());

        return path;
    }

    /**
     * Calculate perpendicular direction vector (normalized).
     */
    private double[] calculatePerpendicularDirection(int dx, int dz) {
        // Perpendicular vector is (-dz, dx)
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length == 0) {
            return new double[]{0, 0};
        }
        return new double[]{-dz / length, dx / length};
    }
}
