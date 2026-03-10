package de.mhus.nimbus.world.player.ws.redis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.generated.network.messages.ChunkDataTransferObject;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.player.ws.SessionManager;
import de.mhus.nimbus.world.shared.redis.WorldRedisMessagingService;
import de.mhus.nimbus.world.shared.world.WChunkService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.BinaryMessage;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Redis listener for chunk update events.
 * Receives chunk updates from world-control and distributes to connected clients.
 *
 * Redis message format:
 * {
 *   "chunkKey": "0:0",
 *   "cx": 0,
 *   "cz": 0,
 *   "blockCount": 256,
 *   "epoches": [1, 2]       // optional: affected epoches (null = all)
 * }
 *
 * Client message type: "c.u" (Chunk Update)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChunkUpdateBroadcastListener {

    private final WorldRedisMessagingService redisMessaging;
    private final SessionManager sessionManager;
    private final WChunkService chunkService;
    private final ObjectMapper objectMapper;

    // Track which worlds are already subscribed
    private final java.util.Set<String> subscribedWorlds = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Subscribe to all active worlds on startup.
     * Dynamically subscribes when sessions connect to new worlds.
     */
    @PostConstruct
    public void subscribeToWorlds() {
        // Initial subscriptions will happen when sessions connect
        log.info("ChunkUpdateBroadcastListener initialized - will subscribe to worlds dynamically");
    }

    /**
     * Subscribe to chunk update events for a specific world.
     * Automatically called when handling chunk updates for a new world.
     * Thread-safe - can be called from multiple threads.
     */
    public void subscribeToWorld(String worldId) {
        // Extract base worldId without instance
        String baseWorldId = de.mhus.nimbus.shared.types.WorldId.unchecked(worldId).toBaseWorldId().getId();

        // Check if already subscribed
        if (subscribedWorlds.contains(baseWorldId)) {
            log.trace("Already subscribed to chunk updates for world: {}", baseWorldId);
            return;
        }

        // Use synchronized to prevent race condition
        synchronized (subscribedWorlds) {
            // Double-check after acquiring lock
            if (subscribedWorlds.contains(baseWorldId)) {
                return;
            }

            redisMessaging.subscribe(baseWorldId, "c.update", (topic, message) -> {
                handleChunkUpdate(baseWorldId, message);
            });

            subscribedWorlds.add(baseWorldId);
            log.info("Subscribed to chunk update events for world: {}", baseWorldId);
        }
    }

    /**
     * Handle incoming chunk update event from Redis.
     * Load updated chunk and send to all sessions registered for that chunk.
     */
    private void handleChunkUpdate(String worldId, String message) {
        try {
            JsonNode data = objectMapper.readTree(message);

            String chunkKey = data.get("chunkKey").asText();
            int cx = data.has("cx") ? data.get("cx").asInt() : parseChunkX(chunkKey);
            int cz = data.has("cz") ? data.get("cz").asInt() : parseChunkZ(chunkKey);

            // Parse affected epoches from message (null = all epoches, i.e. legacy mode)
            Set<Integer> affectedEpoches = null;
            if (data.has("epoches") && data.get("epoches").isArray()) {
                affectedEpoches = new HashSet<>();
                for (JsonNode epochNode : data.get("epoches")) {
                    affectedEpoches.add(epochNode.asInt());
                }
            }

            log.debug("Received chunk update: world={} chunk={} epoches={}",
                    worldId, chunkKey, affectedEpoches);

            // Load updated chunk from database
            WorldId wid = WorldId.of(worldId).orElse(null);
            if (wid == null) {
                log.warn("Invalid worldId: {}", worldId);
                return;
            }

            // Cache DTOs per epoch to avoid redundant DB loads
            Map<Integer, ChunkDataTransferObject> dtoByEpoch = new HashMap<>();

            // Send to all sessions registered for this chunk, loading per-epoch
            int sent = 0;
            for (PlayerSession session : sessionManager.getAllSessions().values()) {
                // Skip if not authenticated
                if (!session.isAuthenticated()) continue;

                // Skip if different world (editor instances also match their base world)
                if (session.getWorldId() == null || !session.getWorldId().matchesBaseWorld(worldId)) continue;

                // Check if session has registered the chunk
                if (!session.isChunkRegistered(cx, cz)) {
                    log.trace("Session {} has not registered chunk ({}, {}), skipping",
                            session.getSessionId(), cx, cz);
                    continue;
                }

                // Skip if session's epoch is not in the affected epoches
                int epoch = session.getEpoch();
                if (affectedEpoches != null && !affectedEpoches.contains(epoch)) {
                    log.trace("Session {} epoch {} not in affected epoches {}, skipping",
                            session.getSessionId(), epoch, affectedEpoches);
                    continue;
                }

                ChunkDataTransferObject dto = dtoByEpoch.computeIfAbsent(epoch, ep -> {
                    var chunkEntityOpt = chunkService.find(wid, chunkKey, ep);
                    if (chunkEntityOpt.isEmpty()) {
                        log.debug("WChunk not found for broadcast: chunkKey={}, epoch={}", chunkKey, ep);
                        return null;
                    }
                    return chunkService.toTransferObject(wid, chunkEntityOpt.get());
                });

                if (dto == null) continue;

                // Send as binary frame if compressed
                if (dto.getC() != null && dto.getC().length > 0) {
                    try {
                        sendCompressedChunkBinary(session, dto);
                        sent++;
                        log.trace("Sent binary chunk update to session: cx={}, cz={}, epoch={}, compressed={} bytes",
                                cx, cz, epoch, dto.getC().length);
                    } catch (Exception e) {
                        log.error("Failed to send binary chunk update to session: cx={}, cz={}",
                                cx, cz, e);
                    }
                } else {
                    log.warn("Chunk not compressed, cannot broadcast: cx={}, cz={}", cx, cz);
                }
            }

            log.info("Broadcast chunk update to {} sessions: world={} chunk={} epoches={}",
                    sent, worldId, chunkKey, affectedEpoches);

        } catch (Exception e) {
            log.error("Failed to handle chunk update from Redis: {}", message, e);
        }
    }

    /**
     * Unsubscribe from world (e.g., when shutting down).
     */
    public void unsubscribeFromWorld(String worldId) {
        redisMessaging.unsubscribe(worldId, "c.update");
        log.info("Unsubscribed from chunk update events for world: {}", worldId);
    }

    private int parseChunkX(String chunkKey) {
        return Integer.parseInt(chunkKey.split(":")[0]);
    }

    private int parseChunkZ(String chunkKey) {
        return Integer.parseInt(chunkKey.split(":")[1]);
    }

    /**
     * Send compressed chunk as binary WebSocket frame.
     * Format: [4 bytes header length][header JSON][GZIP compressed data]
     */
    private void sendCompressedChunkBinary(PlayerSession session, ChunkDataTransferObject dto) throws Exception {
        // 1. Build header with metadata (small data, stays JSON)
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("cx", dto.getCx());
        header.put("cz", dto.getCz());
        if (dto.getI() != null && !dto.getI().isEmpty()) {
            header.put("i", dto.getI());
        }

        String headerJson = objectMapper.writeValueAsString(header);
        byte[] headerBytes = headerJson.getBytes(StandardCharsets.UTF_8);

        // 2. Build binary frame: [4 bytes length][header][compressed data]
        ByteBuffer buffer = ByteBuffer.allocate(4 + headerBytes.length + dto.getC().length);
        buffer.putInt(headerBytes.length);  // Header length as int32 (big-endian)
        buffer.put(headerBytes);             // Header JSON
        buffer.put(dto.getC());              // GZIP compressed data

        // 3. Send as binary WebSocket frame
        session.sendMessage(new BinaryMessage(buffer.array()));

        log.debug("Sent binary chunk update: cx={}, cz={}, header={} bytes, compressed={} bytes, total={} bytes",
                dto.getCx(), dto.getCz(), headerBytes.length, dto.getC().length, buffer.position());
    }
}
