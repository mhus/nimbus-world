package de.mhus.nimbus.world.player.ws.redis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.player.ws.ChunkSenderService;
import de.mhus.nimbus.world.player.ws.ChunkSenderService.ChunkCoord;
import de.mhus.nimbus.world.player.ws.SessionManager;
import de.mhus.nimbus.world.shared.redis.WorldRedisMessagingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Redis listener for epoch switch events.
 * When an epoch switch occurs, all sessions for the affected world update their epoch
 * and resend all registered chunks to the client with the new epoch data.
 *
 * Redis channel: world:{baseWorldId}:epoch.switch
 * Message format: {"epoch": 1}
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EpochSwitchBroadcastListener {

    public static final String CHANNEL = "epoch.switch";

    private final WorldRedisMessagingService redisMessaging;
    private final SessionManager sessionManager;
    private final ChunkSenderService chunkSenderService;
    private final ObjectMapper objectMapper;

    private final Set<String> subscribedWorlds = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * Subscribe to epoch switch events for a specific world.
     * Thread-safe - can be called from multiple threads.
     */
    public void subscribeToWorld(String worldId) {
        String baseWorldId = de.mhus.nimbus.shared.types.WorldId.unchecked(worldId).toBaseWorldId().getId();

        if (subscribedWorlds.contains(baseWorldId)) {
            return;
        }

        synchronized (subscribedWorlds) {
            if (subscribedWorlds.contains(baseWorldId)) {
                return;
            }

            redisMessaging.subscribe(baseWorldId, CHANNEL, (topic, message) -> {
                handleEpochSwitch(baseWorldId, message);
            });

            subscribedWorlds.add(baseWorldId);
            log.info("Subscribed to epoch switch events for world: {}", baseWorldId);
        }
    }

    /**
     * Handle incoming epoch switch event from Redis.
     * Updates epoch on all sessions for this world and resends their registered chunks.
     */
    private void handleEpochSwitch(String baseWorldId, String message) {
        try {
            JsonNode data = objectMapper.readTree(message);
            int newEpoch = data.get("epoch").asInt();

            log.info("Epoch switch received: world={}, newEpoch={}", baseWorldId, newEpoch);

            int updatedSessions = 0;
            for (PlayerSession session : sessionManager.getAllSessions().values()) {
                if (!session.isAuthenticated()) continue;
                if (session.getWorldId() == null || !session.getWorldId().matchesBaseWorld(baseWorldId)) continue;

                int oldEpoch = session.getEpoch();
                session.setEpoch(newEpoch);
                updatedSessions++;

                log.debug("Updated epoch for session {}: {} -> {}", session.getSessionId(), oldEpoch, newEpoch);

                // Resend all registered chunks with new epoch data
                resendRegisteredChunks(session);
            }

            log.info("Epoch switch completed: world={}, epoch={}, sessions updated={}",
                    baseWorldId, newEpoch, updatedSessions);

        } catch (Exception e) {
            log.error("Failed to handle epoch switch from Redis: {}", message, e);
        }
    }

    /**
     * Resend all registered chunks for a session.
     * Called after epoch switch to ensure client receives chunks with new epoch data.
     */
    private void resendRegisteredChunks(PlayerSession session) {
        Set<String> registeredChunks = session.getRegisteredChunks();
        if (registeredChunks.isEmpty()) {
            return;
        }

        List<ChunkCoord> chunks = new ArrayList<>();
        for (String chunkKey : registeredChunks) {
            String[] parts = chunkKey.split(":");
            if (parts.length == 2) {
                chunks.add(new ChunkCoord(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
            }
        }

        if (!chunks.isEmpty()) {
            chunkSenderService.sendChunksAsync(session, chunks);
            log.debug("Scheduled resend of {} chunks for session {} after epoch switch",
                    chunks.size(), session.getSessionId());
        }
    }
}
