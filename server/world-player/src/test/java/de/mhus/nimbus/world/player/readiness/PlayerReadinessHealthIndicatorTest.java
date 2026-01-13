package de.mhus.nimbus.world.player.readiness;

import org.junit.jupiter.api.Test;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.support.GenericApplicationContext;

import static org.junit.jupiter.api.Assertions.*;

class PlayerReadinessHealthIndicatorTest {

    @Test
    void transitionsAndSessionLimit() {
        var tracker = new WebSocketSessionTracker();
        var props = new PlayerReadinessProperties(2); // Limit 2
        var indicator = new PlayerReadinessHealthIndicator(tracker, props);
        var ctx = new GenericApplicationContext();

        // Vor Refresh -> DOWN
        assertEquals("DOWN", indicator.health().getStatus().getCode());

        // Kontext refreshed -> READY
        indicator.onApplicationEvent(new ContextRefreshedEvent(ctx));
        assertEquals("UP", indicator.health().getStatus().getCode());

        // Sessions innerhalb Limit
        tracker.increment(); // 1
        assertEquals("UP", indicator.health().getStatus().getCode());
        tracker.increment(); // 2
        assertEquals("UP", indicator.health().getStatus().getCode());

        // Überschreitung Limit
        tracker.increment(); // 3
        assertEquals("DOWN", indicator.health().getStatus().getCode());
        assertTrue(indicator.health().getDetails().get("readiness").toString().contains("too many"));

        // Entfernen Sessions wieder OK
        tracker.decrement(); // 2
        assertEquals("UP", indicator.health().getStatus().getCode());

        // Shutdown -> DOWN
        indicator.onApplicationEvent(new ContextClosedEvent(ctx));
        assertEquals("DOWN", indicator.health().getStatus().getCode());
    }
}

