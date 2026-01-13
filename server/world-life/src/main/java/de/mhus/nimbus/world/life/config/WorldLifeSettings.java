package de.mhus.nimbus.world.life.config;

import de.mhus.nimbus.shared.service.SSettingsService;
import de.mhus.nimbus.shared.settings.SettingInteger;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for World Life service.
 * Loaded from SSettingsService at startup.
 */
@Component
@RequiredArgsConstructor
public class WorldLifeSettings {

    private final SSettingsService settingsService;

    private SettingInteger simulationIntervalMs;
    private SettingInteger chunkRefreshIntervalMs;
    private SettingInteger chunkTtlMs;
    private SettingInteger chunkTtlCleanupIntervalMs;
    private SettingInteger ownershipHeartbeatIntervalMs;
    private SettingInteger ownershipStaleThresholdMs;
    private SettingInteger orphanDetectionIntervalMs;
    private SettingInteger pathwayIntervalMs;

    @PostConstruct
    private void init() {
        simulationIntervalMs = settingsService.getInteger(
                "life.simulationIntervalMs",
                1000
        );
        chunkRefreshIntervalMs = settingsService.getInteger(
                "life.chunkRefreshIntervalMs",
                300000
        );
        chunkTtlMs = settingsService.getInteger(
                "life.chunkTtlMs",
                300000
        );
        chunkTtlCleanupIntervalMs = settingsService.getInteger(
                "life.chunkTtlCleanupIntervalMs",
                60000
        );
        ownershipHeartbeatIntervalMs = settingsService.getInteger(
                "life.ownershipHeartbeatIntervalMs",
                5000
        );
        ownershipStaleThresholdMs = settingsService.getInteger(
                "life.ownershipStaleThresholdMs",
                10000
        );
        orphanDetectionIntervalMs = settingsService.getInteger(
                "life.orphanDetectionIntervalMs",
                30000
        );
        pathwayIntervalMs = settingsService.getInteger(
                "life.pathwayIntervalMs",
                5000
        );
    }

    /**
     * Simulation loop interval in milliseconds.
     * Default: 1000ms (1 second)
     */
    public long getSimulationIntervalMs() {
        return simulationIntervalMs.get();
    }

    /**
     * Chunk refresh interval in milliseconds.
     * Requests full chunk list from world-player pods.
     * Default: 300000ms (5 minutes)
     * @deprecated Replaced by TTL-based mechanism. world-player now pushes chunks automatically.
     */
    @Deprecated
    public long getChunkRefreshIntervalMs() {
        return chunkRefreshIntervalMs.get();
    }

    /**
     * Chunk TTL (Time-To-Live) in milliseconds.
     * Chunks without update for this duration are removed.
     * Default: 300000ms (5 minutes)
     */
    public long getChunkTtlMs() {
        return chunkTtlMs.get();
    }

    /**
     * Chunk TTL cleanup task interval in milliseconds.
     * How often to check for and remove stale chunks.
     * Default: 60000ms (1 minute)
     */
    public long getChunkTtlCleanupIntervalMs() {
        return chunkTtlCleanupIntervalMs.get();
    }

    /**
     * Entity ownership heartbeat interval in milliseconds.
     * Default: 5000ms (5 seconds)
     */
    public long getOwnershipHeartbeatIntervalMs() {
        return ownershipHeartbeatIntervalMs.get();
    }

    /**
     * Entity ownership stale threshold in milliseconds.
     * Entities with no heartbeat for this duration are considered orphaned.
     * Default: 10000ms (10 seconds)
     */
    public long getOwnershipStaleThresholdMs() {
        return ownershipStaleThresholdMs.get();
    }

    /**
     * Orphan detection interval in milliseconds.
     * Default: 30000ms (30 seconds)
     */
    public long getOrphanDetectionIntervalMs() {
        return orphanDetectionIntervalMs.get();
    }

    /**
     * Pathway interval for PreyAnimalBehavior in milliseconds.
     * Default: 5000ms (5 seconds)
     */
    public long getPathwayIntervalMs() {
        return pathwayIntervalMs.get();
    }
}
