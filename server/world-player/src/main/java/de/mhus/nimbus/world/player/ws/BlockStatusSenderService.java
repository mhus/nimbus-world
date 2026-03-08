package de.mhus.nimbus.world.player.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.generated.network.messages.BlockProgressStatusData;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.shared.utils.TypeUtil;
import de.mhus.nimbus.world.shared.world.WProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;

import java.util.Map;

/**
 * Service for sending block progress status updates to clients.
 * Message type: "b.ps" (Block Progress Status, Server → Client)
 *
 * Sends block status changes (e.g., door open/closed) to clients
 * who have the affected chunk registered.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BlockStatusSenderService {

    private final ObjectMapper objectMapper;
    private final SessionManager sessionManager;
    private final WProgressService progressService;

    /**
     * Set a block status and broadcast the change to all sessions viewing the chunk.
     *
     * @param worldId  World identifier
     * @param chunkKey Chunk key (e.g. "1:2")
     * @param cx       Chunk X coordinate
     * @param cz       Chunk Z coordinate
     * @param blockKey Block identifier (blockId string)
     * @param status   New status value (e.g. "open", "closed")
     */
    public void setAndBroadcast(String worldId, String chunkKey, int cx, int cz, String blockKey, String status) {
        progressService.setBlockStatus(worldId, chunkKey, blockKey, status);
        broadcastStatusUpdate(worldId, cx, cz, Map.of(blockKey, status));
    }

    /**
     * Set a block status and broadcast the change.
     * Chunk coordinates are parsed from the chunkKey.
     *
     * @param worldId  World identifier
     * @param chunkKey Chunk key (e.g. "1:2")
     * @param blockKey Block identifier (blockId string)
     * @param status   New status value (e.g. "open", "closed")
     */
    public void setAndBroadcast(String worldId, String chunkKey, String blockKey, String status) {
        int[] coord = TypeUtil.parseChunkCoord(chunkKey);
        setAndBroadcast(worldId, chunkKey, coord[0], coord[1], blockKey, status);
    }

    /**
     * Remove a block status and broadcast the removal to all sessions viewing the chunk.
     *
     * @param worldId  World identifier
     * @param chunkKey Chunk key (e.g. "1:2")
     * @param cx       Chunk X coordinate
     * @param cz       Chunk Z coordinate
     * @param blockKey Block identifier (blockId string)
     */
    public void removeAndBroadcast(String worldId, String chunkKey, int cx, int cz, String blockKey) {
        progressService.removeBlockStatus(worldId, chunkKey, blockKey);
        broadcastStatusRemoval(worldId, cx, cz, blockKey);
    }

    /**
     * Broadcast a block status update to all sessions that have the chunk registered.
     *
     * @param worldId World identifier
     * @param cx      Chunk X coordinate
     * @param cz      Chunk Z coordinate
     * @param statusMap blockId -> status
     */
    public void broadcastStatusUpdate(String worldId, int cx, int cz, Map<String, String> statusMap) {
        if (statusMap == null || statusMap.isEmpty()) return;

        BlockProgressStatusData data = BlockProgressStatusData.builder()
                .cx(cx)
                .cz(cz)
                .s(statusMap)
                .build();

        sendToChunkSessions(worldId, cx, cz, data);
    }

    /**
     * Broadcast a block status removal to all sessions that have the chunk registered.
     * Sends null as the status value to indicate removal.
     *
     * @param worldId  World identifier
     * @param cx       Chunk X coordinate
     * @param cz       Chunk Z coordinate
     * @param blockKey Block identifier to remove
     */
    public void broadcastStatusRemoval(String worldId, int cx, int cz, String blockKey) {
        // Send with null value to indicate removal
        BlockProgressStatusData data = BlockProgressStatusData.builder()
                .cx(cx)
                .cz(cz)
                .s(new java.util.HashMap<>() {{ put(blockKey, null); }})
                .build();

        sendToChunkSessions(worldId, cx, cz, data);
    }

    private void sendToChunkSessions(String worldId, int cx, int cz, BlockProgressStatusData data) {
        try {
            NetworkMessage message = NetworkMessage.builder()
                    .t("b.ps")
                    .d(objectMapper.valueToTree(data))
                    .build();

            String json = objectMapper.writeValueAsString(message);
            TextMessage textMessage = new TextMessage(json);

            int sentCount = 0;
            for (PlayerSession session : sessionManager.getAllSessions().values()) {
                if (session.isAuthenticated() &&
                    worldId.equals(session.getWorldId().getId()) &&
                    session.isChunkRegistered(cx, cz)) {

                    session.sendMessage(textMessage);
                    sentCount++;
                }
            }

            log.debug("Broadcast block status update to {} sessions: worldId={}, chunk=({},{}), entries={}",
                    sentCount, worldId, cx, cz, data.getS().size());

        } catch (Exception e) {
            log.error("Failed to broadcast block status update: worldId={}, chunk=({},{})",
                    worldId, cx, cz, e);
        }
    }
}
