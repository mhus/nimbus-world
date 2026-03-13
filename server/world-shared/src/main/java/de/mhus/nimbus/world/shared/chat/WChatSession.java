package de.mhus.nimbus.world.shared.chat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Per-chat session with a background thread loop and message queue.
 * Processes incoming chat/command messages asynchronously.
 * Registers itself in Redis so other pods can route messages to the correct pod.
 */
@Slf4j
public class WChatSession implements Runnable {

    private static final Duration REDIS_TTL = Duration.ofMinutes(2);
    private static final long REDIS_REFRESH_INTERVAL_MS = 30_000; // 30 seconds
    private static final long IDLE_TIMEOUT_MS = 60_000; // 1 minute
    private static final long POLL_TIMEOUT_SECONDS = 10;

    private final String chatKey;
    private final String worldId;
    private final String chatId;
    private final WChatService chatService;
    private final WChatExecutorService executorService;
    private final StringRedisTemplate redis;
    private final String localUrl;

    private final LinkedBlockingQueue<WChatSessionMessage> queue = new LinkedBlockingQueue<>();
    private final WChatSessionQueue sessionQueue = new SessionQueueAdapter();
    private volatile boolean running = true;
    private long lastRedisRefresh;
    private long lastMessageTime;

    public WChatSession(String chatKey, String worldId, String chatId,
                        WChatService chatService, WChatExecutorService executorService,
                        StringRedisTemplate redis, String localUrl) {
        this.chatKey = chatKey;
        this.worldId = worldId;
        this.chatId = chatId;
        this.chatService = chatService;
        this.executorService = executorService;
        this.redis = redis;
        this.localUrl = localUrl;
    }

    public void enqueue(WChatSessionMessage msg) {
        queue.offer(msg);
    }

    public void requestStop() {
        running = false;
    }

    @Override
    public void run() {
        log.info("WChatSession started: chatKey={}", chatKey);
        WChatAgent agent = null;
        try {
            registerInRedis();
            lastMessageTime = System.currentTimeMillis();
            lastRedisRefresh = System.currentTimeMillis();

            // Notify agent of session start (agent can restore state)
            agent = notifyAgentSessionStarted();

            while (running) {
                WChatSessionMessage msg = queue.poll(POLL_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                // Check if chat was deactivated/archived
                if (!running) break;
                if (isChatDeactivated()) {
                    log.info("Chat deactivated/archived, ending session: chatKey={}", chatKey);
                    break;
                }

                // Refresh Redis TTL periodically
                long now = System.currentTimeMillis();
                if (now - lastRedisRefresh > REDIS_REFRESH_INTERVAL_MS) {
                    refreshRedis();
                    lastRedisRefresh = now;
                }

                if (msg != null) {
                    processMessage(msg);
                    lastMessageTime = System.currentTimeMillis();
                } else {
                    // No message received — check idle timeout
                    if (System.currentTimeMillis() - lastMessageTime > IDLE_TIMEOUT_MS) {
                        log.info("Idle timeout reached, ending session: chatKey={}", chatKey);
                        break;
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.info("WChatSession interrupted: chatKey={}", chatKey);
        } catch (Exception e) {
            log.error("WChatSession error: chatKey={}", chatKey, e);
        } finally {
            // Notify agent of session end (agent can persist state)
            notifyAgentSessionEnded(agent);
            cleanupRedis();
            executorService.onSessionEnded(chatKey);
            log.info("WChatSession ended: chatKey={}", chatKey);
        }
    }

    private void processMessage(WChatSessionMessage msg) {
        try {
            switch (msg.getType()) {
                case CHAT -> chatService.processAgentChat(msg, sessionQueue);
                case COMMAND -> chatService.processAgentCommand(msg);
            }
        } catch (Exception e) {
            log.error("Error processing message: chatKey={}, type={}", chatKey, msg.getType(), e);
        }
    }

    /**
     * Load the chat entity and notify the agent of session start.
     * The agent can restore internal state from chat.getAgentState().
     */
    private WChatAgent notifyAgentSessionStarted() {
        try {
            var wId = de.mhus.nimbus.shared.types.WorldId.unchecked(worldId);
            var chatOpt = chatService.findByWorldIdAndChatId(wId, chatId);
            if (chatOpt.isEmpty()) {
                log.warn("Chat not found for session start: chatKey={}", chatKey);
                return null;
            }
            WChat chat = chatOpt.get();
            String agentName = chat.getType();
            var agentOpt = chatService.getAgent(agentName);
            if (agentOpt.isEmpty()) {
                log.debug("Agent not found for session start event: agentName={}", agentName);
                return null;
            }
            WChatAgent agent = agentOpt.get();
            agent.onSessionStarted(chat);
            log.debug("Agent {} notified of session start: chatKey={}", agentName, chatKey);
            return agent;
        } catch (Exception e) {
            log.warn("Error notifying agent of session start: chatKey={}", chatKey, e);
            return null;
        }
    }

    /**
     * Notify the agent of session end and persist its state to MongoDB.
     * The agent can store internal state via chat.setAgentState(...).
     */
    private void notifyAgentSessionEnded(WChatAgent agent) {
        if (agent == null) return;
        try {
            var wId = de.mhus.nimbus.shared.types.WorldId.unchecked(worldId);
            var chatOpt = chatService.findByWorldIdAndChatId(wId, chatId);
            if (chatOpt.isEmpty()) {
                log.warn("Chat not found for session end: chatKey={}", chatKey);
                return;
            }
            WChat chat = chatOpt.get();
            agent.onSessionEnded(chat);
            chatService.save(chat);
            log.debug("Agent {} notified of session end, state saved: chatKey={}", agent.getName(), chatKey);
        } catch (Exception e) {
            log.warn("Error notifying agent of session end: chatKey={}", chatKey, e);
        }
    }

    private boolean isChatDeactivated() {
        try {
            var chatOpt = chatService.findByWorldIdAndChatId(
                    de.mhus.nimbus.shared.types.WorldId.unchecked(worldId), chatId);
            return chatOpt.map(WChat::isArchived).orElse(true);
        } catch (Exception e) {
            log.warn("Error checking chat status: chatKey={}", chatKey, e);
            return false;
        }
    }

    private String redisKey() {
        return "wchat:session:" + worldId + ":" + chatId;
    }

    private void registerInRedis() {
        try {
            String key = redisKey();
            redis.opsForHash().put(key, "localUrl", localUrl);
            redis.opsForHash().put(key, "updatedAt", Instant.now().toString());
            redis.expire(key, REDIS_TTL);
            log.debug("Registered in Redis: key={}, localUrl={}", key, localUrl);
        } catch (Exception e) {
            log.error("Failed to register in Redis: chatKey={}", chatKey, e);
        }
    }

    private void refreshRedis() {
        try {
            String key = redisKey();
            redis.opsForHash().put(key, "updatedAt", Instant.now().toString());
            redis.expire(key, REDIS_TTL);
            log.trace("Refreshed Redis TTL: key={}", key);
        } catch (Exception e) {
            log.warn("Failed to refresh Redis TTL: chatKey={}", chatKey, e);
        }
    }

    private void cleanupRedis() {
        try {
            String key = redisKey();
            // Only delete if we are still the owner
            Object storedUrl = redis.opsForHash().get(key, "localUrl");
            if (localUrl.equals(storedUrl)) {
                redis.delete(key);
                log.debug("Cleaned up Redis: key={}", key);
            }
        } catch (Exception e) {
            log.warn("Failed to cleanup Redis: chatKey={}", chatKey, e);
        }
    }

    /**
     * Adapter that exposes the internal queue as WChatSessionQueue.
     * Agents can use this to consume additional messages during processing.
     */
    private class SessionQueueAdapter implements WChatSessionQueue {

        @Override
        public WChatSessionMessage poll(long timeout, TimeUnit unit) throws InterruptedException {
            return queue.poll(timeout, unit);
        }

        @Override
        public WChatSessionMessage poll() {
            return queue.poll();
        }

        @Override
        public WChatSessionMessage peek() {
            return queue.peek();
        }

        @Override
        public boolean hasNext() {
            return !queue.isEmpty();
        }
    }
}
