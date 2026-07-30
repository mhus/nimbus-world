package de.mhus.nimbus.world.life.service;

import tools.jackson.databind.JsonNode;
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
 * - Handles dialog_start/dialog_end to pause/resume entity movement
 * - Passes interaction to entity's behavior
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

        // Handle dialog pause/resume
        if ("dialog_start".equals(action)) {
            handleDialogStart(worldId, entityId, userId);
            return;
        }
        if ("dialog_end".equals(action)) {
            handleDialogEnd(worldId, entityId, userId);
            return;
        }

        log.debug("Entity interaction received: entityId={}, action={}, userId={}", entityId, action, userId);
    }

    private void handleDialogStart(WorldId worldId, String entityId, String playerId) {
        SimulationState state = simulatorService.findSimulationState(worldId, entityId);
        if (state == null) {
            log.warn("World {}: dialog_start for unknown entity: {}", worldId, entityId);
            return;
        }

        boolean wasFirst = state.dialogStart(playerId);
        log.info("World {}: Entity {} dialog_start by player {} (first={})", worldId, entityId, playerId, wasFirst);

        if (wasFirst) {
            // Immediately stop the entity by publishing an idle pathway
            simulatorService.forceIdlePathway(worldId, entityId, state);
        }
    }

    private void handleDialogEnd(WorldId worldId, String entityId, String playerId) {
        SimulationState state = simulatorService.findSimulationState(worldId, entityId);
        if (state == null) {
            log.warn("World {}: dialog_end for unknown entity: {}", worldId, entityId);
            return;
        }

        boolean nowEmpty = state.dialogEnd(playerId);
        log.info("World {}: Entity {} dialog_end by player {} (resumeMovement={})", worldId, entityId, playerId, nowEmpty);
    }
}
