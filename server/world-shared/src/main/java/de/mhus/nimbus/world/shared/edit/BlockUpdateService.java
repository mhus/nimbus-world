package de.mhus.nimbus.world.shared.edit;

import de.mhus.nimbus.generated.types.Block;
import de.mhus.nimbus.shared.engine.EngineMapper;
import de.mhus.nimbus.world.shared.redis.BlockUpdateBroadcastMessage;
import de.mhus.nimbus.world.shared.redis.WorldRedisMessagingService;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service for broadcasting block updates to world-player pods via Redis.
 * Used by block editor and copy/move operations to send "b.u" updates to clients.
 *
 * Block updates are broadcast via Redis pub/sub to all world-player pods,
 * which then distribute them to relevant WebSocket sessions based on audience filter.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BlockUpdateService {

    private final WorldRedisMessagingService redisMessaging;
    private final WWorldService worldService;
    private final EngineMapper objectMapper;

    /**
     * Send block update with optional source information.
     * Source is typically set to "layerDataId:layerName" for editor operations.
     *
     * @param worldId   World identifier
     * @param sessionId Session identifier (originating session, will be excluded from broadcast)
     * @param block     Block data
     * @param source    Source information (e.g., "layerDataId:layerName", optional)
     * @param meta      Block metadata (optional, currently unused)
     * @return true if broadcast was sent successfully
     */
    public boolean sendBlockUpdateWithSource(String worldId, String sessionId, Block block, String source, String meta) {
        // Set source field on block for legacy compatibility
        if (source != null && !source.isBlank()) {
            block.setSource(source);
        }

        return sendBlockUpdate(worldId, sessionId, block, source, meta);
    }

    /**
     * Send block update without source information.
     * Broadcasts to EDITOR actors only.
     */
    public boolean sendBlockUpdate(String worldId, String sessionId, Block block, String meta) {
        return sendBlockUpdate(worldId, sessionId, block, null, meta);
    }

    /**
     * Send block update with optional source.
     */
    private boolean sendBlockUpdate(String worldId, String sessionId, Block block, String source, String meta) {
        // Serialize block to JSON
        try {
            String blockJson = objectMapper.writeValueAsString(block);
            return sendBlockUpdate(worldId, sessionId,
                    block.getPosition().getX(),
                    block.getPosition().getY(),
                    block.getPosition().getZ(), blockJson, source, meta);
        } catch (Exception e) {
            log.error("Failed to serialize block for update: session={} pos=({})", sessionId, block.getPosition(), e);
            return false;
        }
    }

    /**
     * Send block update broadcast to all world-player pods via Redis.
     * The broadcast is received by all world-player instances and distributed to relevant WebSocket sessions.
     *
     * @param worldId   World identifier
     * @param sessionId Originating session identifier (will be excluded from receiving the broadcast)
     * @param x         Block X coordinate
     * @param y         Block Y coordinate
     * @param z         Block Z coordinate
     * @param blockJson Block data as JSON string
     * @param source    Source information (e.g., "layerDataId:layerName", optional)
     * @param meta      Block metadata (optional, currently unused)
     * @return true if broadcast was published successfully
     */
    private boolean sendBlockUpdate(String worldId, String sessionId, int x, int y, int z, String blockJson, String source, String meta) {
        try {
            // Get world to read chunk size
            WWorld world = worldService.getByWorldId(worldId)
                    .orElseThrow(() -> new IllegalStateException("World not found: " + worldId));

            // Read chunk size from world configuration (no default hardcoded value!)
            int chunkSize = world.getPublicData().getChunkSize();

            // Calculate chunk coordinates based on actual chunk size
            int cx = (int) Math.floor((double) x / chunkSize);
            int cz = (int) Math.floor((double) z / chunkSize);

            // Build broadcast message
            BlockUpdateBroadcastMessage broadcast = BlockUpdateBroadcastMessage.builder()
                    .worldId(worldId)
                    .blockJson(blockJson)
                    .targetAudience(BlockUpdateBroadcastMessage.AUDIENCE_EDITOR)
                    .originatingSessionId(sessionId)
                    .cx(cx)
                    .cz(cz)
                    .source(source)
                    .build();

            // Serialize and publish to Redis
            String messageJson = objectMapper.writeValueAsString(broadcast);
            redisMessaging.publish(worldId, "b.u", messageJson);

            log.debug("Broadcast block update via Redis: world={} chunkSize={} chunk=({},{}) pos=({},{},{}) origin={}",
                    worldId, chunkSize, cx, cz, x, y, z, sessionId);

            return true;

        } catch (Exception e) {
            log.error("Failed to broadcast block update: world={} session={} pos=({},{},{})",
                    worldId, sessionId, x, y, z, e);
            return false;
        }
    }
}
