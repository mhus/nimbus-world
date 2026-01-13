package de.mhus.nimbus.world.player.ws.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import de.mhus.nimbus.generated.network.messages.ChunkRegisterData;
import de.mhus.nimbus.generated.types.EntityPathway;
import de.mhus.nimbus.world.player.ws.ChunkSenderService;
import de.mhus.nimbus.world.player.ws.ChunkSenderService.ChunkCoord;
import de.mhus.nimbus.world.player.ws.NetworkMessage;
import de.mhus.nimbus.world.player.ws.PathwayBroadcastService;
import de.mhus.nimbus.world.player.session.PlayerSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles chunk registration messages from clients.
 * Message type: "c.r"
 *
 * Clients register chunks they want to receive updates for.
 * Registration is based on player position and view distance.
 * Only registered chunks receive updates.
 *
 * Supports two formats:
 * - New format: {cx, cz, lr, hr} - center position + load range (creates rectangle)
 * - Old format: {c: [...]} - explicit array of chunk coordinates (for compatibility)
 *
 * Delta-based: Only newly registered chunks are sent to client.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChunkRegistrationHandler implements MessageHandler {

    private final ChunkSenderService chunkSenderService;
    private final PathwayBroadcastService pathwayBroadcastService;
    private final ObjectMapper objectMapper;
    private final de.mhus.nimbus.world.shared.redis.WorldRedisMessagingService redisMessaging;

    @Override
    public String getMessageType() {
        return "c.r";
    }

    @Override
    public void handle(PlayerSession session, NetworkMessage message) throws Exception {
        JsonNode data = message.getD();

        List<ChunkCoord> requestedChunks = new ArrayList<>();

        // Check for new format: cx, cz, lr (center + low (density) range)
        // TODO support hr (high (density) range) if needed
        if (data.has("cx") && data.has("cz") && data.has("lr")) {
            ChunkRegisterData registerData = objectMapper.treeToValue(data, ChunkRegisterData.class);

            int centerX = registerData.getCx();
            int centerZ = registerData.getCz();
            int lowDensityRange = registerData.getLr();

            // Create rectangle of chunks from center ± loadRange
            for (int x = centerX - lowDensityRange; x <= centerX + lowDensityRange; x++) {
                for (int z = centerZ - lowDensityRange; z <= centerZ + lowDensityRange; z++) {
                    requestedChunks.add(new ChunkCoord(x, z));
                }
            }

            log.debug("Chunk registration (new format): center=({}, {}), loadRange={}, total chunks={}",
                    centerX, centerZ, lowDensityRange, requestedChunks.size());

        } else {
            log.warn("Invalid chunk registration: missing 'cx/cz/lr'");
            return;
        }

        // Calculate delta (new chunks = requested - already registered)
        List<ChunkCoord> newChunks = new ArrayList<>();
        for (ChunkCoord coord : requestedChunks) {
            if (!session.isChunkRegistered(coord.cx(), coord.cz())) {
                newChunks.add(coord);
            }
        }

        // Update registration (replace with new list)
        session.clearChunks();
        for (ChunkCoord coord : requestedChunks) {
            session.registerChunk(coord.cx(), coord.cz());
        }

        log.debug("Chunk registration: session={}, total={}, new={}, worldId={}",
                session.getWebSocketSession().getId(), requestedChunks.size(),
                newChunks.size(), session.getWorldId());

        // Publish chunk registration to Redis for world-life
        if (!newChunks.isEmpty()) {
            publishChunkRegistrationUpdate(session.getWorldId().getId(), "add", newChunks);
        }

        // Asynchronously send new chunks to client
        if (!newChunks.isEmpty()) {
            chunkSenderService.sendChunksAsync(session, newChunks);
        }

        // Send cached pathways for newly registered chunks
        if (!newChunks.isEmpty() && session.getWorldId() != null) {
            sendCachedPathwaysForChunks(session, newChunks);
        }
    }

    /**
     * Send cached pathways for newly registered chunks.
     * This ensures new sessions immediately see existing entities.
     *
     * @param session Player session
     * @param chunks Newly registered chunks
     */
    private void sendCachedPathwaysForChunks(PlayerSession session, List<ChunkCoord> chunks) {
        try {
            String worldId = session.getWorldId().getId();
            List<EntityPathway> allPathways = new ArrayList<>();

            // Collect cached pathways for all new chunks
            for (ChunkCoord chunk : chunks) {
                List<EntityPathway> pathways = pathwayBroadcastService.getCachedPathwaysForChunk(
                        worldId, chunk.cx(), chunk.cz());
                allPathways.addAll(pathways);
            }

            // Send pathways to client if any found
            if (!allPathways.isEmpty()) {
                JsonNode pathwaysArray = objectMapper.valueToTree(allPathways);
                NetworkMessage pathwayMessage = NetworkMessage.builder()
                        .t("e.p")
                        .d(pathwaysArray)
                        .build();

                String json = objectMapper.writeValueAsString(pathwayMessage);
                session.getWebSocketSession().sendMessage(new TextMessage(json));

                log.debug("Sent {} cached pathways to session {} for {} new chunks",
                        allPathways.size(), session.getSessionId(), chunks.size());
            }

        } catch (Exception e) {
            log.error("Failed to send cached pathways for chunks", e);
        }
    }

    /**
     * Publish chunk registration update to Redis for world-life.
     * Channel: world:{worldId}:c.r
     *
     * @param worldId World identifier
     * @param action "add" or "remove"
     * @param chunks List of chunk coordinates
     */
    private void publishChunkRegistrationUpdate(String worldId, String action, List<ChunkCoord> chunks) {
        try {
            com.fasterxml.jackson.databind.node.ObjectNode message = objectMapper.createObjectNode();
            message.put("action", action);

            ArrayNode chunksArray = message.putArray("chunks");
            for (ChunkCoord chunk : chunks) {
                com.fasterxml.jackson.databind.node.ObjectNode chunkObj = chunksArray.addObject();
                chunkObj.put("cx", chunk.cx());
                chunkObj.put("cz", chunk.cz());
            }

            String json = objectMapper.writeValueAsString(message);
            redisMessaging.publish(worldId, "c.r", json);

            log.trace("Published chunk registration to Redis: action={}, chunks={}", action, chunks.size());

        } catch (Exception e) {
            log.error("Failed to publish chunk registration to Redis: action={}", action, e);
        }
    }
}

