package de.mhus.nimbus.world.player.ws.handlers;

import tools.jackson.databind.JsonNode;
import de.mhus.nimbus.world.player.service.GameplayService;
import de.mhus.nimbus.world.player.ws.NetworkMessage;
import de.mhus.nimbus.world.player.session.PlayerSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Handles simple interaction messages from clients (no block/entity target).
 * Message type: "int" (Simple Interaction, Client → Server)
 *
 * Sent when a shortcut fires but no block or entity is selected.
 *
 * Expected data:
 * {
 *   "ac": "click",   // action type
 *   "sc": "key1"     // shortcut key
 * }
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SimpleInteractionHandler implements MessageHandler {

    private final GameplayService gameplay;

    @Override
    public String getMessageType() {
        return "int";
    }

    @Override
    public void handle(PlayerSession session, NetworkMessage message) throws Exception {
        if (!session.isAuthenticated()) {
            log.warn("Simple interaction from unauthenticated session: {}",
                    session.getWebSocketSession().getId());
            return;
        }

        JsonNode data = message.getD();

        String action = data.has("ac") ? data.get("ac").asText() : null;
        String shortcutKey = data.has("sc") ? data.get("sc").asText() : null;

        if (action == null) {
            log.warn("Simple interaction without action or shortcutKey");
            return;
        }

        log.trace("Simple interaction received: action={}, shortcutKey={}, user={}",
                action, shortcutKey, session.getTitle());

        gameplay.onSimpleInteraction(session, action, shortcutKey, data);
    }

}
