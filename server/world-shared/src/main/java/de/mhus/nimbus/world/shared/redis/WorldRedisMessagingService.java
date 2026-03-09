package de.mhus.nimbus.world.shared.redis;

import de.mhus.nimbus.shared.types.WorldId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Service;

import org.springframework.data.redis.connection.MessageListener;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorldRedisMessagingService {

    private final StringRedisTemplate redisTemplate;
    private final RedisMessageListenerContainer container;
    private final Map<String, MessageListener> listeners = new ConcurrentHashMap<>();

    public void publish(String worldId, String channel, String message) {
        String t = topic(worldId, channel);
        redisTemplate.convertAndSend(t, message);
    }

    public void subscribe(String worldId, String channel, BiConsumer<String,String> handler) {
        String t = topic(worldId, channel);
        if (listeners.containsKey(t)) return; // already subscribed
        MessageListener listener = (msg, pattern) -> {
            try {
                String body = new String(msg.getBody());
                handler.accept(t, body);
            } catch (Exception e) {
                log.warn("Failed to process redis message on {}: {}", t, e.getMessage(), e);
            }
        };
        container.addMessageListener(listener, ChannelTopic.of(t));
        listeners.put(t, listener);
    }

    public void unsubscribe(String worldId, String channel) {
        String t = topic(worldId, channel);
        MessageListener listener = listeners.remove(t);
        if (listener != null) {
            container.removeMessageListener(listener, ChannelTopic.of(t));
        }
    }

    /**
     * Subscribe to all worlds for a specific channel using pattern matching.
     * Pattern: "world:*:channel"
     *
     * @param channel The channel name (e.g., "e.p", "u.m")
     * @param handler Handler that receives (topic, message)
     */
    public void subscribeToAllWorlds(String channel, BiConsumer<String,String> handler) {
        String pattern = "world:*:" + channel;
        if (listeners.containsKey(pattern)) return; // already subscribed

        MessageListener listener = (msg, patternBytes) -> {
            try {
                String topic = new String(msg.getChannel());
                String body = new String(msg.getBody());
                handler.accept(topic, body);
            } catch (Exception e) {
                log.warn("Failed to process redis message on pattern {}: {}", pattern, e.getMessage(), e);
            }
        };

        container.addMessageListener(listener, new org.springframework.data.redis.listener.PatternTopic(pattern));
        listeners.put(pattern, listener);
        log.info("Subscribed to Redis pattern: {}", pattern);
    }

    /**
     * Unsubscribe from pattern subscription for all worlds.
     */
    public void unsubscribeFromAllWorlds(String channel) {
        String pattern = "world:*:" + channel;
        MessageListener listener = listeners.remove(pattern);
        if (listener != null) {
            container.removeMessageListener(listener, new org.springframework.data.redis.listener.PatternTopic(pattern));
        }
    }

    // ===== Global (non-world-specific) channels =====

    /**
     * Publish to a global channel (not world-specific).
     * Topic format: "world:global:{channel}"
     */
    public void publishGlobal(String channel, String message) {
        String t = "world:global:" + channel;
        redisTemplate.convertAndSend(t, message);
    }

    /**
     * Subscribe to a global channel (not world-specific).
     * Topic format: "world:global:{channel}"
     */
    public void subscribeGlobal(String channel, BiConsumer<String, String> handler) {
        String t = "world:global:" + channel;
        if (listeners.containsKey(t)) return;
        MessageListener listener = (msg, pattern) -> {
            try {
                String body = new String(msg.getBody());
                handler.accept(t, body);
            } catch (Exception e) {
                log.warn("Failed to process redis message on {}: {}", t, e.getMessage(), e);
            }
        };
        container.addMessageListener(listener, ChannelTopic.of(t));
        listeners.put(t, listener);
        log.info("Subscribed to global Redis channel: {}", t);
    }

    private String topic(String worldId, String channel) {
        return "world:" + WorldId.unchecked(worldId).getFullBaseId() + ":" + channel;
    }
}
