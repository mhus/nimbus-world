package de.mhus.nimbus.world.player.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.player.ws.SessionManager;
import de.mhus.nimbus.world.shared.redis.WorldRedisMessagingService;
import de.mhus.nimbus.world.shared.session.SessionCommandService;
import de.mhus.nimbus.world.shared.session.SessionCommandTarget;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Receives session commands from Redis and dispatches them to matching player sessions.
 * Subscribes to global channel world:global:s.cmd and uses ClientService to forward
 * commands to WebSocket clients.
 *
 * Target types:
 * - ALL: all authenticated sessions
 * - TEAM: sessions whose cachedTeamId matches the target
 * - PLAYER: session whose entityId matches the target
 * - WORLD: sessions whose worldId starts with the target (prefix match for region/world/instance)
 * - HEX_GRID: sessions whose worldId matches exactly and cachedHexQ/R match hexQ/hexR from message
 */
@Service
@Slf4j
public class MessageSessionService {

    private final WorldRedisMessagingService redisMessaging;
    private final SessionManager sessionManager;
    private final ClientService clientService;
    private final ObjectMapper objectMapper;

    public MessageSessionService(
            WorldRedisMessagingService redisMessaging,
            @Lazy SessionManager sessionManager,
            ClientService clientService,
            ObjectMapper objectMapper) {
        this.redisMessaging = redisMessaging;
        this.sessionManager = sessionManager;
        this.clientService = clientService;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void subscribe() {
        redisMessaging.subscribeGlobal(SessionCommandService.REDIS_CHANNEL_SESSION_CMD, this::handleSessionCommand);
        log.info("Subscribed to global session command channel (channel: world:global:{})",
                SessionCommandService.REDIS_CHANNEL_SESSION_CMD);
    }

    private void handleSessionCommand(String topic, String message) {
        try {
            var node = objectMapper.readTree(message);

            String targetTypeStr = node.has("targetType") ? node.get("targetType").asText() : null;
            String target = node.has("target") ? node.get("target").asText() : null;
            String cmd = node.has("cmd") ? node.get("cmd").asText() : null;
            Integer hexQ = node.has("hexQ") ? node.get("hexQ").asInt() : null;
            Integer hexR = node.has("hexR") ? node.get("hexR").asInt() : null;

            if (targetTypeStr == null || cmd == null) {
                log.warn("Invalid session command message: {}", message);
                return;
            }

            SessionCommandTarget targetType;
            try {
                targetType = SessionCommandTarget.valueOf(targetTypeStr);
            } catch (IllegalArgumentException e) {
                log.warn("Unknown target type in session command: {}", targetTypeStr);
                return;
            }

            List<String> args = new ArrayList<>();
            if (node.has("args") && node.get("args").isArray()) {
                for (var arg : node.get("args")) {
                    args.add(arg.asText());
                }
            }

            log.debug("Session command received: targetType={}, target={}, cmd={}", targetType, target, cmd);

            int count = 0;
            for (PlayerSession session : sessionManager.getAllSessions().values()) {
                if (!session.isAuthenticated()) continue;

                if (matchesTarget(session, targetType, target, hexQ, hexR)) {
                    clientService.sendCommand(session, cmd, args);
                    count++;
                }
            }

            log.debug("Session command dispatched to {} sessions: cmd={}", count, cmd);

        } catch (Exception e) {
            log.error("Failed to handle session command: {}", e.getMessage(), e);
        }
    }

    private boolean matchesTarget(PlayerSession session, SessionCommandTarget targetType, String target,
                                   Integer hexQ, Integer hexR) {
        return switch (targetType) {
            case ALL -> true;
            case TEAM -> target != null && target.equals(session.getCachedTeamId());
            case PLAYER -> target != null && target.equals(session.getEntityId());
            case WORLD -> target != null && session.getWorldId() != null
                    && session.getWorldId().getFullId().startsWith(target);
            case HEX_GRID -> target != null
                    && hexQ != null && hexR != null
                    && session.getWorldId() != null
                    && session.getWorldId().getFullId().equals(target)
                    && hexQ.equals(session.getCachedHexQ())
                    && hexR.equals(session.getCachedHexR());
        };
    }
}
