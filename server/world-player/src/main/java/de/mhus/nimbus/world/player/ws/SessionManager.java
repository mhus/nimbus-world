package de.mhus.nimbus.world.player.ws;

import de.mhus.nimbus.generated.network.ClientType;
import de.mhus.nimbus.shared.types.PlayerData;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.shared.utils.LocationService;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.player.session.SessionAuthenticatedConsumer;
import de.mhus.nimbus.world.player.session.SessionClosedConsumer;
import de.mhus.nimbus.world.shared.session.WSessionService;
import de.mhus.nimbus.world.shared.session.WSessionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all active player WebSocket sessions.
 * Provides session lookup and cleanup.
 * Synchronizes sessions with Redis via WSessionService.
 */
@Service
@Slf4j
public class SessionManager {

    private final WSessionService wSessionService;
    private final LocationService locationService;
    private final de.mhus.nimbus.world.shared.client.WorldClientService worldClientService;

    @Autowired
    @Lazy
    private de.mhus.nimbus.world.player.ws.redis.ChunkUpdateBroadcastListener chunkUpdateListener;

    @Autowired
    @Lazy
    private de.mhus.nimbus.world.player.ws.redis.BlockUpdateBroadcastListener blockUpdateListener;

    @Autowired
    @Lazy
    private List<SessionClosedConsumer> sessionClosedConsumers;

    @Autowired
    @Lazy
    private List<SessionAuthenticatedConsumer> sessionAuthenticatedConsumers;

    public SessionManager(WSessionService wSessionService,
                         LocationService locationService,
                         de.mhus.nimbus.world.shared.client.WorldClientService worldClientService) {
        this.wSessionService = wSessionService;
        this.locationService = locationService;
        this.worldClientService = worldClientService;
    }

    private final Map<String, PlayerSession> sessionsByWebSocketId = new ConcurrentHashMap<>();
    private final Map<String, PlayerSession> sessionsBySessionId = new ConcurrentHashMap<>();

    /**
     * Register new WebSocket connection.
     */
    public PlayerSession createSession(WebSocketSession webSocketSession) {
        PlayerSession playerSession = new PlayerSession(webSocketSession);
        sessionsByWebSocketId.put(webSocketSession.getId(), playerSession);
        log.debug("Created session for WebSocket: {}", webSocketSession.getId());
        return playerSession;
    }

    /**
     * Get session by WebSocket ID.
     */
    public Optional<PlayerSession> getByWebSocketId(String webSocketId) {
        return Optional.ofNullable(sessionsByWebSocketId.get(webSocketId));
    }

    /**
     * Get session by session ID (after authentication).
     * Only locally known sessions are returned.
     */
    public Optional<PlayerSession> getBySessionId(String sessionId) {
        return Optional.ofNullable(sessionsBySessionId.get(sessionId));
    }

//    /**
//     * Update session ID after authentication.
//     * Creates or updates WSession in Redis based on authentication type.
//     */
//    public void setSessionId(PlayerSession session, String sessionId,
//                              String worldId, String regionId, String userId, String characterId) {
//        session.setSessionId(sessionId);
//        sessionsBySessionId.put(sessionId, session);
//
//        String playerUrl = locationService.getInternalServerUrl();
//
//        if (isUsernamePasswordLogin && applicationDevelopmentEnabled) {
//            // Username/password login: Create new WSession in Redis
//            WSession wSession = wSessionService.create(
//                worldId != null ? worldId : applicationDevelopmentWorldId,
//                regionId != null ? regionId : applicationDevelopmentRegionId,
//                userId,
//                characterId,
//                null // use default TTL from WorldProperties
//            );
//            session.setSessionId(wSession.getId()); // Update to WSession ID
//            sessionsBySessionId.remove(sessionId); // Remove temporary ID
//            sessionsBySessionId.put(wSession.getId(), session); // Add with WSession ID
//
//            // Update WSession to RUNNING and store player URL
//            wSessionService.updateStatus(wSession.getId(), WSessionStatus.RUNNING);
//            wSessionService.updatePlayerUrl(wSession.getId(), playerUrl);
//
//            log.info("Created WSession for username/password login: sessionId={}, worldId={}, regionId={}, userId={}, playerUrl={}",
//                wSession.getId(), worldId, regionId, userId, playerUrl);
//        } else if (isUsernamePasswordLogin) {
//            // deny login if not in development mode
//            log.error("Username/password login is only allowed in development mode.");
//            throw new RuntimeException("Username/password login not allowed");
//        } else {
//            // Token login: Lookup existing WSession in Redis
//            Optional<WSession> wSession = wSessionService.get(sessionId);
//            if (wSession.isPresent()) {
//                // Always update playerUrl (even if already RUNNING - for reconnects or pod changes)
//                wSessionService.updatePlayerUrl(sessionId, playerUrl);
//
//                if (wSession.get().getStatus() == WSessionStatus.WAITING) {
//                    // Update WSession to RUNNING
//                    wSessionService.updateStatus(sessionId, WSessionStatus.RUNNING);
//
//                    log.info("Updated WSession to RUNNING for token login: sessionId={}, worldId={}, userId={}, playerUrl={}",
//                        sessionId, wSession.get().getWorldId(), wSession.get().getUserId(), playerUrl);
//                } else {
//                    log.debug("WSession already in {} state, updated playerUrl: sessionId={}, playerUrl={}",
//                        wSession.get().getStatus(), sessionId, playerUrl);
//                }
//            } else {
//                log.warn("WSession not found for token login: sessionId={}", sessionId);
//            }
//        }
//
//        log.debug("Registered sessionId {} for WebSocket {}", sessionId, session.getWebSocketSession().getId());
//    }

    /**
     * Remove session on disconnect.
     * Updates WSession in Redis to DEPRECATED.
     * Notifies SessionClosedConsumers for cleanup.
     * Notifies world-control for instance cleanup (fire-and-forget).
     */
    public void removeSession(String webSocketId) {
        PlayerSession session = sessionsByWebSocketId.remove(webSocketId);
        if (session != null) {
            session.setStatus(PlayerSession.SessionStatus.CLOSED);
            String sessionId = session.getSessionId();
            if (sessionId != null) {
                sessionsBySessionId.remove(sessionId);

                // Update WSession to DEPRECATED in Redis
                wSessionService.updateStatus(sessionId, WSessionStatus.CLOSED);
                log.info("Updated WSession to DEPRECATED on disconnect: sessionId={}", sessionId);

                // Notify world-control for instance cleanup (fire-and-forget)
                if (session.getWorldId() != null && session.getEntityId() != null) {
                    worldClientService.notifySessionClosed(
                            session.getWorldId().getId(),
                            session.getEntityId()
                    );
                }
            }

            // Notify SessionClosedConsumers for cleanup
            notifySessionClosed(session);

            log.debug("Removed session for WebSocket: {}", webSocketId);
        }
    }

    /**
     * Notify all SessionClosedConsumers.
     */
    private void notifySessionClosed(PlayerSession session) {
        for (SessionClosedConsumer consumer : sessionClosedConsumers) {
            try {
                consumer.onSessionClosed(session);
            } catch (Exception e) {
                log.error("SessionClosedConsumer failed: {}", consumer.getClass().getSimpleName(), e);
            }
        }
    }

    /**
     * Notify all SessionAuthenticatedConsumers.
     */
    private void notifySessionAuthenticated(PlayerSession session) {
        for (SessionAuthenticatedConsumer consumer : sessionAuthenticatedConsumers) {
            try {
                consumer.onSessionAuthenticated(session);
            } catch (Exception e) {
                log.error("SessionAuthenticatedConsumer failed: {}", consumer.getClass().getSimpleName(), e);
            }
        }
    }

    /**
     * Mark session as deprecated (connection lost, but keep session data).
     * Updates WSession in Redis to DEPRECATED.
     * Notifies world-control for instance cleanup (fire-and-forget).
     */
    public void deprecateSession(String webSocketId) {
        getByWebSocketId(webSocketId).ifPresent(session -> {
            session.setStatus(PlayerSession.SessionStatus.DEPRECATED);

            // Update WSession to DEPRECATED in Redis
            String sessionId = session.getSessionId();
            if (sessionId != null) {
                wSessionService.updateStatus(sessionId, WSessionStatus.CLOSED);
                log.info("Updated WSession to DEPRECATED: sessionId={}", sessionId);

                // Notify world-control for instance cleanup (fire-and-forget)
                if (session.getWorldId() != null && session.getEntityId() != null) {
                    worldClientService.notifySessionClosed(
                            session.getWorldId().getId(),
                            session.getEntityId()
                    );
                }
            }

            log.debug("Deprecated session for WebSocket: {}", webSocketId);
        });
    }

    /**
     * Get all active sessions.
     */
    public Map<String, PlayerSession> getAllSessions() {
        return Map.copyOf(sessionsByWebSocketId);
    }

    /**
     * Count active sessions.
     */
    public int getSessionCount() {
        return sessionsByWebSocketId.size();
    }

    public void authenticateSession(PlayerSession session, String worldSessionId, WorldId worldId, PlayerData playerData, ClientType clientType, String actor) {
        var worldSessionX = wSessionService.get(worldSessionId);
        if (worldSessionX.isEmpty()) {
            log.warn("WSession not found for authentication: sessionId={}", worldSessionId);
            session.setStatus(PlayerSession.SessionStatus.DEPRECATED);
            return;
        }
        var worldSession = worldSessionX.get();
        if (worldSession.getStatus() != WSessionStatus.WAITING) {
            log.warn("WSession not in WAITING state for authentication: sessionId={} status={}", worldSessionId, worldSession.getStatus());
            session.setStatus(PlayerSession.SessionStatus.DEPRECATED);
            return;
        }
        if (!worldSession.getWorldId().equals(worldId.toString())) {
            log.warn("WSession worldId mismatch for authentication: sessionId={} expected={} actual={}",
                    worldSessionId, worldSession.getWorldId(), worldId);
            session.setStatus(PlayerSession.SessionStatus.DEPRECATED);
            return;
        }
        session.setPlayer(playerData);
        session.setActor(actor);
        session.setTitle(playerData.character().getPublicData().getTitle());
        session.setWorldId(worldId);
        session.setClientType(clientType);
        session.setStatus(PlayerSession.SessionStatus.AUTHENTICATED);
        session.setSessionId(worldSession.getId());

        // register session
        wSessionService.updateStatus(worldSessionId, WSessionStatus.RUNNING);
        wSessionService.updatePlayerUrl(worldSessionId, locationService.getInternalServerUrl());

        sessionsBySessionId.put(worldSessionId, session);

        // Subscribe to Redis broadcasts for this world (once per world, thread-safe)
        // Only after authentication, when session is RUNNING
        chunkUpdateListener.subscribeToWorld(worldId.getId());
        blockUpdateListener.subscribeToWorld(worldId.getId());

        log.debug("Session authenticated and subscribed to broadcasts: sessionId={}, worldId={}, actor={}",
                worldSessionId, worldId.getId(), actor);

        // Notify SessionAuthenticatedConsumers (e.g., start persistence tick thread)
        notifySessionAuthenticated(session);
    }
}
