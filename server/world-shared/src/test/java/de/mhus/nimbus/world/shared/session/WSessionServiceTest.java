package de.mhus.nimbus.world.shared.session;

import de.mhus.nimbus.shared.engine.EngineMapper;
import de.mhus.nimbus.shared.types.PlayerId;
import de.mhus.nimbus.shared.types.WorldId;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;

class WSessionServiceTest {

    @Test
    void createAndGetAndUpdateAndDelete() {
        StringRedisTemplate template = Mockito.mock(StringRedisTemplate.class);
        WorldSettings props = Mockito.mock(WorldSettings.class);
        Mockito.when(props.getWaitingMinutes()).thenReturn(5L);
        Mockito.when(props.getRunningHours()).thenReturn(12L);
        Mockito.when(props.getDeprecatedMinutes()).thenReturn(30L);
        @SuppressWarnings("unchecked") HashOperations hashOps = Mockito.mock(HashOperations.class);
        Mockito.doReturn(hashOps).when(template).opsForHash();
        Mockito.when(hashOps.entries(Mockito.matches("wsession.*"))).thenReturn(Map.of(
                "status", "WAITING",
                "world", "r1:w1",
                "region", "r1",
                "user", "u1",
                "character", "c1",
                "created", Instant.now().toString(),
                "updated", Instant.now().toString(),
                "expire", Instant.now().plusSeconds(300).toString()
        ));
        Mockito.when(template.delete(anyString())).thenReturn(true);
        WSessionService svc = new WSessionService(template, props, new EngineMapper());
        WSession session = svc.create(WorldId.of("r1:w1").get(), PlayerId.of("u1:c1").get(), "PLAYER");
        assertNotNull(session.getId());
        assertEquals(60, session.getId().length());
        assertTrue(Duration.between(Instant.now(), session.getExpireAt()).toMinutes() <= 5);
        Optional<WSession> loaded = svc.get(session.getId());
        assertTrue(loaded.isPresent());
        assertEquals("r1:w1", loaded.get().getWorldId());
        Optional<WSession> updated = svc.updateStatus(session.getId(), WSessionStatus.RUNNING);
        assertTrue(updated.isPresent());
        assertEquals(WSessionStatus.RUNNING, updated.get().getStatus());
        Optional<WSession> deprecated = svc.updateStatus(session.getId(), WSessionStatus.CLOSED);
        assertTrue(deprecated.isPresent());
        assertTrue(svc.delete(session.getId()));
    }

    @Test
    void cleanupExpiredRemovesOnlyExpired() {
        StringRedisTemplate template = Mockito.mock(StringRedisTemplate.class);
        WorldSettings props = Mockito.mock(WorldSettings.class);
        Mockito.when(props.isCleanupEnabled()).thenReturn(true);
        Mockito.when(props.getCleanupScanCount()).thenReturn(100);
        Mockito.when(props.getCleanupMaxDeletes()).thenReturn(1000);
        Mockito.when(props.getWaitingMinutes()).thenReturn(5L);
        Mockito.when(props.getRunningHours()).thenReturn(12L);
        Mockito.when(props.getDeprecatedMinutes()).thenReturn(30L);
        @SuppressWarnings("unchecked") HashOperations hashOps = Mockito.mock(HashOperations.class);
        Mockito.doReturn(hashOps).when(template).opsForHash();
        Mockito.when(hashOps.get("wsession:session:expiredKey", "expire")).thenReturn(Instant.now().minusSeconds(10).toString());
        Mockito.when(hashOps.get("wsession:session:validKey", "expire")).thenReturn(Instant.now().plusSeconds(600).toString());
        Mockito.when(template.delete("wsession:session:expiredKey")).thenReturn(true);
        Mockito.when(template.delete("wsession:session:validKey")).thenReturn(true);
        // Mock keys with correct pattern that matches cleanup logic
        Mockito.when(template.keys("wsession:session:*")).thenReturn(java.util.Set.of("wsession:session:expiredKey","wsession:session:validKey"));
        // Mock getConnectionFactory to return null, so fallback logic is used
        Mockito.when(template.getConnectionFactory()).thenReturn(null);
        WSessionService svc = new WSessionService(template, props, new EngineMapper());
        var result = svc.cleanupExpired("0");
        assertEquals(1, result.deleted());
        Mockito.verify(template).delete("wsession:session:expiredKey");
        Mockito.verify(template, Mockito.never()).delete("wsession:session:validKey");
    }
}
