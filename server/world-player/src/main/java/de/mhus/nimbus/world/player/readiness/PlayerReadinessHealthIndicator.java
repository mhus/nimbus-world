package de.mhus.nimbus.world.player.readiness;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component("sessionLimitReadiness")
public class PlayerReadinessHealthIndicator implements HealthIndicator, ApplicationListener<ApplicationEvent> {

    private final AtomicBoolean baseReady = new AtomicBoolean(false);
    private final WebSocketSessionTracker tracker;
    private final PlayerReadinessProperties props;

    public PlayerReadinessHealthIndicator(WebSocketSessionTracker tracker, PlayerReadinessProperties props) {
        this.tracker = tracker;
        this.props = props;
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof ContextRefreshedEvent) {
            baseReady.set(true);
            log.info("Player readiness base READY (context refreshed)");
        } else if (event instanceof ContextClosedEvent) {
            baseReady.set(false);
            log.info("Player readiness base NOT_READY (context closing)");
        }
    }

    @Override
    public Health health() {
        if (!baseReady.get()) {
            return Health.down().withDetail("readiness", "NOT_READY (booting/shutdown)").build();
        }
        int active = tracker.getActiveSessions();
        int max = props.maxActiveSessions();
        if (active > max) {
            return Health.down().withDetail("readiness", "NOT_READY (too many sessions)" )
                    .withDetail("activeSessions", active)
                    .withDetail("maxActiveSessions", max)
                    .build();
        }
        return Health.up().withDetail("readiness", "READY")
                .withDetail("activeSessions", active)
                .withDetail("maxActiveSessions", max)
                .build();
    }
}
