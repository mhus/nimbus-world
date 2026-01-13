package de.mhus.nimbus.world.player.readiness;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "world.player.readiness")
public record PlayerReadinessProperties(int maxActiveSessions) {
    public PlayerReadinessProperties {
        if (maxActiveSessions <= 0) {
            maxActiveSessions = 100; // Fallback
        }
    }
}

