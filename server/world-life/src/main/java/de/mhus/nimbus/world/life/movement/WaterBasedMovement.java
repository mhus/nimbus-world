package de.mhus.nimbus.world.life.movement;

import de.mhus.nimbus.generated.types.ENTITY_POSES;
import de.mhus.nimbus.generated.types.Rotation;
import de.mhus.nimbus.generated.types.Vector3;
import de.mhus.nimbus.generated.types.Waypoint;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.life.service.TerrainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Water-based movement system for aquatic entities.
 * Generates waypoints that stay within water bounds (between groundLevel and waterLevel).
 *
 * Features:
 * - Finds valid start position in water
 * - Generates waypoints along random direction
 * - Ensures each waypoint is within water
 * - Entities stay between ground and water surface
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WaterBasedMovement {

    private final TerrainService terrainService;
    private final Random random = new Random();

    /**
     * Find valid start position in water.
     *
     * @param worldId World identifier
     * @param x Starting X coordinate
     * @param z Starting Z coordinate
     * @return Water Y coordinate, or -1 if no water at this position
     */
    public int findStartPosition(WorldId worldId, double x, double z) {
        int floorX = (int) Math.floor(x);
        int floorZ = (int) Math.floor(z);

        return terrainService.getWaterPosition(worldId, floorX, floorZ);
    }

    /**
     * Get random horizontal direction (N, S, E, W, NE, NW, SE, SW).
     *
     * @return Normalized direction vector
     */
    public Vector3 getRandomDirection() {
        int choice = random.nextInt(8);

        Vector3 direction = new Vector3();
        direction.setY(0.0);

        switch (choice) {
            case 0 -> { direction.setX(1.0); direction.setZ(0.0); }  // East
            case 1 -> { direction.setX(-1.0); direction.setZ(0.0); } // West
            case 2 -> { direction.setX(0.0); direction.setZ(1.0); }  // South
            case 3 -> { direction.setX(0.0); direction.setZ(-1.0); } // North
            case 4 -> { direction.setX(1.0); direction.setZ(1.0); }  // SE
            case 5 -> { direction.setX(1.0); direction.setZ(-1.0); } // NE
            case 6 -> { direction.setX(-1.0); direction.setZ(1.0); } // SW
            case 7 -> { direction.setX(-1.0); direction.setZ(-1.0); }// NW
            default -> { direction.setX(1.0); direction.setZ(0.0); }
        }

        return direction;
    }

    /**
     * Generate pathway with water-aware waypoints.
     * All waypoints stay within water bounds (between ground and water surface).
     *
     * @param worldId World identifier
     * @param startPosition Starting position (should be in water)
     * @param direction Movement direction (not necessarily normalized)
     * @param waypointCount Number of waypoints to generate
     * @param speed Entity speed (blocks/second)
     * @param currentTime Current timestamp (milliseconds)
     * @return List of waypoints
     */
    public List<Waypoint> generatePathway(
            WorldId worldId,
            Vector3 startPosition,
            Vector3 direction,
            int waypointCount,
            double speed,
            long currentTime) {

        List<Waypoint> waypoints = new ArrayList<>();

        // Current position tracking
        double currentX = startPosition.getX();
        double currentY = startPosition.getY();
        double currentZ = startPosition.getZ();
        long waypointTime = currentTime;

        // Normalize direction vector (ignore Y component)
        double dirLength = Math.sqrt(direction.getX() * direction.getX() + direction.getZ() * direction.getZ());
        if (dirLength == 0) {
            log.warn("Direction vector has zero length, cannot generate pathway");
            return waypoints;
        }

        double dirX = direction.getX() / dirLength;
        double dirZ = direction.getZ() / dirLength;

        for (int i = 0; i < waypointCount; i++) {
            // Random step distance (1.5 to 2.5 blocks - fish swim in smaller steps)
            double stepDistance = 1.5 + random.nextDouble();

            // Calculate next position (2D movement, Y will be adjusted for water depth)
            double nextX = currentX + dirX * stepDistance;
            double nextZ = currentZ + dirZ * stepDistance;

            // Find water position at next location
            int waterY = terrainService.getWaterPosition(
                    worldId,
                    (int) Math.floor(nextX),
                    (int) Math.floor(nextZ)
            );

            // Check if position has water
            if (waterY < 0) {
                // No water at this position, skip this waypoint
                log.trace("Skipping waypoint - no water at: pos=({}, {})", (int)nextX, (int)nextZ);
                continue;
            }

            // Create next position in water
            Vector3 nextPosition = new Vector3();
            nextPosition.setX(nextX);
            nextPosition.setY((double) waterY);
            nextPosition.setZ(nextZ);

            // Calculate movement duration based on 3D distance
            double distance = distance(currentX, currentY, currentZ, nextX, waterY, nextZ);
            long movementDuration = (long) ((distance / speed) * 1000);
            waypointTime += movementDuration;

            // Create waypoint (fish use WALK pose, could be SWIM later)
            Waypoint waypoint = Waypoint.builder()
                    .timestamp(waypointTime)
                    .target(nextPosition)
                    .rotation(calculateRotation(currentX, currentZ, nextX, nextZ))
                    .pose(ENTITY_POSES.WALK)  // Could add SWIM pose later
                    .build();

            waypoints.add(waypoint);

            // Update current position for next iteration
            currentX = nextX;
            currentY = waterY;
            currentZ = nextZ;
        }

        log.trace("Generated water pathway with {} waypoints (requested {})", waypoints.size(), waypointCount);

        return waypoints;
    }

    /**
     * Calculate 3D distance between two points.
     */
    private double distance(double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Calculate rotation (yaw) to face from one position towards another.
     * Pitch is set to 0 (horizontal).
     *
     * @param fromX Starting X
     * @param fromZ Starting Z
     * @param toX Target X
     * @param toZ Target Z
     * @return Rotation with yaw pointing towards target
     */
    private Rotation calculateRotation(double fromX, double fromZ, double toX, double toZ) {
        double dx = toX - fromX;
        double dz = toZ - fromZ;

        // Calculate yaw (rotation around Y axis)
        double yawRad = Math.atan2(dx, dz);
        double yawDeg = Math.toDegrees(yawRad);

        Rotation rotation = new Rotation();
        rotation.setY(yawDeg);
        rotation.setP(0.0);  // Horizontal pitch
        return rotation;
    }

    /**
     * Generate random position within radius around center point (in water).
     * Useful for behaviors that roam around a home position.
     *
     * @param center Center position
     * @param radius Radius in blocks
     * @return Random position within radius
     */
    public Vector3 randomPositionInRadius(Vector3 center, double radius) {
        // Random angle
        double angle = random.nextDouble() * 2 * Math.PI;

        // Random distance (0 to radius)
        double distance = random.nextDouble() * radius;

        double offsetX = Math.cos(angle) * distance;
        double offsetZ = Math.sin(angle) * distance;

        Vector3 position = new Vector3();
        position.setX(center.getX() + offsetX);
        position.setY(center.getY());  // Y will be adjusted by water lookup
        position.setZ(center.getZ() + offsetZ);

        return position;
    }
}
