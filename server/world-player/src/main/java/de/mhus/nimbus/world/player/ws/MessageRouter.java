package de.mhus.nimbus.world.player.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.player.ws.handlers.MessageHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Routes incoming WebSocket messages to appropriate handlers.
 */
@Service
@Slf4j
public class MessageRouter {

    private final ObjectMapper objectMapper;
    private final Map<String, MessageHandler> handlers;

    public MessageRouter(ObjectMapper objectMapper, List<MessageHandler> handlerList) {
        this.objectMapper = objectMapper;
        this.handlers = handlerList.stream()
                .collect(Collectors.toMap(MessageHandler::getMessageType, Function.identity()));
        log.info("Registered {} message handlers: {}", handlers.size(), handlers.keySet());
    }

    /**
     * Route incoming text message to appropriate handler.
     *
     * @param session  Player session
     * @param message  Raw WebSocket message
     */
    public void route(PlayerSession session, TextMessage message) {
        try {
            String payload = message.getPayload();
            log.trace("Received message from {}: {}", session.getWebSocketSession().getId(),
                     payload.length() > 200 ? payload.substring(0, 200) + "..." : payload);

            // Parse network message
            NetworkMessage networkMessage = objectMapper.readValue(payload, NetworkMessage.class);

            if (networkMessage.getT() == null) {
                log.warn("Message without type from {} - ignoring", session.getWebSocketSession().getId());
                return;
            }

            // Find handler
            MessageHandler handler = handlers.get(networkMessage.getT());
            if (handler == null) {
                log.warn("No handler for message type: {} - ignoring", networkMessage.getT());
                return;
            }

            // Handle message
            handler.handle(session, networkMessage);

        } catch (Exception e) {
            log.error("Error routing message from {} - ignoring message", session.getWebSocketSession().getId(), e);
        }
    }

}
