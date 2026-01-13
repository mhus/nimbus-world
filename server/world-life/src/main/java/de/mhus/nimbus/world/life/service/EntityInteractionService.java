package de.mhus.nimbus.world.life.service;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.life.behavior.BehaviorRegistry;
import de.mhus.nimbus.world.life.config.WorldLifeSettings;
import de.mhus.nimbus.world.life.model.SimulationState;
import de.mhus.nimbus.world.life.redis.PathwayPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Service for processing entity interactions from players.
 *
 * Receives interactions via Redis from world-player pods and processes them:
 * - Checks if this pod owns the entity
 * - Passes interaction to entity's behavior
 * - Behavior may generate new pathways in response
 * - Publishes new pathways to Redis if generated
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EntityInteractionService {

    private final EntityOwnershipService ownershipService;
    private final BehaviorRegistry behaviorRegistry;
    private final PathwayPublisher pathwayPublisher;
    private final WorldLifeSettings properties;
    private final SimulatorService simulatorService;

    /**
     * Handle entity interaction from a player.
     *
     * @param worldId World ID
     * @param entityId Entity being interacted with
     * @param action Interaction action type
     * @param timestamp Client timestamp
     * @param params Action-specific parameters
     * @param userId User ID of player
     * @param sessionId Session ID of player
     * @param displayName Display name of player
     */
    public void handleInteraction(
            WorldId worldId,
            String entityId,
            String action,
            Long timestamp,
            JsonNode params,
            String userId,
            String sessionId,
            String displayName) {

        // Check if this pod owns the entity
        if (!ownershipService.isOwnedByThisPod(worldId, entityId)) {
            log.trace("World {}: Entity interaction for entity not owned by this pod: entityId={}", worldId, entityId);
            return;
        }

        log.info("World {}: Processing entity interaction: entityId={}, action={}, user={}",
                worldId, entityId, action, displayName);

        // TODO: Pass interaction to behavior
        // For now, behaviors don't have interaction handlers
        // Future: EntityBehavior.onInteraction(entity, action, params, player)

        // Example: Some interactions might trigger immediate pathway changes
        // e.g., "attack" → entity flees, "talk" → entity stops moving

        // Placeholder: Log interaction for owned entity
        log.debug("Entity interaction received for owned entity: entityId={}, action={}, userId={}",
                entityId, action, userId);
    }

    /**
     * Get simulation state map from SimulatorService.
     * Note: This creates a coupling - consider refactoring if it becomes problematic.
     */
    private Map<String, SimulationState> getSimulationStates() {
        // Access via reflection or make simulationStates package-private
        // For now, we'll keep it simple and just log
        return Map.of();
    }
}
