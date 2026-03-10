package de.mhus.nimbus.world.control.service;

import de.mhus.nimbus.shared.utils.LocationService;
import de.mhus.nimbus.world.control.config.InstanceCleanupProperties;
import de.mhus.nimbus.world.shared.world.WWorldInstance;
import de.mhus.nimbus.world.shared.world.WWorldInstanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Scheduled service for automatic cleanup of unused world instances.
 * Runs periodically (default: every hour) and deletes instances that haven't been used
 * for longer than the configured maximum age (default: 48 hours).
 *
 * Only runs in world-control service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "nimbus.instance.cleanup", name = "enabled", havingValue = "true", matchIfMissing = true)
public class InstanceCleanupScheduler {

    private final WWorldInstanceService instanceService;
    private final InstanceCleanupProperties properties;
    private final LocationService locationService;

    /**
     * Scheduled cleanup task.
     * Runs at fixed intervals configured via nimbus.instance.cleanup.cleanupIntervalMs.
     * Default: every hour (3600000ms).
     */
    @Scheduled(fixedDelayString = "#{@instanceCleanupProperties.cleanupIntervalMs}")
    public void cleanupUnusedInstances() {
        // Only run in world-control
        if (!locationService.isWorldControl()) {
            log.debug("Instance cleanup skipped - not running in world-control");
            return;
        }

        if (!properties.isEnabled()) {
            log.debug("Instance cleanup is disabled");
            return;
        }

        log.info("Starting automatic instance cleanup (maxAge={}h, dryRun={})",
                properties.getMaxAgeHours(), properties.isDryRun());

        try {
            // Calculate cutoff time
            Instant cutoffTime = Instant.now().minus(properties.getMaxAgeHours(), ChronoUnit.HOURS);

            // Find instances older than cutoff time
            List<WWorldInstance> oldInstances = instanceService.findByUpdatedAtBefore(cutoffTime);
            log.debug("Found {} instances older than cutoff", oldInstances.size());

            int deletedCount = 0;
            int skippedCount = 0;

            for (WWorldInstance instance : oldInstances) {
                // Stop if we reached the max deletes limit
                if (deletedCount >= properties.getMaxDeletesPerRun()) {
                    log.warn("Reached max deletes per run limit ({}), stopping cleanup", properties.getMaxDeletesPerRun());
                    break;
                }

                // Instance is already filtered by updatedAtBefore, but validate null check
                if (instance.getUpdatedAt() == null) {
                    skippedCount++;
                    continue;
                }

                {
                    if (properties.isDryRun()) {
                        log.info("DRY-RUN: Would delete instance: instanceId={}, updatedAt={}, age={}h, activePlayers={}",
                                instance.getInstanceId(),
                                instance.getUpdatedAt(),
                                calculateAgeHours(instance.getUpdatedAt()),
                                instance.getActivePlayerCount());
                        deletedCount++;
                    } else {
                        try {
                            log.info("Deleting unused instance: instanceId={}, updatedAt={}, age={}h, activePlayers={}",
                                    instance.getInstanceId(),
                                    instance.getUpdatedAt(),
                                    calculateAgeHours(instance.getUpdatedAt()),
                                    instance.getActivePlayerCount());

                            boolean deleted = instanceService.delete(instance.getInstanceId());

                            if (deleted) {
                                deletedCount++;
                                log.info("Instance deleted successfully: instanceId={}", instance.getInstanceId());
                            } else {
                                log.warn("Failed to delete instance (not found): instanceId={}", instance.getInstanceId());
                                skippedCount++;
                            }
                        } catch (Exception e) {
                            log.error("Error deleting instance {}: {}", instance.getInstanceId(), e.getMessage(), e);
                            skippedCount++;
                        }
                    }
                }
            }

            log.info("Instance cleanup completed: deleted={}, skipped={}, candidates={}, dryRun={}",
                    deletedCount, skippedCount, oldInstances.size(), properties.isDryRun());

        } catch (Exception e) {
            log.error("Instance cleanup failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Calculate the age of an instance in hours.
     *
     * @param updatedAt The last update timestamp
     * @return Age in hours
     */
    private long calculateAgeHours(Instant updatedAt) {
        if (updatedAt == null) {
            return -1;
        }
        return ChronoUnit.HOURS.between(updatedAt, Instant.now());
    }
}
