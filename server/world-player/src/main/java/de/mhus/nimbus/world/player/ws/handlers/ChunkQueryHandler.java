package de.mhus.nimbus.world.player.ws.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import de.mhus.nimbus.generated.network.messages.ChunkDataTransferObject;
import de.mhus.nimbus.world.player.ws.NetworkMessage;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.shared.world.WChunkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;

/**
 * Handles chunk query messages from clients.
 * Message type: "c.q"
 *
 * Clients request specific chunks to be sent.
 * Server responds with chunk data via "c.u" (chunk update) message.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChunkQueryHandler implements MessageHandler {

    private final ObjectMapper objectMapper;
    private final WChunkService chunkService;

    @Override
    public String getMessageType() {
        return "c.q";
    }

    @Override
    public void handle(PlayerSession session, NetworkMessage message) throws Exception {
        JsonNode data = message.getD();

        if (!data.has("c") || !data.get("c").isArray()) {
            log.warn("Invalid chunk query: missing 'c' array");
            return;
        }

        // Extract requested chunks
        JsonNode chunksArray = data.get("c");
        ArrayNode responseChunks = objectMapper.createArrayNode();

        for (JsonNode chunkNode : chunksArray) {
            int cx = chunkNode.has("x") ? chunkNode.get("x").asInt() : 0;
            int cz = chunkNode.has("z") ? chunkNode.get("z").asInt() : 0;

            // Load chunk entity first
            String chunkKey = cx + ":" + cz;
            var chunkOpt = chunkService.find(session.getWorldId(), chunkKey);

            if (chunkOpt.isEmpty()) {
                // Generate default chunk if not found
                var chunkData = chunkService.loadChunkData(session.getWorldId(), chunkKey, true);
                if (chunkData.isPresent()) {
                    var saved = chunkService.saveChunk(session.getWorldId(), chunkKey, chunkData.get());
                    chunkOpt = java.util.Optional.of(saved);
                } else {
                    log.debug("Chunk not found and could not generate: cx={}, cz={}", cx, cz);
                    continue;
                }
            }

            var chunk = chunkOpt.get();

            // Convert to transfer object (uses compressed storage if available)
            ChunkDataTransferObject dto = chunkService.toTransferObject(session.getWorldId(), chunk);
            if (dto != null) {
                responseChunks.add(objectMapper.valueToTree(dto));
                log.trace("Loaded chunk: cx={}, cz={}, worldId={}, compressed={}",
                        cx, cz, session.getWorldId(), chunk.isCompressed());
            }
        }

        // Send chunk update response
        if (responseChunks.size() > 0) {
            NetworkMessage response = NetworkMessage.builder()
                    .t("c.u")  // Chunk update message type
                    .d(responseChunks)
                    .build();

            String json = objectMapper.writeValueAsString(response);
            session.getWebSocketSession().sendMessage(new TextMessage(json));

            log.debug("Sent {} chunks to session={}", responseChunks.size(),
                    session.getWebSocketSession().getId());
        }
    }
}
