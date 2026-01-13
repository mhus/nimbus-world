package de.mhus.nimbus.world.shared.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Service for job management.
 * Provides CRUD operations and job state transitions.
 *
 * Jobs exist per world (no instances).
 * Instances cannot have their own jobs - always taken from the defined world.
 * No COW for branches - jobs are independent per world/branch.
 */
@Service
@ConditionalOnExpression("!'WorldPlayer'.equals('${spring.application.name}')")
@RequiredArgsConstructor
@Slf4j
public class WJobService {

    private final WJobRepository jobRepository;
    private final JobExecutorRegistry executorRegistry;

    @Transactional
    public WJob createJob(String worldId, String executor, String type,
                          Map<String, String> parameters) {
        return createJob(worldId, executor, type, parameters, 5, 0);
    }

    @Transactional
    public WJob createJob(String worldId, String executor, String type,
                          Map<String, String> parameters, int priority, int maxRetries) {

        if (!executorRegistry.hasExecutor(executor)) {
            log.warn("Creating job with unknown executor: {}", executor);
        }

        // IMPORTANT: Filter out instances - jobs are per world only
        de.mhus.nimbus.shared.types.WorldId parsedWorldId = de.mhus.nimbus.shared.types.WorldId.unchecked(worldId);
        String lookupWorldId = parsedWorldId.withoutInstance().getId();

        WJob job = WJob.builder()
                .worldId(lookupWorldId)
                .executor(executor)
                .type(type)
                .status(JobStatus.PENDING.name())
                .parameters(parameters != null ? parameters : Map.of())
                .priority(priority)
                .maxRetries(maxRetries)
                .build();

        job.touchCreate();
        WJob saved = jobRepository.save(job);

        log.info("Created job: id={} world={} executor={} type={} priority={}",
                saved.getId(), worldId, executor, type, priority);

        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<WJob> getJob(String jobId) {
        return jobRepository.findById(jobId);
    }

    @Transactional(readOnly = true)
    public List<WJob> getJobsByWorld(String worldId) {
        // IMPORTANT: Filter out instances - jobs are per world only
        de.mhus.nimbus.shared.types.WorldId parsedWorldId = de.mhus.nimbus.shared.types.WorldId.unchecked(worldId);
        String lookupWorldId = parsedWorldId.withoutInstance().getId();

        return jobRepository.findByWorldId(lookupWorldId);
    }

    @Transactional(readOnly = true)
    public List<WJob> getJobsByWorldAndStatus(String worldId, JobStatus status) {
        // IMPORTANT: Filter out instances - jobs are per world only
        de.mhus.nimbus.shared.types.WorldId parsedWorldId = de.mhus.nimbus.shared.types.WorldId.unchecked(worldId);
        String lookupWorldId = parsedWorldId.withoutInstance().getId();

        return jobRepository.findByWorldIdAndStatus(lookupWorldId, status.name());
    }

    @Transactional(readOnly = true)
    public List<WJob> getPendingJobs() {
        return jobRepository.findByStatusAndEnabledOrderByPriorityDescCreatedAtAsc(
                JobStatus.PENDING.name(), true);
    }

    @Transactional
    public Optional<WJob> markJobRunning(String jobId) {
        return jobRepository.findById(jobId).map(job -> {
            job.markStarted();
            WJob saved = jobRepository.save(job);
            log.debug("Job started: id={} world={} executor={}",
                    jobId, job.getWorldId(), job.getExecutor());
            return saved;
        });
    }

    @Transactional
    public Optional<WJob> markJobCompleted(String jobId, String result) {
        return jobRepository.findById(jobId).map(job -> {
            job.markCompleted(result);
            WJob saved = jobRepository.save(job);
            log.info("Job completed: id={} world={} executor={} duration={}ms",
                    jobId, job.getWorldId(), job.getExecutor(),
                    calculateDuration(job));
            return saved;
        });
    }

    @Transactional
    public Optional<WJob> markJobFailed(String jobId, String errorMessage) {
        return jobRepository.findById(jobId).map(job -> {
            job.markFailed(errorMessage);

            if (job.canRetry()) {
                job.setStatus(JobStatus.PENDING.name());
                job.setStartedAt(null);
                log.info("Job failed, retrying: id={} world={} executor={} retry={}/{} error={}",
                        jobId, job.getWorldId(), job.getExecutor(),
                        job.getRetryCount(), job.getMaxRetries(), errorMessage);
            } else {
                log.error("Job failed: id={} world={} executor={} error={}",
                        jobId, job.getWorldId(), job.getExecutor(), errorMessage);
            }

            return jobRepository.save(job);
        });
    }

    @Transactional
    public Optional<WJob> updateJob(String jobId, Consumer<WJob> updater) {
        return jobRepository.findById(jobId).map(job -> {
            updater.accept(job);
            job.touchUpdate();
            return jobRepository.save(job);
        });
    }

    @Transactional
    public boolean deleteJob(String jobId) {
        return jobRepository.findById(jobId).map(job -> {
            job.setEnabled(false);
            job.touchUpdate();
            jobRepository.save(job);
            log.debug("Job soft-deleted: id={}", jobId);
            return true;
        }).orElse(false);
    }

    @Transactional
    public boolean hardDeleteJob(String jobId) {
        if (jobRepository.existsById(jobId)) {
            jobRepository.deleteById(jobId);
            log.debug("Job hard-deleted: id={}", jobId);
            return true;
        }
        return false;
    }

    @Transactional(readOnly = true)
    public List<WJob> findJobsForCleanup(Instant cutoffTime) {
        return jobRepository.findByStatusInAndCompletedAtBefore(
                List.of(JobStatus.COMPLETED.name(), JobStatus.FAILED.name()),
                cutoffTime
        );
    }

    @Transactional(readOnly = true)
    public long countJobs(String worldId, JobStatus status) {
        // IMPORTANT: Filter out instances - jobs are per world only
        de.mhus.nimbus.shared.types.WorldId parsedWorldId = de.mhus.nimbus.shared.types.WorldId.unchecked(worldId);
        String lookupWorldId = parsedWorldId.withoutInstance().getId();

        return jobRepository.countByWorldIdAndStatus(lookupWorldId, status.name());
    }

    /**
     * Find all jobs for a world with optional query filter.
     * Filters out instances - jobs are per world only.
     */
    @Transactional(readOnly = true)
    public List<WJob> getJobsByWorldAndQuery(String worldId, String query) {
        // IMPORTANT: Filter out instances - jobs are per world only
        de.mhus.nimbus.shared.types.WorldId parsedWorldId = de.mhus.nimbus.shared.types.WorldId.unchecked(worldId);
        String lookupWorldId = parsedWorldId.withoutInstance().getId();

        List<WJob> all = jobRepository.findByWorldId(lookupWorldId);

        // Apply search filter if provided
        if (query != null && !query.isBlank()) {
            all = filterByQuery(all, query);
        }

        return all;
    }

    private List<WJob> filterByQuery(List<WJob> jobs, String query) {
        String lowerQuery = query.toLowerCase();
        return jobs.stream()
                .filter(job -> {
                    String id = job.getId();
                    String executor = job.getExecutor();
                    String type = job.getType();
                    String status = job.getStatus();
                    return (id != null && id.toLowerCase().contains(lowerQuery)) ||
                            (executor != null && executor.toLowerCase().contains(lowerQuery)) ||
                            (type != null && type.toLowerCase().contains(lowerQuery)) ||
                            (status != null && status.toLowerCase().contains(lowerQuery));
                })
                .toList();
    }

    private Long calculateDuration(WJob job) {
        if (job.getStartedAt() != null && job.getCompletedAt() != null) {
            return job.getCompletedAt().toEpochMilli() - job.getStartedAt().toEpochMilli();
        }
        return null;
    }
}
