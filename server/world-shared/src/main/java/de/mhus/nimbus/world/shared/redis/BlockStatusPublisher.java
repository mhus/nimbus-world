package de.mhus.nimbus.world.shared.redis;

import tools.jackson.databind.ObjectMapper;
import de.mhus.nimbus.shared.utils.TypeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Publishes block status changes via Redis to all world-player pods.
 * Channel: world:{worldId}:b.ps
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BlockStatusPublisher {

    private static final String CHANNEL = "b.ps";

    private final WorldRedisMessagingService redisMessaging;
    private final ObjectMapper objectMapper;

    /**
     * Publish a block status change.
     *
     * @param worldId  World identifier
     * @param chunkKey Chunk key (e.g. "1:2")
     * @param blockKey Block position key ("x,y,z")
     * @param status   New status value, or null for removal
     */
    public void publishStatusChange(String worldId, String chunkKey, String blockKey, String status) {
        try {
            int[] coord = TypeUtil.parseChunkCoord(chunkKey);

            BlockStatusBroadcastMessage message = BlockStatusBroadcastMessage.builder()
                    .worldId(worldId)
                    .chunkKey(chunkKey)
                    .cx(coord[0])
                    .cz(coord[1])
                    .statusEntries(Map.of(blockKey, status != null ? status : ""))
                    .build();

            String json = objectMapper.writeValueAsString(message);
            redisMessaging.publish(worldId, CHANNEL, json);

            log.debug("Published block status change: worldId={}, chunk={}, block={}, status={}",
                    worldId, chunkKey, blockKey, status);
        } catch (Exception e) {
            log.error("Failed to publish block status change: worldId={}, chunk={}, block={}",
                    worldId, chunkKey, blockKey, e);
        }
    }
}
