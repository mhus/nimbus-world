package de.mhus.nimbus.world.player.ws.handlers;

import tools.jackson.databind.JsonNode;
import de.mhus.nimbus.world.player.service.GameplayService;
import de.mhus.nimbus.world.player.ws.NetworkMessage;
import de.mhus.nimbus.world.player.session.PlayerSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Handles entity interaction messages from clients.
 * Message type: "e.int.r" (Entity Interaction Request, Client → Server)
 *
 * Client sends entity interactions when player interacts with NPCs or entities.
 *
 * Expected data:
 * {
 *   "entityId": "npc_farmer_001",
 *   "ts": 1697045600000,
 *   "ac": "interact" | "fireShortcut" | "hitDuringShortcut",
 *   "pa": {
 *     "shortcutNr": 2,           // for 'fireShortcut'
 *     "shortcutItemId": "potion1" // for 'fireShortcut'
 *   }
 * }
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EntityInteractionHandler implements MessageHandler {

    private final GameplayService gameplay;

    @Override
    public String getMessageType() {
        return "e.int.r";
    }

    @Override
    public void handle(PlayerSession session, NetworkMessage message) throws Exception {
        if (!session.isAuthenticated()) {
            log.warn("Entity interaction from unauthenticated session: {}",
                    session.getWebSocketSession().getId());
            return;
        }

        JsonNode data = message.getD();

        // Extract interaction data
        String entityId = data.has("entityId") ? data.get("entityId").asText() : null;
        Long timestamp = data.has("ts") ? data.get("ts").asLong() : null;
        String userAction = data.has("ac") ? data.get("ac").asText() : null;
        String shortcutKey = data.has("sc") ? data.get("sc").asText() : null;
        JsonNode params = data.has("pa") ? data.get("pa") : null;

        if (entityId == null || userAction == null) {
            log.warn("Entity interaction without entityId or action");
            return;
        }

        log.trace("Entity interaction received: entityId={}, action={}, shortcut={}, user={}",
                entityId, userAction, shortcutKey, session.getTitle());

        if (entityId.startsWith("@")) {
            gameplay.onPlayerPlayerInteraction(session, entityId, userAction, shortcutKey, timestamp, params);
        } else {
            gameplay.onPlayerEntityInteraction(session, entityId, userAction, shortcutKey, timestamp, params);
        }
    }

}
