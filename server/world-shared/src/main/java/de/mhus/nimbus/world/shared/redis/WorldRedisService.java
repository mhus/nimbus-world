package de.mhus.nimbus.world.shared.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

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

    public void addToSet(String worldId, String key, String... members) {
        if (members == null || members.length == 0) return;
        String namespaced = ns(worldId, key);
        redis.opsForSet().add(namespaced, members);
    }

    public void removeFromSet(String worldId, String key, String... members) {
        if (members == null || members.length == 0) return;
        String namespaced = ns(worldId, key);
        redis.opsForSet().remove(namespaced, (Object[]) members);
    }

    public Set<String> getSetMembers(String worldId, String key) {
        String namespaced = ns(worldId, key);
        Set<String> members = redis.opsForSet().members(namespaced);
        return members != null ? members : Collections.emptySet();
    }

    public void setExpire(String worldId, String key, Duration ttl) {
        String namespaced = ns(worldId, key);
        redis.expire(namespaced, ttl);
    }

    private String ns(String worldId, String key) {
        // Use ':' delimiter to match test expectations and redis key convention
        return "world:" + worldId + ":" + key;
    }
}
