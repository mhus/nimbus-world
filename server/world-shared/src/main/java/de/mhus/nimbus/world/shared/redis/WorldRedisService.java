package de.mhus.nimbus.world.shared.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WorldRedisService {

    private final StringRedisTemplate redis;

    public void putValue(String worldId, String key, String value, Duration ttl) {
        String namespaced = ns(worldId, key);
        redis.opsForValue().set(namespaced, value, ttl);
    }

    public Optional<String> getValue(String worldId, String key) {
        String namespaced = ns(worldId, key);
        return Optional.ofNullable(redis.opsForValue().get(namespaced));
    }

    public boolean deleteValue(String worldId, String key) {
        String namespaced = ns(worldId, key);
        Boolean res = redis.delete(namespaced);
        return Boolean.TRUE.equals(res);
    }

    private String ns(String worldId, String key) {
        // Use ':' delimiter to match test expectations and redis key convention
        return "world:" + worldId + ":" + key;
    }
}
