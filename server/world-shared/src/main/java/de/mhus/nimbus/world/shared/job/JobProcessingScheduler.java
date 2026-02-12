package de.mhus.nimbus.world.shared.job;

import de.mhus.nimbus.shared.utils.LocationService;
import de.mhus.nimbus.world.shared.redis.WorldRedisLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Scheduled task that processes pending jobs.
 * Uses Redis locks to prevent concurrent execution across pods.
 */
@Component
@ConditionalOnProperty(
        value = "nimbus.services.job-processing",
        havingValue = "true",
        matchIfMissing = false
)
@RequiredArgsConstructor
@Slf4j
public class JobProcessingScheduler {

    private final WJobService jobService;
    private final JobExecutorRegistry executorRegistry;
    private final WorldRedisLockService lockService;
    private final JobSettings properties;
    private final LocationService locationService;

    private static final Duration JOB_LOCK_TTL = Duration.ofMinutes(5);

    /**
     * Process pending jobs at fixed intervals.
     */
    @Scheduled(fixedDelayString = "#{${world.job.processing-interval-ms:5000}}")
    public void processPendingJobs() {
        if (!properties.isProcessingEnabled()) {
            return;
        }

        try {
            List<WJob> pendingJobs = jobService.getPendingJobs();

            if (pendingJobs.isEmpty()) {
                log.trace("No pending jobs to process");
                return;
            }

            log.debug("Found {} pending jobs", pendingJobs.size());

            int processed = 0;
            int skipped = 0;
            int failed = 0;

            for (WJob job : pendingJobs) {
                if (processed >= properties.getMaxJobsPerCycle()) {
                    log.debug("Reached max jobs per cycle ({}), stopping",
                            properties.getMaxJobsPerCycle());
                    break;
                }

                if (!executorRegistry.hasExecutor(job.getExecutor())) {
                    log.warn("Job {} has unknown executor: {}, skipping",
                            job.getId(), job.getExecutor());
                    skipped++;
                    continue;
                }

                if (job.getLocation() != null && !job.getLocation().isBlank()) {
                    String currentServerName = locationService.getApplicationServiceName();
                    if (!job.getLocation().equals(currentServerName)) {
                        log.debug("Job {} is for location '{}', but this is '{}', skipping",
                                job.getId(), job.getLocation(), currentServerName);
                        skipped++;
                        continue;
                    }
                }

                String lockToken = lockService.acquireGenericLock(
                        "job:" + job.getId(), JOB_LOCK_TTL);

                if (lockToken == null) {
                    log.debug("Job {} is locked by another pod, skipping", job.getId());
                    skipped++;
                    continue;
                }

                try {
                    jobService.processJob(job);
                    processed++;
                } catch (Exception e) {
                    log.error("Error processing job: {}", job.getId(), e);
                    failed++;
                } finally {
                    lockService.releaseGenericLock("job:" + job.getId(), lockToken);
                }
            }

            if (processed > 0 || failed > 0) {
                log.info("Job processing cycle: processed={} skipped={} failed={} remaining={}",
                        processed, skipped, failed, pendingJobs.size() - processed - skipped - failed);
            }

        } catch (Exception e) {
            log.error("Error during job processing cycle", e);
        }
    }

}
