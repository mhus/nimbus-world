package de.mhus.nimbus.world.life.behavior;

import de.mhus.nimbus.generated.types.ENTITY_POSES;
import de.mhus.nimbus.generated.types.EntityPathway;
import de.mhus.nimbus.generated.types.Vector3;
import de.mhus.nimbus.generated.types.Waypoint;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.life.config.WorldLifeSettings;
import de.mhus.nimbus.world.life.model.SimulationState;
import de.mhus.nimbus.world.life.movement.BlockBasedMovement;
import de.mhus.nimbus.world.shared.world.WEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Prey animal behavior - slow-moving passive animals that roam around.
 *
 * Behavior pattern:
 * - Generates pathways every 5 seconds (configurable)
 * - Random walk in random directions
 * - 5 waypoints per pathway
 * - Idle pauses between movements (1-3 seconds)
 * - Terrain-aware movement using BlockBasedMovement
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PreyAnimalBehavior implements EntityBehavior {

    private static final String BEHAVIOR_TYPE = "PreyAnimalBehavior";

    // Configuration constants
    private static final int DEFAULT_WAYPOINTS_PER_PATH = 5;
    private static final long DEFAULT_MIN_IDLE_DURATION_MS = 1000;
    private static final long DEFAULT_MAX_IDLE_DURATION_MS = 3000;

    private final BlockBasedMovement blockMovement;
    private final WorldLifeSettings properties;
    private final Random random = new Random();

    @Override
    public String getBehaviorType() {
        return BEHAVIOR_TYPE;
    }

    @Override
    public EntityPathway update(WEntity entity, SimulationState state, long currentTime, WorldId worldId) {
        // Check if we need a new pathway (with interval check)
        if (!needsNewPathwayWithInterval(state, currentTime)) {
            return null;
        }

        // Generate new pathway
        return generatePathway(entity, state, currentTime, worldId);
    }

    /**
     * Check if entity needs new pathway, considering both expiration and interval.
     */
    private boolean needsNewPathwayWithInterval(SimulationState state, long currentTime) {
        if (!needsNewPathway(state, currentTime)) {
            return false;
        }

        // Check minimum interval between pathways
        long timeSinceLastPathway = currentTime - state.getLastPathwayTime();
        return timeSinceLastPathway >= properties.getPathwayIntervalMs();
    }

    /**
     * Generate a new pathway for the entity.
     */
    private EntityPathway generatePathway(WEntity entity, SimulationState state, long currentTime, WorldId worldId) {
        // Get entity's current position (server-side simulation data)
        Vector3 currentPosition = entity.getPosition();
        if (currentPosition == null) {
            log.warn("Entity has no position: {}", entity.getEntityId());
            return null;
        }

        // Ensure start position is on solid ground
        int startY = blockMovement.findStartPosition(worldId, currentPosition.getX(), currentPosition.getZ());

        Vector3 startPosition = new Vector3();
        startPosition.setX(currentPosition.getX());
        startPosition.setY((double) startY);
        startPosition.setZ(currentPosition.getZ());

        // Choose random direction for roaming
        Vector3 direction = blockMovement.getRandomDirection();

        // Get entity speed (default to 1.0 blocks/second)
        double speed = entity.getSpeed() != null ? entity.getSpeed() : 1.0;

        // Generate waypoints using terrain-aware movement
        List<Waypoint> movementWaypoints = blockMovement.generatePathway(
                worldId,
                startPosition,
                direction,
                DEFAULT_WAYPOINTS_PER_PATH,
                speed,
                currentTime
        );

        if (movementWaypoints.isEmpty()) {
            log.debug("No valid waypoints generated for entity {}", entity.getEntityId());
            return null;
        }

        // Add idle pauses between movement waypoints
        List<Waypoint> waypointsWithIdle = addIdlePauses(movementWaypoints);

        // Create pathway
        EntityPathway pathway = EntityPathway.builder()
                .entityId(entity.getEntityId())
                .startAt(currentTime)
                .waypoints(waypointsWithIdle)
                .isLooping(false)
                .idlePose(ENTITY_POSES.IDLE)
                .build();

        log.trace("Generated pathway for entity {}: {} waypoints (includes idle)",
                entity.getEntityId(), waypointsWithIdle.size());

        return pathway;
    }

    /**
     * Add idle pauses after each movement waypoint.
     * Idle pauses make movement look more natural.
     *
     * @param movementWaypoints Original movement waypoints
     * @return Waypoints with idle pauses inserted
     */
    private List<Waypoint> addIdlePauses(List<Waypoint> movementWaypoints) {
        List<Waypoint> result = new ArrayList<>();

        for (Waypoint waypoint : movementWaypoints) {
            // Add movement waypoint
            result.add(waypoint);

            // Add idle pause after movement
            long idleDuration = DEFAULT_MIN_IDLE_DURATION_MS +
                    (long) (random.nextDouble() * (DEFAULT_MAX_IDLE_DURATION_MS - DEFAULT_MIN_IDLE_DURATION_MS));

            Waypoint idleWaypoint = Waypoint.builder()
                    .timestamp(waypoint.getTimestamp() + idleDuration)
                    .target(waypoint.getTarget())  // Stay at same position
                    .rotation(waypoint.getRotation())
                    .pose(ENTITY_POSES.IDLE)
                    .build();

            result.add(idleWaypoint);
        }

        return result;
    }
}
