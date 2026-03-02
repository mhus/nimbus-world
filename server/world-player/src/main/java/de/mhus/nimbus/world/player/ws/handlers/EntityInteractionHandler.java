package de.mhus.nimbus.world.player.ws.handlers;

import com.fasterxml.jackson.databind.JsonNode;
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
 * Server processes the interaction (currently just logging).
 *
 * Expected data:
 * {
 *   "entityId": "npc_farmer_001",
 *   "ts": 1697045600000,  // timestamp
 *   "ac": "click",  // action: 'click', 'fireShortcut', 'use', 'talk', 'attack', 'touch', etc.
 *   "pa": {  // params
 *     "clickType": "left",  // for 'click' action
 *     "shortcutNr": 2,      // for 'fireShortcut' action
 *     ...
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
        String shortcut = data.has("sc") ? data.get("sc").asText() : null;
        JsonNode params = data.has("pa") ? data.get("pa") : null;

        if (entityId == null || shortcut == null) {
            log.warn("Entity interaction without entityId or action");
            return;
        }

        log.trace("Entity interaction received: entityId={}, shortcut={}, user={}",
                entityId, shortcut, session.getTitle());

        if (entityId.startsWith("@")) {
            // this is a player d not send to life server, send to gameplay service
            gameplay.onPlayerPlayerInteraction(session, entityId, shortcut, timestamp, params);
        } else {
            gameplay.onPlayerEntityInteraction(session, entityId, shortcut, timestamp, params);
        }

        log.debug("Entity interaction forwarded to world-life: entityId={}, shortcut={}, user={}",
                entityId, shortcut, session.getTitle());
    }

}
