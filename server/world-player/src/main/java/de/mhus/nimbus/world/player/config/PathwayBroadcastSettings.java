package de.mhus.nimbus.world.player.config;

import de.mhus.nimbus.shared.service.SSettingsService;
import de.mhus.nimbus.shared.settings.SettingInteger;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PathwayBroadcastSettings {

    private final SSettingsService settingsService;

    private SettingInteger pathwayBroadcastIntervalMs;
    private SettingInteger entityUpdateTimeoutMs;
    private SettingInteger pathwayPredictionTimeMs;

    @PostConstruct
    private void init() {
        pathwayBroadcastIntervalMs = settingsService.getInteger(
                "player.pathwayBroadcastIntervalMs",
                100
        );
        entityUpdateTimeoutMs = settingsService.getInteger(
                "player.entityUpdateTimeoutMs",
                200
        );
        pathwayPredictionTimeMs = settingsService.getInteger(
                "player.pathwayPredictionTimeMs",
                100
        );
    }

    /**
     * Interval in milliseconds for broadcasting entity pathways to Redis.
     * Default: 100ms (10 times per second)
     */
    public int getPathwayBroadcastIntervalMs() {
        return pathwayBroadcastIntervalMs.get();
    }

    /**
     * Timeout in milliseconds after which an entity is considered inactive.
     * Inactive entities will not generate pathways.
     * Default: 200ms
     */
    public int getEntityUpdateTimeoutMs() {
        return entityUpdateTimeoutMs.get();
    }

    /**
     * Time in milliseconds to predict future position based on velocity.
     * Default: 100ms
     */
    public int getPathwayPredictionTimeMs() {
        return pathwayPredictionTimeMs.get();
    }
}
