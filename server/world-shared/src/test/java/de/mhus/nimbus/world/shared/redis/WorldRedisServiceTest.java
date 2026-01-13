package de.mhus.nimbus.world.shared.redis;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;

class WorldRedisServiceTest {

    @Test
    void putAndGetAndDelete() {
        StringRedisTemplate template = Mockito.mock(StringRedisTemplate.class, Mockito.RETURNS_DEEP_STUBS);
        @SuppressWarnings("unchecked") ValueOperations<String,String> ops = Mockito.mock(ValueOperations.class);
        Mockito.when(template.opsForValue()).thenReturn(ops);
        Mockito.when(ops.get("world:w1:test")).thenReturn("v1");
        Mockito.when(template.delete("world:w1:test")).thenReturn(true);
        WorldRedisService service = new WorldRedisService(template);
        service.putValue("w1","test","v1", Duration.ofSeconds(30));
        Optional<String> value = service.getValue("w1","test");
        assertTrue(value.isPresent());
        assertEquals("v1", value.get());
        assertTrue(service.deleteValue("w1","test"));
    }
}
