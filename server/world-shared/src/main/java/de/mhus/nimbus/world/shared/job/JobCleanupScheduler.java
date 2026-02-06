package de.mhus.nimbus.world.shared.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Scheduled task that cleans up old completed/failed jobs.
 * Runs periodically to delete jobs older than retention threshold.
 * Disabled in WorldPlayer module (stateless, no persistent job state).
 */
@Component
@ConditionalOnProperty(
        value = "nimbus.services.job-cleanup",
        havingValue = "true",
        matchIfMissing = false
)
@RequiredArgsConstructor
@Slf4j
public class JobCleanupScheduler {

    private final WJobService jobService;
    private final JobSettings properties;

    /**
     * Clean up old jobs at fixed intervals.
     */
    @Scheduled(fixedDelayString = "#{${world.job.cleanup-interval-ms:3600000}}")
    public void cleanupOldJobs() {
        if (!properties.isCleanupEnabled()) {
            return;
        }

        jobService.cleanup(properties.getRetentionHours());
    }
}
