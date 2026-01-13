package de.mhus.nimbus.shared.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

/**
 * Manages application readiness state using Spring Boot's Availability API.
 *
 * The readiness state indicates whether the application is ready to accept traffic.
 * It integrates with Spring Boot Actuator's /actuator/health/readiness endpoint.
 *
 * Behavior:
 *  - Sets ACCEPTING_TRAFFIC after application context is fully initialized (ContextRefreshedEvent)
 *  - Sets REFUSING_TRAFFIC when shutdown begins (ContextClosedEvent)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReadinessHealthIndicator implements ApplicationListener<ApplicationEvent> {

    private final ApplicationContext applicationContext;

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof ContextRefreshedEvent) {
            AvailabilityChangeEvent.publish(applicationContext, ReadinessState.ACCEPTING_TRAFFIC);
            log.info("Application readiness state: ACCEPTING_TRAFFIC (context refreshed)");
        } else if (event instanceof ContextClosedEvent) {
            AvailabilityChangeEvent.publish(applicationContext, ReadinessState.REFUSING_TRAFFIC);
            log.info("Application readiness state: REFUSING_TRAFFIC (context closing)");
        }
    }
}
