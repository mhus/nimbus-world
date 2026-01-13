package de.mhus.nimbus.world.player.ws.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.mhus.nimbus.world.player.service.ExecutionService;
import de.mhus.nimbus.world.player.session.SessionPingConsumer;
import de.mhus.nimbus.world.player.ws.NetworkMessage;
import de.mhus.nimbus.world.player.session.PlayerSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;

import java.util.List;

/**
 * Handles ping messages from clients.
 * Message type: "p"
 *
 * Clients send regular pings to keep connection alive and measure latency.
 * Server responds with pong including both client and server timestamps.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PingHandler implements MessageHandler {

    private final ObjectMapper objectMapper;
    private final ExecutionService executionService;

    @Autowired
    @Lazy
    private List<SessionPingConsumer> pingConsumers;

    @Override
    public String getMessageType() {
        return "p";
    }

    @Override
    public void handle(PlayerSession session, NetworkMessage message) throws Exception {
        JsonNode data = message.getD();

        // Extract client timestamp
        long clientTimestamp = data.has("cTs") ? data.get("cTs").asLong() : System.currentTimeMillis();

        // Update session last ping time
        session.touch();

        // Send pong response
        ObjectNode responseData = objectMapper.createObjectNode();
        responseData.put("cTs", clientTimestamp);  // Echo client timestamp
        responseData.put("sTs", System.currentTimeMillis());  // Add server timestamp

        NetworkMessage response = NetworkMessage.builder()
                .r(message.getI())
                .t("p")
                .d(responseData)
                .build();

        String json = objectMapper.writeValueAsString(response);
        session.getWebSocketSession().sendMessage(new TextMessage(json));

        executionService.execute(() -> {
            for (var consumer : pingConsumers) {
                try {
                    var result = consumer.onSessionPing(session);
                    if (result != null) {
                        if (!processSessionPingResult(session, consumer, result)) {
                            break;
                        }
                    }
                } catch (Exception e) {
                    log.error("Error in PingConsumer {}", consumer.getClass().getName(), e);
                }
            }
        });

        log.trace("Ping/pong: session={}, clientTs={}, latency={}ms",
                session.getWebSocketSession().getId(),
                clientTimestamp,
                System.currentTimeMillis() - clientTimestamp);
    }

    private boolean processSessionPingResult(PlayerSession session, SessionPingConsumer consumer, SessionPingConsumer.ACTION result) {
        if (result != null && result == SessionPingConsumer.ACTION.DISCONNECT) {
            log.warn("Session {} disconnected due to PingConsumer {} request",
                    session.getWebSocketSession().getId(),
                    consumer.getClass().getName());
            try {
                session.getWebSocketSession().close();
            } catch (Exception e) {
                log.error("Error closing session {}", session.getWebSocketSession().getId(), e);
            }
            return false;
        }
        return true;
    }
}
