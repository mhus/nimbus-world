package de.mhus.nimbus.world.life.behavior;

import de.mhus.nimbus.generated.types.EntityPathway;
import de.mhus.nimbus.generated.types.Vector3;
import de.mhus.nimbus.generated.types.WorldInfo;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.life.model.SimulationState;
import de.mhus.nimbus.world.shared.redis.EntityStateRedisService;
import de.mhus.nimbus.world.shared.redis.EntityStatusPublisher;
import de.mhus.nimbus.world.shared.world.EntitySchedulePhase;
import de.mhus.nimbus.world.shared.world.WEntity;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldService;
import de.mhus.nimbus.world.shared.world.WorldTime;
import de.mhus.nimbus.world.shared.world.WorldTimeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Behavior for entities with a daily schedule (timetable).
 * Switches location, sub-behavior, and presence based on world hour.
 *
 * On phase change:
 * - present=false → sends Gone, returns null (entity inactive)
 * - new point → updates position/middlePoint, respawns at new location
 * - delegates pathway generation to the phase's sub-behavior (or entity default)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduledBehavior implements EntityBehavior {

    private static final String BEHAVIOR_TYPE = "ScheduledBehavior";
    private static final String REDIS_FIELD_SCHEDULE_PHASE = "schedulePhase";

    private final WorldTimeService worldTimeService;
    private final WWorldService worldService;
    private final BehaviorRegistry behaviorRegistry;
    private final EntityStatusPublisher entityStatusPublisher;
    private final EntityStateRedisService entityStateRedisService;

    @Override
    public String getBehaviorType() {
        return BEHAVIOR_TYPE;
    }

    @Override
    public EntityPathway update(WEntity entity, SimulationState state, long currentTime, WorldId worldId, int epoch) {
        List<EntitySchedulePhase> schedule = entity.getSchedule();
        if (schedule == null || schedule.isEmpty()) {
            // No schedule — delegate to default behavior
            return delegateToBehavior(entity.getBehaviorModel(), entity, state, currentTime, worldId, epoch);
        }

        // Get current world hour
        WWorld world = worldService.getByWorldId(worldId.getId()).orElse(null);
        if (world == null || world.getPublicData() == null) {
            return delegateToBehavior(entity.getBehaviorModel(), entity, state, currentTime, worldId, epoch);
        }

        WorldInfo worldInfo = world.getPublicData();
        WorldTime worldTime = worldTimeService.getCurrentWorldTime(worldInfo);
        int currentHour = worldTime.hour();

        // Find matching phase
        EntitySchedulePhase activePhase = findActivePhase(schedule, currentHour);
        String activePhaseName = activePhase != null ? activePhase.getName() : null;
        String previousPhase = state.getCurrentSchedulePhase();

        // Phase change?
        boolean phaseChanged = !java.util.Objects.equals(previousPhase, activePhaseName);
        if (phaseChanged) {
            state.setCurrentSchedulePhase(activePhaseName);
            onPhaseChange(entity, state, activePhase, worldId, worldInfo);
        }

        // Not present in this phase → entity is gone
        if (activePhase != null && !activePhase.isPresent()) {
            return null;
        }
        // No matching phase and entity was gone → stay gone
        if (activePhase == null && state.getLifecycleState() == SimulationState.LifecycleState.GONE) {
            return null;
        }

        // Determine sub-behavior
        String behaviorName = activePhase != null && activePhase.getBehavior() != null
                ? activePhase.getBehavior()
                : entity.getBehaviorModel();

        // Apply phase overrides to entity (temporarily)
        if (activePhase != null) {
            applyPhaseOverrides(entity, activePhase);
        }

        return delegateToBehavior(behaviorName, entity, state, currentTime, worldId, epoch);
    }

    @Override
    public boolean needsNewPathway(SimulationState state, long currentTime) {
        // Always check — phase changes need immediate response
        return true;
    }

    private EntitySchedulePhase findActivePhase(List<EntitySchedulePhase> schedule, int hour) {
        for (EntitySchedulePhase phase : schedule) {
            if (phase.matchesHour(hour)) {
                return phase;
            }
        }
        return null;
    }

    private void onPhaseChange(WEntity entity, SimulationState state,
                                EntitySchedulePhase newPhase, WorldId worldId, WorldInfo worldInfo) {
        String entityId = entity.getEntityId();
        String phaseName = newPhase != null ? newPhase.getName() : "none";

        log.info("World {}: Entity {} phase change -> {}", worldId, entityId, phaseName);

        // Update Redis
        entityStateRedisService.setSchedulePhase(worldId.getId(), entityId, phaseName);

        if (newPhase == null) {
            return;
        }

        if (!newPhase.isPresent()) {
            // Entity disappears
            entityStatusPublisher.publishStatusUpdate(
                    worldId.getId(), entityId, Map.of(EntityStatusPublisher.GONE, 1), null);
            state.setLifecycleState(SimulationState.LifecycleState.GONE);
            state.setCurrentPathway(null);
            log.info("World {}: Entity {} gone (schedule phase: {})", worldId, entityId, phaseName);
            return;
        }

        // Entity reappears if it was gone
        if (state.getLifecycleState() == SimulationState.LifecycleState.GONE) {
            state.setLifecycleState(SimulationState.LifecycleState.ALIVE);
            state.setLifecycleTimestamp(0);
            state.setCurrentPathway(null);
            state.setPathwayEndTime(0);
            entityStateRedisService.remove(worldId.getId(), entityId);
        }

        // Relocate if new point specified
        if (newPhase.getPoint() != null && !newPhase.getPoint().isBlank()) {
            Vector3 newPosition = parsePoint(newPhase.getPoint());
            if (newPosition != null) {
                entity.setPosition(newPosition);
                entity.setMiddlePoint(newPosition);
                state.setCurrentPathway(null);
                state.setPathwayEndTime(0);
                log.info("World {}: Entity {} relocated to {} (phase: {})",
                        worldId, entityId, newPhase.getPoint(), phaseName);
            }
        }
    }

    private void applyPhaseOverrides(WEntity entity, EntitySchedulePhase phase) {
        if (phase.getRoamRadius() != null) {
            entity.setRadius(phase.getRoamRadius());
        }
        if (phase.getSpeed() != null) {
            entity.setSpeed(phase.getSpeed());
        }
    }

    private EntityPathway delegateToBehavior(String behaviorName, WEntity entity,
                                              SimulationState state, long currentTime,
                                              WorldId worldId, int epoch) {
        if (behaviorName == null || behaviorName.isBlank()) {
            return null;
        }
        EntityBehavior subBehavior = behaviorRegistry.getBehavior(behaviorName);
        if (subBehavior == null) {
            log.warn("World {}: Sub-behavior '{}' not found for scheduled entity {}",
                    worldId, behaviorName, entity.getEntityId());
            return null;
        }
        return subBehavior.update(entity, state, currentTime, worldId, epoch);
    }

    /**
     * Parse point string as "x,y,z" coordinates.
     */
    private Vector3 parsePoint(String point) {
        try {
            String[] parts = point.split(",");
            if (parts.length != 3) {
                log.warn("Invalid point format (expected x,y,z): {}", point);
                return null;
            }
            return Vector3.builder()
                    .x(Double.parseDouble(parts[0].trim()))
                    .y(Double.parseDouble(parts[1].trim()))
                    .z(Double.parseDouble(parts[2].trim()))
                    .build();
        } catch (NumberFormatException e) {
            log.warn("Invalid point coordinates: {}", point);
            return null;
        }
    }
}
