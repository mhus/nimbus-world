package de.mhus.nimbus.world.generator.flat.hexgrid;

import de.mhus.nimbus.world.shared.generator.WFlat;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * TerrainPathFinder finds an optimal path between two points by following the terrain.
 * - Minimizes slope by considering terrain elevation
 * - Respects maximum slope constraint (±1 per block)
 * - Prevents excessive drift from direct path
 * - Returns null if terrain makes path impossible
 */
@Slf4j
public class TerrainPathFinder {

    private final WFlat flat;
    private final int maxSlopePerBlock;
    private final double maxDriftRatio;  // Max ratio of actual distance to direct distance

    /**
     * @param flat The terrain to navigate
     * @param maxSlopePerBlock Maximum elevation change per block (typically 1)
     * @param maxDriftRatio Maximum drift from direct path (e.g., 1.5 means 50% longer path allowed)
     */
    public TerrainPathFinder(WFlat flat, int maxSlopePerBlock, double maxDriftRatio) {
        this.flat = flat;
        this.maxSlopePerBlock = maxSlopePerBlock;
        this.maxDriftRatio = maxDriftRatio;
    }

    /**
     * Finds a terrain-following path from start to end.
     *
     * @param startX Start X coordinate
     * @param startZ Start Z coordinate
     * @param startLevel Required start elevation
     * @param endX End X coordinate
     * @param endZ End Z coordinate
     * @param endLevel Required end elevation
     * @return List of PathPoints, or null if path not feasible
     */
    public List<PathPoint> findPath(int startX, int startZ, int startLevel,
                                      int endX, int endZ, int endLevel) {
        List<PathPoint> path = new ArrayList<>();

        // Calculate direct distance for drift control
        int dx = endX - startX;
        int dz = endZ - startZ;
        double directDistance = Math.sqrt(dx * dx + dz * dz);
        double maxPathLength = directDistance * maxDriftRatio;

        // Greedy pathfinding: step-by-step toward goal
        int currentX = startX;
        int currentZ = startZ;
        int currentLevel = startLevel;
        double traveledDistance = 0;

        // Velocity vector for momentum (prevents zigzagging)
        double velocityX = 0;
        double velocityZ = 0;
        double velocityDamping = 0.6;  // Velocity decreases quickly

        path.add(new PathPoint(currentX, currentZ, currentLevel));

        while (currentX != endX || currentZ != endZ) {
            // Find best next step
            StepCandidate bestStep = findBestNextStep(
                currentX, currentZ, currentLevel,
                endX, endZ, endLevel,
                directDistance, traveledDistance, maxPathLength,
                velocityX, velocityZ
            );

            if (bestStep == null) {
                // No valid step found - path blocked
                log.debug("TerrainPathFinder: Path blocked at ({}, {})", currentX, currentZ);
                return null;
            }

            // Calculate movement vector
            int moveX = bestStep.x - currentX;
            int moveZ = bestStep.z - currentZ;

            // Update velocity with damping
            velocityX = velocityX * velocityDamping + moveX;
            velocityZ = velocityZ * velocityDamping + moveZ;

            // Move to next position
            currentX = bestStep.x;
            currentZ = bestStep.z;
            currentLevel = bestStep.level;
            traveledDistance += bestStep.distance;

            path.add(new PathPoint(currentX, currentZ, currentLevel));

            // Safety check: prevent infinite loops
            if (path.size() > maxPathLength * 2) {
                log.warn("TerrainPathFinder: Path too long, aborting");
                return null;
            }
        }

        // Verify we reached the end level (within tolerance)
        if (Math.abs(currentLevel - endLevel) > maxSlopePerBlock) {
            log.debug("TerrainPathFinder: Cannot reach end level (current={}, target={})",
                currentLevel, endLevel);
            return null;
        }

        // Adjust final level to match exactly
        if (!path.isEmpty()) {
            path.get(path.size() - 1).level = endLevel;
        }

        log.debug("TerrainPathFinder: Found path with {} points, traveled distance: {:.1f} (direct: {:.1f})",
            path.size(), traveledDistance, directDistance);

        return path;
    }

    /**
     * Finds the best next step from current position.
     *
     * @param velocityX Current velocity X component (for momentum)
     * @param velocityZ Current velocity Z component (for momentum)
     */
    private StepCandidate findBestNextStep(int currentX, int currentZ, int currentLevel,
                                            int targetX, int targetZ, int targetLevel,
                                            double directDistance, double traveledDistance,
                                            double maxPathLength,
                                            double velocityX, double velocityZ) {
        StepCandidate bestCandidate = null;
        double bestScore = Double.MAX_VALUE;

        // Consider 8 neighboring cells
        int[][] neighbors = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},  // Cardinal directions
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}  // Diagonals
        };

        for (int[] neighbor : neighbors) {
            int nextX = currentX + neighbor[0];
            int nextZ = currentZ + neighbor[1];

            // Check bounds
            if (nextX < 0 || nextX >= flat.getSizeX() || nextZ < 0 || nextZ >= flat.getSizeZ()) {
                continue;
            }

            // Get terrain level
            int terrainLevel = flat.getLevel(nextX, nextZ);

            // Calculate distance to target
            int dx = targetX - nextX;
            int dz = targetZ - nextZ;
            double distanceToTarget = Math.sqrt(dx * dx + dz * dz);

            // Calculate step distance
            double stepDistance = Math.sqrt(neighbor[0] * neighbor[0] + neighbor[1] * neighbor[1]);

            // Check drift constraint
            if (traveledDistance + stepDistance + distanceToTarget > maxPathLength) {
                continue;  // Would drift too far
            }

            // Calculate required level to reach target from here
            double remainingSteps = distanceToTarget;
            int levelDiff = targetLevel - terrainLevel;
            double requiredSlopePerBlock = remainingSteps > 0 ? Math.abs(levelDiff) / remainingSteps : 0;

            // Choose level: follow terrain if slope allows, otherwise adjust minimally
            int nextLevel;
            int slopeFromCurrent = terrainLevel - currentLevel;

            if (Math.abs(slopeFromCurrent) <= maxSlopePerBlock) {
                // Can follow terrain
                nextLevel = terrainLevel;
            } else {
                // Must limit slope
                nextLevel = currentLevel + (slopeFromCurrent > 0 ? maxSlopePerBlock : -maxSlopePerBlock);
            }

            // Roads must stay above sea level (minimum level 1)
            nextLevel = Math.max(1, nextLevel);

            // Ensure level never goes below 1 (similar to rivers)
            if (nextLevel < 1) {
                nextLevel = 1;
            }

            // Check if we can still reach target level
            double remainingLevelChange = Math.abs(targetLevel - nextLevel);
            if (remainingLevelChange > remainingSteps * maxSlopePerBlock) {
                continue;  // Cannot reach target level from here
            }

            // Calculate score: prefer minimal slope and progress toward goal
            double slopeCost = Math.abs(nextLevel - terrainLevel) * 10.0;  // Penalty for deviating from terrain
            double distanceCost = distanceToTarget;  // Prefer getting closer to target
            double driftPenalty = (traveledDistance + stepDistance) / directDistance * 5.0;  // Penalty for drifting

            // Momentum bonus: prefer moving in the same direction as current velocity
            // Dot product between movement direction and velocity vector
            double momentumAlignment = neighbor[0] * velocityX + neighbor[1] * velocityZ;
            double momentumPenalty = -momentumAlignment * 8.0;  // Bonus for alignment (negative = lower score)

            double score = slopeCost + distanceCost + driftPenalty + momentumPenalty;

            if (score < bestScore) {
                bestScore = score;
                bestCandidate = new StepCandidate(nextX, nextZ, nextLevel, stepDistance);
            }
        }

        return bestCandidate;
    }

    /**
     * Candidate for next step in path.
     */
    private static class StepCandidate {
        final int x, z, level;
        final double distance;

        StepCandidate(int x, int z, int level, double distance) {
            this.x = x;
            this.z = z;
            this.level = level;
            this.distance = distance;
        }
    }

    /**
     * A point along the path.
     */
    public static class PathPoint {
        public final int x, z;
        public int level;

        public PathPoint(int x, int z, int level) {
            this.x = x;
            this.z = z;
            this.level = level;
        }
    }
}
