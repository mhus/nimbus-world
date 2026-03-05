package de.mhus.nimbus.world.shared.job;

import de.mhus.nimbus.shared.types.WorldId;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
@ConditionalOnProperty(
        value = "nimbus.services.job-service",
        havingValue = "true",
        matchIfMissing = false
)
@RequiredArgsConstructor
@Slf4j
public class WJobService {

    private final WJobRepository jobRepository;
    private final JobExecutorRegistry executorRegistry;
    private final MongoTemplate mongoTemplate;

    @Transactional
    public WJob createJob(String worldId, String executor, String title, String type,
                          Map<String, String> parameters) {
        return createJob(worldId, executor, title, type, parameters, null, null, 5, 0, null, null);
    }

    @Transactional
    public WJob createJob(String worldId, String executor, String title, String type,
                          Map<String, String> parameters, int priority, int maxRetries) {
        return createJob(worldId, executor, title, type, parameters, null, null, priority, maxRetries, null, null);
    }

    @Transactional
    @NotNull
    public WJob createJob(String worldId, String executor, String title, String type,
                          Map<String, String> parameters, String location, String parent, int priority, int maxRetries,
                          NextJob onSuccess, NextJob onError) {

        WJob job = WJob.builder()
                .executor(executor)
                .title(title)
                .type(type)
                .location(location)
                .parameters(parameters != null ? parameters : Map.of())
                .priority(priority)
                .parent(parent)
                .maxRetries(maxRetries)
                .onSuccess(onSuccess)
                .onError(onError)
                .build();
        return createJob(worldId, job);
    }

    @Transactional
    public WJob createJob(String worldId, WJob job) {
        // IMPORTANT: Filter out instances - jobs are per world only
        WorldId parsedWorldId = de.mhus.nimbus.shared.types.WorldId.unchecked(worldId);
        String lookupWorldId = parsedWorldId.withoutInstance().getId();

        job.setWorldId(lookupWorldId);
        job.setStatus(JobStatus.PENDING.name());
        if (Strings.isBlank(job.getExecutor())) {
            throw new IllegalArgumentException("Executor must be specified");
        }
        if (Strings.isBlank(job.getTitle())) {
            throw new IllegalArgumentException("Title must be specified");
        }

        job.touchCreate();
        WJob saved = jobRepository.save(job);

        log.info("Created job: id={} world={} executor={} title={} type={} priority={}",
                saved.getId(), worldId, job.getExecutor(), job.getTitle(), job.getType(), job.getPriority());

        return saved;
    }

    @Transactional(readOnly = true)
    public Optional<WJob> getJob(String jobId) {
        return jobRepository.findById(jobId);
    }

    @Transactional(readOnly = true)
    public List<WJob> getJobsByWorld(String worldId) {
        // IMPORTANT: Filter out instances - jobs are per world only
        WorldId parsedWorldId = de.mhus.nimbus.shared.types.WorldId.unchecked(worldId);
        String lookupWorldId = parsedWorldId.withoutInstance().getId();

        return jobRepository.findByWorldId(lookupWorldId);
    }

    @Transactional(readOnly = true)
    public List<WJob> getJobsByWorldAndStatus(String worldId, JobStatus status) {
        // IMPORTANT: Filter out instances - jobs are per world only
        WorldId parsedWorldId = de.mhus.nimbus.shared.types.WorldId.unchecked(worldId);
        String lookupWorldId = parsedWorldId.withoutInstance().getId();

        return jobRepository.findByWorldIdAndStatus(lookupWorldId, status.name());
    }

    @Transactional(readOnly = true)
    public List<WJob> getPendingJobs() {
        return jobRepository.findByStatusAndEnabledOrderByPriorityDescCreatedAtAsc(
                JobStatus.PENDING.name(), true);
    }

    /**
     * Atomically mark a job as RUNNING.
     * Only transitions from PENDING status to prevent double-start.
     */
    public boolean markJobRunning(String jobId) {
        Instant now = Instant.now();
        Query query = new Query(Criteria.where("id").is(jobId)
                .and("status").is(JobStatus.PENDING.name()));
        Update update = new Update()
                .set("status", JobStatus.RUNNING.name())
                .set("startedAt", now)
                .set("modifiedAt", now);

        var result = mongoTemplate.updateFirst(query, update, WJob.class);
        if (result.getModifiedCount() > 0) {
            log.debug("Job started: id={}", jobId);
            return true;
        }
        log.warn("markJobRunning failed: jobId={} - not found or not PENDING", jobId);
        return false;
    }

    /**
     * Atomically mark a job as async.
     */
    public boolean markJobAsync(String jobId, String asyncResult) {
        Instant now = Instant.now();
        Query query = new Query(Criteria.where("id").is(jobId));
        Update update = new Update()
                .set("async", asyncResult)
                .set("modifiedAt", now);

        var result = mongoTemplate.updateFirst(query, update, WJob.class);
        if (result.getModifiedCount() > 0) {
            log.info("Job async: id={}", jobId);
            return true;
        }
        log.warn("markJobAsync failed: jobId={}", jobId);
        return false;
    }

    /**
     * Atomically mark a job as COMPLETED with optional result.
     * Schedules follow-up job if configured.
     */
    public boolean markJobCompleted(String jobId, String resultData) {
        Instant now = Instant.now();
        Query query = new Query(Criteria.where("id").is(jobId)
                .and("status").is(JobStatus.RUNNING.name()));
        Update update = new Update()
                .set("status", JobStatus.COMPLETED.name())
                .set("completedAt", now)
                .set("result", resultData)
                .set("modifiedAt", now);

        var result = mongoTemplate.updateFirst(query, update, WJob.class);
        if (result.getModifiedCount() > 0) {
            log.info("Job completed: id={}", jobId);
            // Schedule follow-up job if configured (needs full job for onSuccess config)
            jobRepository.findById(jobId).ifPresent(job ->
                    scheduleNextJob(job, job.getOnSuccess(), resultData, null));
            return true;
        }
        log.warn("markJobCompleted failed: jobId={} - not found or not RUNNING", jobId);
        return false;
    }

    /**
     * Atomically mark a job as FAILED with error message.
     * Handles retry logic: if retries remaining, resets to PENDING.
     */
    public boolean markJobFailed(String jobId, String errorMessage) {
        Instant now = Instant.now();

        // First: atomically set FAILED, increment retryCount, set error
        Query query = new Query(Criteria.where("id").is(jobId)
                .and("status").is(JobStatus.RUNNING.name()));
        Update update = new Update()
                .set("status", JobStatus.FAILED.name())
                .set("completedAt", now)
                .set("errorMessage", errorMessage)
                .inc("retryCount", 1)
                .set("modifiedAt", now);

        var result = mongoTemplate.updateFirst(query, update, WJob.class);
        if (result.getModifiedCount() == 0) {
            log.warn("markJobFailed failed: jobId={} - not found or not RUNNING", jobId);
            return false;
        }

        // Check if retry is possible and reset to PENDING
        var jobOpt = jobRepository.findById(jobId);
        if (jobOpt.isPresent()) {
            WJob job = jobOpt.get();
            if (job.canRetry()) {
                Query retryQuery = new Query(Criteria.where("id").is(jobId)
                        .and("status").is(JobStatus.FAILED.name()));
                Update retryUpdate = new Update()
                        .set("status", JobStatus.PENDING.name())
                        .unset("startedAt")
                        .set("modifiedAt", Instant.now());
                mongoTemplate.updateFirst(retryQuery, retryUpdate, WJob.class);
                log.info("Job failed, retrying: id={} retry={}/{} error={}",
                        jobId, job.getRetryCount(), job.getMaxRetries(), errorMessage);
            } else {
                log.error("Job failed: id={} error={}", jobId, errorMessage);
                scheduleNextJob(job, job.getOnError(), null, errorMessage);
            }
        }
        return true;
    }

    /**
     * Atomically update specific fields on a job.
     * For callers that need to set individual fields without load-modify-save.
     */
    public boolean updateJobFields(String jobId, Map<String, Object> fields) {
        Query query = new Query(Criteria.where("id").is(jobId));
        Update update = new Update().set("modifiedAt", Instant.now());
        for (var entry : fields.entrySet()) {
            update.set(entry.getKey(), entry.getValue());
        }

        var result = mongoTemplate.updateFirst(query, update, WJob.class);
        if (result.getModifiedCount() > 0) {
            return true;
        }
        log.warn("updateJobFields failed: jobId={}", jobId);
        return false;
    }

    @Transactional
    public Optional<WJob> updateJob(String jobId, Consumer<WJob> updater) {
        return jobRepository.findById(jobId).map(job -> {
            updater.accept(job);
            job.touchUpdate();
            return jobRepository.save(job);
        });
    }

    /**
     * Atomically soft-delete a job.
     */
    public boolean deleteJob(String jobId) {
        Query query = new Query(Criteria.where("id").is(jobId)
                .and("enabled").is(true));
        Update update = new Update()
                .set("enabled", false)
                .set("modifiedAt", Instant.now());

        var result = mongoTemplate.updateFirst(query, update, WJob.class);
        if (result.getModifiedCount() > 0) {
            log.debug("Job soft-deleted: id={}", jobId);
            return true;
        }
        return false;
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
        WorldId parsedWorldId = de.mhus.nimbus.shared.types.WorldId.unchecked(worldId);
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
        WorldId parsedWorldId = de.mhus.nimbus.shared.types.WorldId.unchecked(worldId);
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
                    String title = job.getTitle();
                    String type = job.getType();
                    String status = job.getStatus();
                    return (id != null && id.toLowerCase().contains(lowerQuery)) ||
                            (executor != null && executor.toLowerCase().contains(lowerQuery)) ||
                            (title != null && title.toLowerCase().contains(lowerQuery)) ||
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

    @Transactional(readOnly = true)
    public void cleanup(long retentionHours) {
        try {
            Instant cutoffTime = Instant.now()
                    .minus(retentionHours, ChronoUnit.HOURS);

            log.debug("Starting job cleanup: cutoff={}", cutoffTime);

            List<WJob> jobsToCleanup = findJobsForCleanup(cutoffTime);

            if (jobsToCleanup.isEmpty()) {
                log.trace("No old jobs to clean up");
                return;
            }

            int deleted = 0;
            int failed = 0;

            for (WJob job : jobsToCleanup) {
                try {
                    if (hardDeleteJob(job.getId())) {
                        deleted++;
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

    @Transactional
    public void processJob(WJob job) {
        log.debug("Processing job: id={} world={} executor={} type={}",
                job.getId(), job.getWorldId(), job.getExecutor(), job.getType());

        markJobRunning(job.getId());

        JobExecutor executor = executorRegistry.getExecutor(job.getExecutor())
                .orElseThrow(() -> new IllegalStateException(
                        "Executor not found: " + job.getExecutor()));

        try {
            JobExecutor.JobResult result = executor.execute(job);

            if (result.async()) {
                markJobAsync(job.getId(), result.resultData());
            } else
            if (result.successful()) {
                markJobCompleted(job.getId(), result.resultData());
            } else {
                markJobFailed(job.getId(), result.errorMessage());
            }

        } catch (JobExecutionException e) {
            log.error("Job execution failed: id={} error={}", job.getId(), e.getMessage());
            markJobFailed(job.getId(), e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error during job execution: id={}", job.getId(), e);
            String errorMessage = "Internal error: " + e.getClass().getSimpleName() + ": " + e.getMessage();
            markJobFailed(job.getId(), errorMessage);
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
    @Transactional
    protected void scheduleNextJob(WJob completedJob, NextJob nextJobConfig, String result, String errorMessage) {
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
            parameters.put(JobExecutor.PREVIOUS_JOB_ID, completedJob.getId());
            if (result != null) {
                parameters.put(JobExecutor.PREVIOUS_JOB_RESULT, result);
            }
            if (errorMessage != null) {
                parameters.put(JobExecutor.PREVIOUS_JOB_ERROR_MESSAGE, errorMessage);
            }

            // Create the next job with the same worldId
            String nextJobTitle = "Follow-up: " + (nextJobConfig.getType() != null ? nextJobConfig.getType() : nextJobConfig.getExecutor());
            WJob nextJob = createJob(
                    completedJob.getWorldId(),
                    nextJobConfig.getExecutor(),
                    nextJobTitle,
                    nextJobConfig.getType() != null ? nextJobConfig.getType() : nextJobConfig.getExecutor(),
                    parameters,
                    nextJobConfig.getLocation(),
                    "job:" + completedJob.getId(), // parent reference
                    5,
                    0,
                    null,
                    null
            );

            log.info("Scheduled follow-up job: nextJobId={} previousJobId={} executor={}",
                    nextJob.getId(), completedJob.getId(), nextJobConfig.getExecutor());

        } catch (Exception e) {
            log.error("Failed to schedule follow-up job for completed job: {} - error: {}",
                    completedJob.getId(), e.getMessage(), e);
        }
    }

    /**
     * Atomically migrate a job to a different world.
     * Only updates if the job currently belongs to the specified worldId.
     */
    public boolean emigrateToWorld(String worldId, String jobId, String newWorldId) {
        Query query = new Query(Criteria.where("id").is(jobId)
                .and("worldId").is(worldId));
        Update update = new Update()
                .set("worldId", newWorldId)
                .set("modifiedAt", Instant.now());

        var result = mongoTemplate.updateFirst(query, update, WJob.class);
        if (result.getModifiedCount() > 0) {
            log.debug("Migrated job {} from world {} to world {}", jobId, worldId, newWorldId);
            return true;
        }
        log.warn("Job {} does not belong to world {}, cannot migrate", jobId, worldId);
        return false;
    }
}
