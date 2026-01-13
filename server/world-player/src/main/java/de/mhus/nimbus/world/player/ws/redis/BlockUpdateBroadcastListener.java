package de.mhus.nimbus.world.player.ws.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.generated.types.Block;
import de.mhus.nimbus.shared.engine.EngineMapper;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.player.ws.NetworkMessage;
import de.mhus.nimbus.world.player.ws.SessionManager;
import de.mhus.nimbus.world.shared.redis.BlockUpdateBroadcastMessage;
import de.mhus.nimbus.world.shared.redis.WorldRedisMessagingService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;

/**
 * Redis listener for block update broadcasts.
 * Receives block updates from world-control and distributes to connected clients via WebSocket.
 *
 * Redis channel: world:{worldId}:b.u
 * Client message type: "b.u" (Block Update)
 *
 * Supports audience filtering:
 * - ALL: Send to all authenticated sessions in the world
 * - EDITOR: Send only to sessions with actor=EDITOR
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BlockUpdateBroadcastListener {

    private final WorldRedisMessagingService redisMessaging;
    private final SessionManager sessionManager;
    private final ObjectMapper objectMapper;
    private final EngineMapper engineMapper;

    // Track which worlds are already subscribed
    private final java.util.Set<String> subscribedWorlds = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Subscribe to all active worlds on startup.
     * Dynamically subscribes when sessions connect to new worlds.
     */
    @PostConstruct
    public void subscribeToWorlds() {
        // Initial subscriptions will happen when sessions connect
        log.info("BlockUpdateBroadcastListener initialized - will subscribe to worlds dynamically");
    }

    /**
     * Subscribe to block update events for a specific world.
     * Automatically called when handling block updates for a new world.
     * Thread-safe - can be called from multiple threads.
     */
    public void subscribeToWorld(String worldId) {
        // Extract base worldId without instance
        String baseWorldId = de.mhus.nimbus.shared.types.WorldId.unchecked(worldId).withoutInstance().getId();

        // Check if already subscribed
        if (subscribedWorlds.contains(baseWorldId)) {
            log.trace("Already subscribed to block updates for world: {}", baseWorldId);
            return;
        }

        // Use synchronized to prevent race condition
        synchronized (subscribedWorlds) {
            // Double-check after acquiring lock
            if (subscribedWorlds.contains(baseWorldId)) {
                return;
            }

            redisMessaging.subscribe(baseWorldId, "b.u", (topic, message) -> {
                handleBlockUpdate(baseWorldId, message);
            });

            subscribedWorlds.add(baseWorldId);
            log.info("Subscribed to block update events for world: {}", baseWorldId);
        }
    }

    /**
     * Handle incoming block update event from Redis.
     * Distributes block updates to relevant sessions based on audience filter.
     */
    private void handleBlockUpdate(String worldId, String message) {
        try {
            // Deserialize broadcast message
            BlockUpdateBroadcastMessage broadcast = objectMapper.readValue(message, BlockUpdateBroadcastMessage.class);

            log.debug("Received block update broadcast: world={}, audience={}, origin={}",
                    worldId, broadcast.getTargetAudience(), broadcast.getOriginatingSessionId());

            // Validate block JSON
            if (broadcast.getBlockJson() == null || broadcast.getBlockJson().isBlank()) {
                log.warn("Block update broadcast has empty blockJson");
                return;
            }

            // Parse and validate block data using EngineMapper
            String blockData = broadcast.getBlockJson().trim();

            // Ensure it's an array format for consistency
            if (blockData.startsWith("{") && blockData.endsWith("}")) {
                blockData = "[" + blockData + "]";
            }
            if (!blockData.startsWith("[") || !blockData.endsWith("]")) {
                log.warn("Block data has invalid format: {}", blockData);
                return;
            }

            // Deserialize to Block DTO array for validation
            Block[] blocks = engineMapper.readValue(blockData, Block[].class);

            // Re-serialize validated blocks (includes source field if set)
            var blockJson = engineMapper.valueToTree(blocks);

            // Build WebSocket message
            NetworkMessage networkMessage = NetworkMessage.builder()
                    .t("b.u")
                    .d(blockJson)
                    .build();

            String json = engineMapper.writeValueAsString(networkMessage);
            TextMessage textMessage = new TextMessage(json);

            // Determine target audience
            boolean editorOnly = BlockUpdateBroadcastMessage.AUDIENCE_EDITOR.equals(broadcast.getTargetAudience());

            // Send to relevant sessions
            int sentCount = 0;
            for (PlayerSession session : sessionManager.getAllSessions().values()) {
                // Skip if not authenticated
                if (!session.isAuthenticated()) continue;

                // Skip if different world
                if (session.getWorldId() == null || !worldId.equals(session.getWorldId().getId())) continue;

                // Filter by audience: EDITOR only?
                if (editorOnly && !"EDITOR".equals(session.getActor())) {
                    log.trace("Skipping non-editor session: {} (actor={})",
                            session.getSessionId(), session.getActor());
                    continue;
                }

                // Filter by chunk if specified
                if (broadcast.getCx() != null && broadcast.getCz() != null) {
                    if (!session.isChunkRegistered(broadcast.getCx(), broadcast.getCz())) {
                        log.trace("Session {} has not registered chunk ({}, {}), skipping",
                                session.getSessionId(), broadcast.getCx(), broadcast.getCz());
                        continue;
                    }
                }

                // Send to session (including originating session)
                session.getWebSocketSession().sendMessage(textMessage);
                sentCount++;
            }

            log.info("Broadcast block update to {} sessions: world={} audience={} blocks={}",
                    sentCount, worldId, broadcast.getTargetAudience(), blocks.length);

        } catch (Exception e) {
            log.error("Failed to handle block update from Redis: {}", message, e);
        }
    }

    /**
     * Unsubscribe from world (e.g., when shutting down).
     */
    public void unsubscribeFromWorld(String worldId) {
        redisMessaging.unsubscribe(worldId, "b.u");
        subscribedWorlds.remove(worldId);
        log.info("Unsubscribed from block update events for world: {}", worldId);
    }
}
