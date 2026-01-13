package de.mhus.nimbus.world.shared.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
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
@ConditionalOnExpression("'WorldEditor'.equals('${spring.application.name}')")
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

        try {
            Instant cutoffTime = Instant.now()
                    .minus(properties.getRetentionHours(), ChronoUnit.HOURS);

            log.debug("Starting job cleanup: cutoff={}", cutoffTime);

            List<WJob> jobsToCleanup = jobService.findJobsForCleanup(cutoffTime);

            if (jobsToCleanup.isEmpty()) {
                log.trace("No old jobs to clean up");
                return;
            }

            int deleted = 0;
            int failed = 0;

            for (WJob job : jobsToCleanup) {
                try {
                    if (properties.isHardDelete()) {
                        if (jobService.hardDeleteJob(job.getId())) {
                            deleted++;
                        }
                    } else {
                        if (jobService.deleteJob(job.getId())) {
                            deleted++;
                        }
                    }
                } catch (Exception e) {
                    log.error("Error deleting job: {}", job.getId(), e);
                    failed++;
                }
            }

            log.info("Job cleanup completed: deleted={} failed={} cutoff={}",
                    deleted, failed, cutoffTime);

        } catch (Exception e) {
            log.error("Error during job cleanup", e);
        }
    }
}
