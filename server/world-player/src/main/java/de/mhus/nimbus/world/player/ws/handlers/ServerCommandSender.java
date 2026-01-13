package de.mhus.nimbus.world.player.ws.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.mhus.nimbus.world.player.ws.NetworkMessage;
import de.mhus.nimbus.world.player.session.PlayerSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;

/**
 * Service for sending server commands to clients.
 * Message types: "s.c" (Server Command, Server → Client)
 *                "s.mc" (Multiple Commands, Server → Client)
 *
 * Server commands are game logic instructions sent from server to client.
 * Examples:
 * - Display message
 * - Play sound
 * - Show UI element
 * - Trigger effect
 * - Set game state
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ServerCommandSender {

    private final ObjectMapper objectMapper;

    /**
     * Send single server command to session.
     *
     * @param session Player session
     * @param commandType Command type (e.g., "message", "sound", "effect")
     * @param commandData Command-specific data
     */
    public void sendCommand(PlayerSession session, String commandType, ObjectNode commandData) {
        try {
            ObjectNode data = objectMapper.createObjectNode();
            data.put("type", commandType);
            data.set("data", commandData);

            NetworkMessage message = NetworkMessage.builder()
                    .t("s.c")
                    .d(data)
                    .build();

            String json = objectMapper.writeValueAsString(message);
            session.getWebSocketSession().sendMessage(new TextMessage(json));

            log.debug("Sent server command '{}' to session: {}",
                    commandType, session.getWebSocketSession().getId());

        } catch (Exception e) {
            log.error("Failed to send server command to session: {}",
                    session.getWebSocketSession().getId(), e);
        }
    }

    /**
     * Send chat message to session.
     *
     * @param session Player session
     * @param message Message text
     * @param sender  Sender name (or "Server")
     */
    public void sendChatMessage(PlayerSession session, String message, String sender) {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("message", message);
        data.put("sender", sender != null ? sender : "Server");
        data.put("timestamp", System.currentTimeMillis());

        sendCommand(session, "chat", data);
    }

    /**
     * Send notification to session.
     *
     * @param session Player session
     * @param title   Notification title
     * @param message Notification message
     * @param level   Level: "info", "success", "warning", "error"
     */
    public void sendNotification(PlayerSession session, String title, String message, String level) {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("title", title);
        data.put("message", message);
        data.put("level", level != null ? level : "info");

        sendCommand(session, "notification", data);
    }

    /**
     * Send effect trigger to session.
     *
     * @param session    Player session
     * @param effectType Effect type (e.g., "explosion", "particles")
     * @param x          World X coordinate
     * @param y          World Y coordinate
     * @param z          World Z coordinate
     */
    public void sendEffect(PlayerSession session, String effectType, double x, double y, double z) {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("effectType", effectType);

        ObjectNode position = objectMapper.createObjectNode();
        position.put("x", x);
        position.put("y", y);
        position.put("z", z);
        data.set("position", position);

        sendCommand(session, "effect", data);
    }
}
