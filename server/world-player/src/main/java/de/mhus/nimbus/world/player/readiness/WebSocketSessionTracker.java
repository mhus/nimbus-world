package de.mhus.nimbus.world.player.readiness;

import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class WebSocketSessionTracker {
    private final AtomicInteger activeSessions = new AtomicInteger();

    public void increment() { activeSessions.incrementAndGet(); }
    public void decrement() { activeSessions.updateAndGet(v -> v > 0 ? v - 1 : 0); }
    public int getActiveSessions() { return activeSessions.get(); }
}
