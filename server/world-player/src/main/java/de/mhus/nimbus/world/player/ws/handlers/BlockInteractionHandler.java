package de.mhus.nimbus.world.player.ws.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.world.player.service.GameplayService;
import de.mhus.nimbus.world.player.ws.NetworkMessage;
import de.mhus.nimbus.world.player.session.PlayerSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Handles block interaction messages from clients.
 * Message type: "b.int" (Block Interaction, Client → Server)
 *
 * Client sends block interactions when player interacts with blocks.
 * Actions: 'click', 'collision', 'climb', 'fireShortcut'
 *
 * Expected data:
 * {
 *   "x": 10,
 *   "y": 64,
 *   "z": 10,
 *   "id": "123",              // block.metadata.id, optional
 *   "gId": "123",             // groupId of block, optional
 *   "ac": "click",            // action: 'click', 'collision', 'climb', 'fireShortcut'
 *   "pa": {                   // params
 *     "clickType": "left"     // only for 'click'
 *   }
 * }
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class BlockInteractionHandler implements MessageHandler {

    private final GameplayService gameplay;

    @Override
    public String getMessageType() {
        return "b.int";
    }

    @Override
    public void handle(PlayerSession session, NetworkMessage message) throws Exception {
        if (!session.isAuthenticated()) {
            log.warn("Block interaction from unauthenticated session: {}",
                    session.getWebSocketSession().getId());
            return;
        }

        JsonNode data = message.getD();

        // Extract required fields
        int x = data.get("x").asInt();
        int y = data.get("y").asInt();
        int z = data.get("z").asInt();
        String action = data.get("ac").asText();

        // Extract optional fields
        String blockId = data.has("id") ? data.get("id").asText() : null;
        String groupId = data.has("gId") ? data.get("gId").asText() : null;
        JsonNode params = data.has("pa") ? data.get("pa") : null;

        // Log interaction for now - implementation will follow later
        log.trace("Block interaction - Session: {}, Position: ({},{},{}), Action: {}, BlockId: {}, GroupId: {}, Params: {}",
                session.getWebSocketSession().getId(),
                x, y, z,
                action,
                blockId,
                groupId,
                params != null ? params.toString() : "none");

        gameplay.onPlayerBlockInteraction(session, x, y, z, blockId, groupId, action, params);

    }


}
