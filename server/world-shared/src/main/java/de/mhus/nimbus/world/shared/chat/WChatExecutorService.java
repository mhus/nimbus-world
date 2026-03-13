package de.mhus.nimbus.world.shared.chat;

import de.mhus.nimbus.shared.utils.LocationService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Singleton service managing all active WChatSessions.
 * Routes incoming messages to the correct session (local or remote).
 * Uses virtual threads for session execution.
 */
@Service
@Slf4j
public class WChatExecutorService {

    /**
     * Result of an enqueue operation.
     */
    public sealed interface EnqueueResult {
        record Local() implements EnqueueResult {}
        record Remote(String url) implements EnqueueResult {}
    }

    private final ConcurrentHashMap<String, WChatSession> activeSessions = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private final StringRedisTemplate redis;
    private final WChatService chatService;
    private final LocationService locationService;

    public WChatExecutorService(StringRedisTemplate redis,
                                @Lazy WChatService chatService,
                                LocationService locationService) {
        this.redis = redis;
        this.chatService = chatService;
        this.locationService = locationService;
        log.info("WChatExecutorService initialized");
    }

    /**
     * Enqueue a message for processing.
     * Routes to local session, returns REMOTE if active on another pod, or creates a new session.
     */
    public EnqueueResult enqueue(WChatSessionMessage msg) {
        String chatKey = chatKey(msg.getWorldId(), msg.getChatId());

        // 1. Check if session is locally active
        WChatSession localSession = activeSessions.get(chatKey);
        if (localSession != null) {
            localSession.enqueue(msg);
            log.debug("Enqueued to local session: chatKey={}", chatKey);
            return new EnqueueResult.Local();
        }

        // 2. Check Redis for session on another pod
        String redisKey = "wchat:session:" + msg.getWorldId() + ":" + msg.getChatId();
        try {
            Object remoteUrl = redis.opsForHash().get(redisKey, "localUrl");
            if (remoteUrl != null) {
                String url = remoteUrl.toString();
                String myUrl = locationService.getInternalServerUrl();
                if (!url.equals(myUrl)) {
                    log.debug("Session active on remote pod: chatKey={}, remoteUrl={}", chatKey, url);
                    return new EnqueueResult.Remote(url);
                }
                // Stale entry for our own URL — session ended but Redis not cleaned up yet
                log.debug("Stale Redis entry for own URL, creating new session: chatKey={}", chatKey);
            }
        } catch (Exception e) {
            log.warn("Failed to check Redis for session: chatKey={}", chatKey, e);
        }

        // 3. Create new session
        return activateNewSession(chatKey, msg);
    }

    private synchronized EnqueueResult activateNewSession(String chatKey, WChatSessionMessage msg) {
        // Double-check — another thread may have created the session in the meantime
        WChatSession existingSession = activeSessions.get(chatKey);
        if (existingSession != null) {
            existingSession.enqueue(msg);
            return new EnqueueResult.Local();
        }

        String localUrl = locationService.getInternalServerUrl();
        WChatSession session = new WChatSession(
                chatKey, msg.getWorldId(), msg.getChatId(),
                chatService, this, redis, localUrl);

        activeSessions.put(chatKey, session);
        session.enqueue(msg);
        executor.submit(session);

        log.info("New WChatSession activated: chatKey={}", chatKey);
        return new EnqueueResult.Local();
    }

    /**
     * Callback from WChatSession when it ends.
     */
    void onSessionEnded(String chatKey) {
        activeSessions.remove(chatKey);
        log.debug("Session removed from active sessions: chatKey={}", chatKey);
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down WChatExecutorService, stopping {} active sessions", activeSessions.size());
        activeSessions.values().forEach(WChatSession::requestStop);
        executor.shutdown();
    }

    private String chatKey(String worldId, String chatId) {
        return worldId + ":" + chatId;
    }
}
