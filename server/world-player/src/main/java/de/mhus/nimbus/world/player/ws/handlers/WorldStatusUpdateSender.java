package de.mhus.nimbus.world.player.ws.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.mhus.nimbus.world.player.ws.NetworkMessage;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.player.ws.SessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;

/**
 * Service for sending world status updates to clients.
 * Message type: "w.su" (World Status Update, Server → Client)
 *
 * When world status changes, all clients need to re-render affected chunks.
 * Status typically controls:
 * - Day/night cycle
 * - Weather
 * - Season
 * - Block modifier states
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorldStatusUpdateSender {

    private final ObjectMapper objectMapper;
    private final SessionManager sessionManager;

    /**
     * Broadcast world status update to all sessions in a world.
     *
     * @param worldId   World identifier
     * @param newStatus New status value
     */
    public void broadcastStatusUpdate(String worldId, int newStatus) {
        try {
            ObjectNode data = objectMapper.createObjectNode();
            data.put("s", newStatus);

            NetworkMessage message = NetworkMessage.builder()
                    .t("w.su")
                    .d(data)
                    .build();

            String json = objectMapper.writeValueAsString(message);
            TextMessage textMessage = new TextMessage(json);

            int sentCount = 0;
            for (PlayerSession session : sessionManager.getAllSessions().values()) {
                if (worldId.equals(session.getWorldId()) && session.isAuthenticated()) {
                    session.getWebSocketSession().sendMessage(textMessage);
                    sentCount++;
                }
            }

            log.info("Broadcast world status update to {} sessions: world={}, status={}",
                    sentCount, worldId, newStatus);

        } catch (Exception e) {
            log.error("Failed to broadcast world status update: worldId={}", worldId, e);
        }
    }
}
