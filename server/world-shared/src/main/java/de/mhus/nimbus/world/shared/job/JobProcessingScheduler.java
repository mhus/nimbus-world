package de.mhus.nimbus.world.shared.job;

import de.mhus.nimbus.world.shared.redis.WorldRedisLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Scheduled task that processes pending jobs.
 * Uses Redis locks to prevent concurrent execution across pods.
 */
@Component
@ConditionalOnExpression("'WorldEditor'.equals('${spring.application.name}') || 'WorldGenerator'.equals('${spring.application.name}')")
@RequiredArgsConstructor
@Slf4j
public class JobProcessingScheduler {

    private final WJobService jobService;
    private final JobExecutorRegistry executorRegistry;
    private final WorldRedisLockService lockService;
    private final JobSettings properties;

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

                String lockToken = lockService.acquireGenericLock(
                        "job:" + job.getId(), JOB_LOCK_TTL);

                if (lockToken == null) {
                    log.debug("Job {} is locked by another pod, skipping", job.getId());
                    skipped++;
                    continue;
                }

                try {
                    processJob(job);
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

    private void processJob(WJob job) {
        log.debug("Processing job: id={} world={} executor={} type={}",
                job.getId(), job.getWorldId(), job.getExecutor(), job.getType());

        jobService.markJobRunning(job.getId());

        JobExecutor executor = executorRegistry.getExecutor(job.getExecutor())
                .orElseThrow(() -> new IllegalStateException(
                        "Executor not found: " + job.getExecutor()));

        try {
            JobExecutor.JobResult result = executor.execute(job);

            if (result.success()) {
                jobService.markJobCompleted(job.getId(), result.resultData());
                scheduleNextJob(job, job.getOnSuccess(), result.resultData(), null);
            } else {
                jobService.markJobFailed(job.getId(), result.errorMessage());
                scheduleNextJob(job, job.getOnError(), null, result.errorMessage());
            }

        } catch (JobExecutionException e) {
            log.error("Job execution failed: id={} error={}", job.getId(), e.getMessage());
            jobService.markJobFailed(job.getId(), e.getMessage());
            scheduleNextJob(job, job.getOnError(), null, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during job execution: id={}", job.getId(), e);
            String errorMessage = "Internal error: " + e.getClass().getSimpleName() + ": " + e.getMessage();
            jobService.markJobFailed(job.getId(), errorMessage);
            scheduleNextJob(job, job.getOnError(), null, errorMessage);
        }
    }

    /**
     * Schedule a follow-up job based on the completion of the current job.
     * Automatically adds previousJobId, previousJobResult, and previousJobErrorMessage as parameters.
     *
     * @param completedJob The job that just completed
     * @param nextJobConfig Configuration for the next job (can be null)
     * @param result Result data from the completed job (null if failed)
     * @param errorMessage Error message from the completed job (null if successful)
     */
    private void scheduleNextJob(WJob completedJob, NextJob nextJobConfig, String result, String errorMessage) {
        if (nextJobConfig == null) {
            log.debug("No follow-up job configured for job: {}", completedJob.getId());
            return;
        }

        if (nextJobConfig.getExecutor() == null || nextJobConfig.getExecutor().isBlank()) {
            log.warn("Follow-up job has no executor defined for job: {}", completedJob.getId());
            return;
        }

        try {
            // Merge user-defined parameters with automatic parameters
            java.util.Map<String, String> parameters = new java.util.HashMap<>(
                    nextJobConfig.getParameters() != null ? nextJobConfig.getParameters() : java.util.Map.of()
            );

            // Add automatic parameters from the completed job
            parameters.put("previousJobId", completedJob.getId());
            if (result != null) {
                parameters.put("previousJobResult", result);
            }
            if (errorMessage != null) {
                parameters.put("previousJobErrorMessage", errorMessage);
            }

            // Create the next job with the same worldId
            WJob nextJob = jobService.createJob(
                    completedJob.getWorldId(),
                    nextJobConfig.getExecutor(),
                    nextJobConfig.getType() != null ? nextJobConfig.getType() : nextJobConfig.getExecutor(),
                    parameters
            );

            log.info("Scheduled follow-up job: nextJobId={} previousJobId={} executor={}",
                    nextJob.getId(), completedJob.getId(), nextJobConfig.getExecutor());

        } catch (Exception e) {
            log.error("Failed to schedule follow-up job for completed job: {} - error: {}",
                    completedJob.getId(), e.getMessage(), e);
        }
    }
}
