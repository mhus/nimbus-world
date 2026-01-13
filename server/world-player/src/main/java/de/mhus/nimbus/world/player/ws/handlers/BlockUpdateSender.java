package de.mhus.nimbus.world.player.ws.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import de.mhus.nimbus.world.player.ws.NetworkMessage;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.player.ws.SessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;

import java.util.List;

/**
 * Service for sending block updates to clients.
 * Message type: "b.u" (Block Update, Server → Client)
 *
 * Sends block changes to clients who have registered the affected chunks.
 * Can be used for:
 * - Player block interactions (break, place)
 * - Server-side world changes
 * - Synchronized updates across clients
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BlockUpdateSender {

    private final ObjectMapper objectMapper;
    private final SessionManager sessionManager;

    /**
     * Send block updates to a specific session.
     *
     * @param session Player session
     * @param blocks  List of BlockData objects to send
     */
    public void sendToSession(PlayerSession session, List<JsonNode> blocks) {
        try {
            if (blocks == null || blocks.isEmpty()) {
                return;
            }

            ArrayNode blocksArray = objectMapper.createArrayNode();
            blocks.forEach(blocksArray::add);

            NetworkMessage message = NetworkMessage.builder()
                    .t("b.u")
                    .d(blocksArray)
                    .build();

            String json = objectMapper.writeValueAsString(message);
            session.getWebSocketSession().sendMessage(new TextMessage(json));

            log.debug("Sent {} block updates to session: {}",
                    blocks.size(), session.getWebSocketSession().getId());

        } catch (Exception e) {
            log.error("Failed to send block updates to session: {}",
                    session.getWebSocketSession().getId(), e);
        }
    }

    /**
     * Broadcast block updates to all sessions in a world that have the chunk registered.
     *
     * @param worldId World identifier
     * @param cx      Chunk X coordinate
     * @param cz      Chunk Z coordinate
     * @param blocks  List of BlockData objects to send
     */
    public void broadcastToChunk(String worldId, int cx, int cz, List<JsonNode> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return;
        }

        int sentCount = 0;
        for (PlayerSession session : sessionManager.getAllSessions().values()) {
            // Check if session is in the same world and has chunk registered
            if (worldId.equals(session.getWorldId()) &&
                session.isAuthenticated() &&
                session.isChunkRegistered(cx, cz)) {

                sendToSession(session, blocks);
                sentCount++;
            }
        }

        log.debug("Broadcast {} block updates to {} sessions in world {} chunk ({}, {})",
                blocks.size(), sentCount, worldId, cx, cz);
    }

    /**
     * Broadcast block updates to all authenticated sessions in a world.
     *
     * @param worldId World identifier
     * @param blocks  List of BlockData objects to send
     */
    public void broadcastToWorld(String worldId, List<JsonNode> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return;
        }

        int sentCount = 0;
        for (PlayerSession session : sessionManager.getAllSessions().values()) {
            if (worldId.equals(session.getWorldId()) && session.isAuthenticated()) {
                sendToSession(session, blocks);
                sentCount++;
            }
        }

        log.debug("Broadcast {} block updates to {} sessions in world {}",
                blocks.size(), sentCount, worldId);
    }
}
