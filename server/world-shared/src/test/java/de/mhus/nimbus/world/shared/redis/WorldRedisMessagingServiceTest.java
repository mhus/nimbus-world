package de.mhus.nimbus.world.shared.redis;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.connection.MessageListener;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class WorldRedisMessagingServiceTest {

    @Test
    void publishAndSubscribe() {
        StringRedisTemplate template = Mockito.mock(StringRedisTemplate.class);
        RedisMessageListenerContainer container = Mockito.mock(RedisMessageListenerContainer.class);
        WorldRedisMessagingService svc = new WorldRedisMessagingService(template, container);
        AtomicReference<String> received = new AtomicReference<>();
        svc.subscribe("w1","updates", (topic, msg) -> received.set(topic + ";" + msg));
        Mockito.verify(container).addMessageListener(any(MessageListener.class), eq(ChannelTopic.of("world:w1:updates")));
        svc.publish("w1","updates","hello");
        Mockito.verify(template).convertAndSend("world:w1:updates", "hello");
        // simulate callback
        // We won't invoke the real adapter; just assert subscription metadata
        assertNull(received.get()); // handler not called because we didn't trigger container
        svc.unsubscribe("w1","updates");
        Mockito.verify(container).removeMessageListener(any(MessageListener.class), eq(ChannelTopic.of("world:w1:updates")));
    }
}
