package de.mhus.nimbus.world.player.gameplay;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.world.player.service.OccupationService;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.shared.world.WEntity;
import de.mhus.nimbus.world.shared.world.WItem;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Gameplay action for occupying entities/items (mounts, vehicles).
 *
 * Server parameters (from block/entity/item serverInfo):
 * - int_overlayModelId / act_overlayModelId: EntityModel ID to use as overlay
 *
 * Triggers:
 * - Block interaction with action="occupy" → occupies using the block's item position
 * - Entity interaction with action="occupy" → occupies using entity's overlay model
 * - Item interaction with action="occupy" → activates backpack overlay
 *
 * Release: Player sends "occupy" action again while already in occupation → release
 */
@Slf4j
public class OccupyAction extends AbstractGamplayAction {

    private final OccupationService occupationService;

    public OccupyAction(BasicGameplay basic, OccupationService occupationService) {
        super(basic);
        this.occupationService = occupationService;
    }

    @Override
    public boolean handleAction(PlayerSession session, Map<String, String> serverParameters, JsonNode params) {
        // If player is already in occupation → release
        if (occupationService.hasActiveOverlay(session)) {
            log.info("Player {} releasing occupation via action", session.getEntityId());
            return occupationService.release(session);
        }

        String overlayModelId = serverParameters.get("overlayModelId");
        if (overlayModelId == null || overlayModelId.isBlank()) {
            log.warn("Occupy action without overlayModelId for player {}", session.getEntityId());
            basic.getBasicClientService().sendSystemNotification(session, "Occupy", "No overlay model configured.");
            return false;
        }

        // Determine source: item position ID from params (if block/item occupation)
        String itemId = params != null && params.has("itemId") ? params.get("itemId").asText() : null;

        if (itemId != null && !itemId.isBlank()) {
            // Occupation from world item (WItemPosition)
            log.info("Player {} occupying item {} with model {}", session.getEntityId(), itemId, overlayModelId);
            return occupationService.occupyFromItem(session, itemId, overlayModelId);
        } else {
            // Backpack overlay (no world item)
            log.info("Player {} activating backpack overlay model {}", session.getEntityId(), overlayModelId);
            return occupationService.activateBackpackOverlay(session, overlayModelId);
        }
    }

    @Override
    public boolean handleItemAction(PlayerSession session, WItem item, String itemAction, JsonNode params) {
        // Item from backpack: use item's server parameters for overlayModelId
        String overlayModelId = item.getServer() != null ? item.getServer().get("overlayModelId") : null;
        if (overlayModelId == null || overlayModelId.isBlank()) {
            // Also check item publicData parameters
            if (item.getPublicData() != null && item.getPublicData().getParameters() != null) {
                overlayModelId = item.getPublicData().getParameters().get("overlayModelId");
            }
        }

        if (overlayModelId == null || overlayModelId.isBlank()) {
            log.warn("Item {} has no overlayModelId", item.getItemId());
            basic.getBasicClientService().sendSystemNotification(session, "Occupy", "This item cannot be used as mount.");
            return false;
        }

        // If already in occupation → release
        if (occupationService.hasActiveOverlay(session)) {
            return occupationService.release(session);
        }

        return occupationService.activateBackpackOverlay(session, overlayModelId);
    }

    @Override
    public boolean handleEntityAction(PlayerSession session, WEntity entity, String userAction, String entityAction, String shortcutKey, JsonNode params) {
        // Entity occupation: overlayModelId from entity server metadata
        String overlayModelId = entity.getServer() != null ? entity.getServer().get("overlayModelId") : null;
        if (overlayModelId == null || overlayModelId.isBlank()) {
            log.warn("Entity {} has no overlayModelId in server metadata", entity.getName());
            basic.getBasicClientService().sendSystemNotification(session, "Occupy", "This entity cannot be occupied.");
            return false;
        }

        // If already in occupation → release
        if (occupationService.hasActiveOverlay(session)) {
            return occupationService.release(session);
        }

        // For entity-based occupation we use backpack overlay (entity stays as-is for now)
        // Future: could disable entity via world-life
        return occupationService.activateBackpackOverlay(session, overlayModelId);
    }
}
