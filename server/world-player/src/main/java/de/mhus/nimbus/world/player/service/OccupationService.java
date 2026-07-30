package de.mhus.nimbus.world.player.service;

import de.mhus.nimbus.generated.types.Entity;
import de.mhus.nimbus.generated.types.EntityModel;
import de.mhus.nimbus.generated.types.EntityStatusUpdate;
import de.mhus.nimbus.generated.types.ItemBlockRef;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.player.session.SessionClosedConsumer;
import de.mhus.nimbus.world.shared.redis.EntityStatusPublisher;
import de.mhus.nimbus.world.shared.world.WEntityModelService;
import de.mhus.nimbus.world.shared.world.WItemPosition;
import de.mhus.nimbus.world.shared.world.WItemPositionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for managing player overlay models (occupation and mounts).
 *
 * Handles two scenarios:
 * 1. Occupation: Player interacts with a placed WItemPosition that has an overlayModelId.
 *    The item disappears from the world, and the player's entity renders the overlay model.
 *    On release, a new WItemPosition is created at the player's current position.
 *
 * 2. Backpack mount: Player activates an item from backpack that has an overlayModelId.
 *    No world item involved — just overlay model on the player entity.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OccupationService implements SessionClosedConsumer {

    private final WItemPositionService itemPositionService;
    private final WEntityModelService entityModelService;
    private final EntityStatusPublisher entityStatusPublisher;
    private final ClientService clientService;
    private final PlayerService playerService;

    /**
     * Start occupation from a placed world item (WItemPosition).
     * The item must have an 'overlayModelId' parameter in its publicData or item definition.
     *
     * @param session     Player session
     * @param itemId      Item ID of the WItemPosition to occupy
     * @param overlayModelId EntityModel ID to use as overlay
     * @return true if occupation started successfully
     */
    public boolean occupyFromItem(PlayerSession session, String itemId, String overlayModelId) {
        if (!session.isAuthenticated()) {
            log.warn("Occupation attempt from unauthenticated session");
            return false;
        }
        if (session.getOccupiedItemId() != null) {
            log.warn("Player {} already in occupation, cannot occupy again", session.getEntityId());
            clientService.sendSystemNotification(session, "Occupation", "Already in an occupation. Release first.");
            return false;
        }
        if (StringUtils.isBlank(overlayModelId)) {
            log.warn("No overlayModelId provided for occupation");
            return false;
        }

        var worldId = session.getWorldId();

        // Validate entity model exists and has overlayMovement config
        var modelOpt = entityModelService.findByModelId(worldId, overlayModelId);
        if (modelOpt.isEmpty()) {
            log.warn("EntityModel not found for occupation: worldId={}, modelId={}", worldId, overlayModelId);
            clientService.sendSystemNotification(session, "Occupation", "Model not found.");
            return false;
        }
        EntityModel entityModel = modelOpt.get().getPublicData();
        if (entityModel == null || entityModel.getOverlayMovement() == null) {
            log.warn("EntityModel {} has no overlayMovement config", overlayModelId);
            clientService.sendSystemNotification(session, "Occupation", "This model cannot be used as overlay.");
            return false;
        }

        // Capture the item's full data BEFORE removing it, so it can be recreated
        // faithfully (texture/scale/title/...) on release or disconnect.
        ItemBlockRef occupiedRef = itemPositionService.findItem(worldId, itemId)
                .map(WItemPosition::getPublicData)
                .orElse(null);

        // Disable the WItemPosition (item disappears from world)
        boolean deleted = itemPositionService.deleteItemPosition(worldId, itemId);
        if (!deleted) {
            log.warn("Failed to disable WItemPosition: worldId={}, itemId={}", worldId, itemId);
            clientService.sendSystemNotification(session, "Occupation", "Item not found in world.");
            return false;
        }

        // Set occupation state on session
        session.setOccupiedItemId(itemId);
        session.setOccupiedItemRef(occupiedRef);
        session.setOccupiedModelId(overlayModelId);

        // Broadcast entity update: player now has overlay model
        broadcastPlayerOverlayUpdate(session, overlayModelId);

        log.info("Player {} occupied item {} with model {}", session.getEntityId(), itemId, overlayModelId);
        return true;
    }

    /**
     * Start overlay from backpack item (no world item involved).
     *
     * @param session        Player session
     * @param overlayModelId EntityModel ID to use as overlay
     * @return true if overlay started successfully
     */
    public boolean activateBackpackOverlay(PlayerSession session, String overlayModelId) {
        if (!session.isAuthenticated()) return false;
        if (session.getOccupiedModelId() != null) {
            clientService.sendSystemNotification(session, "Overlay", "Already active. Release first.");
            return false;
        }
        if (StringUtils.isBlank(overlayModelId)) return false;

        var worldId = session.getWorldId();
        var modelOpt = entityModelService.findByModelId(worldId, overlayModelId);
        if (modelOpt.isEmpty() || modelOpt.get().getPublicData() == null
                || modelOpt.get().getPublicData().getOverlayMovement() == null) {
            clientService.sendSystemNotification(session, "Overlay", "Model not found or not an overlay model.");
            return false;
        }

        session.setOccupiedItemId(null); // no world item
        session.setOccupiedModelId(overlayModelId);

        broadcastPlayerOverlayUpdate(session, overlayModelId);

        log.info("Player {} activated backpack overlay model {}", session.getEntityId(), overlayModelId);
        return true;
    }

    /**
     * Release current occupation/overlay.
     * If occupation was from a world item, creates a new WItemPosition at the player's current position.
     *
     * @param session Player session
     * @return true if released successfully
     */
    public boolean release(PlayerSession session) {
        if (!session.isAuthenticated()) return false;
        String occupiedModelId = session.getOccupiedModelId();
        if (occupiedModelId == null) {
            log.debug("Player {} has no active occupation to release", session.getEntityId());
            return false;
        }

        String occupiedItemId = session.getOccupiedItemId();

        // If this was a world item occupation, recreate the WItemPosition at current player position
        if (StringUtils.isNotBlank(occupiedItemId) && session.getLastPosition() != null) {
            recreateItemAtPosition(session, occupiedItemId, occupiedModelId);
        }

        // Clear occupation state
        session.setOccupiedItemId(null);
        session.setOccupiedItemRef(null);
        session.setOccupiedModelId(null);

        // Broadcast: player no longer has overlay
        broadcastPlayerOverlayUpdate(session, null);

        log.info("Player {} released occupation (model={}, item={})",
                session.getEntityId(), occupiedModelId, occupiedItemId);
        return true;
    }

    /**
     * Check if session has an active overlay.
     */
    public boolean hasActiveOverlay(PlayerSession session) {
        return session.getOccupiedModelId() != null;
    }

    /**
     * Get the active overlay model ID for a session.
     */
    public String getActiveOverlayModelId(PlayerSession session) {
        return session.getOccupiedModelId();
    }

    private void broadcastPlayerOverlayUpdate(PlayerSession session, String overlayModelId) {
        String entityId = session.getEntityId();
        String worldId = session.getWorldId().getId();

        // 1. Send command to own client for local overlay handling (model swap, physics override)
        if (overlayModelId != null) {
            clientService.sendCommand(session, "overlayModel", List.of(overlayModelId));
        } else {
            clientService.sendCommand(session, "overlayModel", List.of());
        }

        // 2. Broadcast entity status update to all other nearby clients (remote rendering)
        Map<String, Object> statusFields = Map.of(
                "overlayModel", overlayModelId != null ? overlayModelId : "",
                "overlayModelModifier", Map.of()
        );
        entityStatusPublisher.publishStatusUpdate(worldId, entityId, statusFields, session.getSessionId());
    }

    private void recreateItemAtPosition(PlayerSession session, String itemId, String modelId) {
        ItemBlockRef ref = session.getOccupiedItemRef();
        if (ref == null) {
            log.warn("Cannot recreate occupied item {} — no cached item data in session (item stays removed)", itemId);
            return;
        }
        // Recreate the item at the player's current position, preserving all other
        // data (texture/scale/title/...) captured when the item was occupied.
        ref.setName(itemId);
        ref.setPosition(session.getLastPosition());
        try {
            itemPositionService.saveItemPosition(session.getWorldId(), ref);
            log.info("Recreated occupied item {} at position {} for player {}",
                    itemId, session.getLastPosition(), session.getEntityId());
        } catch (Exception e) {
            log.error("Failed to recreate occupied item {} for player {}", itemId, session.getEntityId(), e);
        }
    }

    // --- SessionClosedConsumer ---

    /**
     * On disconnect, place any occupied world item back so it is not lost when the
     * (in-memory) session ends.
     */
    @Override
    public void onSessionClosed(PlayerSession session) {
        if (session.getOccupiedModelId() != null) {
            release(session);
        }
    }
}
