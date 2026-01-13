package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.world.shared.job.JobStatus;
import de.mhus.nimbus.world.shared.job.WJob;
import de.mhus.nimbus.world.shared.job.WJobService;
import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * REST Controller for managing WJob entities.
 * Provides CRUD operations for async jobs within worlds.
 */
@RestController
@RequestMapping("/control/worlds/{worldId}/jobs")
@RequiredArgsConstructor
public class JobController extends BaseEditorController {

    private final WJobService jobService;

    // DTOs

    /**
     * Request DTO for creating jobs.
     */
    public record JobRequest(
            String executor,
            String type,
            Map<String, String> parameters,
            Integer priority,
            Integer maxRetries
    ) {}

    /**
     * Response DTO for job data.
     */
    public record JobResponse(
            String id,
            String worldId,
            String executor,
            String type,
            String status,
            Map<String, String> parameters,
            String result,
            String errorMessage,
            int priority,
            int maxRetries,
            int retryCount,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt,
            Instant modifiedAt,
            boolean enabled
    ) {}

    /**
     * Summary response for job counts.
     */
    public record JobSummaryResponse(
            String worldId,
            long pending,
            long running,
            long completed,
            long failed
    ) {}

    private JobResponse toResponse(WJob job) {
        return new JobResponse(
                job.getId(),
                job.getWorldId(),
                job.getExecutor(),
                job.getType(),
                job.getStatus(),
                job.getParameters(),
                job.getResult(),
                job.getErrorMessage(),
                job.getPriority(),
                job.getMaxRetries(),
                job.getRetryCount(),
                job.getCreatedAt(),
                job.getStartedAt(),
                job.getCompletedAt(),
                job.getModifiedAt(),
                job.isEnabled()
        );
    }

    /**
     * List all jobs in a world
     * GET /control/worlds/{worldId}/jobs
     */
    @GetMapping
    public ResponseEntity<?> list(@PathVariable String worldId) {
        var error = validateId(worldId, "worldId");
        if (error != null) return error;

        try {
            List<JobResponse> result = jobService.getJobsByWorld(worldId).stream()
                    .map(this::toResponse)
                    .toList();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return bad(e.getMessage());
        }
    }

    /**
     * Get job summary (counts by status)
     * GET /control/worlds/{worldId}/jobs/summary
     */
    @GetMapping("/summary")
    public ResponseEntity<?> summary(@PathVariable String worldId) {
        var error = validateId(worldId, "worldId");
        if (error != null) return error;

        try {
            JobSummaryResponse summary = new JobSummaryResponse(
                    worldId,
                    jobService.countJobs(worldId, JobStatus.PENDING),
                    jobService.countJobs(worldId, JobStatus.RUNNING),
                    jobService.countJobs(worldId, JobStatus.COMPLETED),
                    jobService.countJobs(worldId, JobStatus.FAILED)
            );
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            return bad(e.getMessage());
        }
    }

    /**
     * List jobs by status
     * GET /control/worlds/{worldId}/jobs/status/{status}
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<?> listByStatus(
            @PathVariable String worldId,
            @PathVariable String status) {

        var error = validateId(worldId, "worldId");
        if (error != null) return error;

        try {
            JobStatus jobStatus = JobStatus.valueOf(status.toUpperCase());
            List<JobResponse> result = jobService.getJobsByWorldAndStatus(worldId, jobStatus).stream()
                    .map(this::toResponse)
                    .toList();
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return bad("Invalid status: " + status + " (valid: PENDING, RUNNING, COMPLETED, FAILED)");
        } catch (Exception e) {
            return bad(e.getMessage());
        }
    }

    /**
     * Get job by ID
     * GET /control/worlds/{worldId}/jobs/{jobId}
     */
    @GetMapping("/{jobId}")
    public ResponseEntity<?> get(
            @PathVariable String worldId,
            @PathVariable String jobId) {

        var error = validateId(worldId, "worldId");
        if (error != null) return error;

        var error2 = validateId(jobId, "jobId");
        if (error2 != null) return error2;

        return jobService.getJob(jobId)
                .<ResponseEntity<?>>map(job -> {
                    if (!worldId.equals(job.getWorldId())) {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(Map.of("error", "Job not found in this world"));
                    }
                    return ResponseEntity.ok(toResponse(job));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Job not found: " + jobId)));
    }

    /**
     * Create new job
     * POST /control/worlds/{worldId}/jobs
     */
    @PostMapping
    public ResponseEntity<?> create(
            @PathVariable String worldId,
            @RequestBody JobRequest request) {

        var error = validateId(worldId, "worldId");
        if (error != null) return error;

        if (blank(request.executor())) {
            return bad("executor is required");
        }

        try {
            int priority = request.priority() != null ? request.priority() : 5;
            int maxRetries = request.maxRetries() != null ? request.maxRetries() : 0;
            String type = blank(request.type()) ? "" : request.type();

            WJob created = jobService.createJob(
                    worldId,
                    request.executor(),
                    type,
                    request.parameters(),
                    priority,
                    maxRetries
            );

            return ResponseEntity.created(
                            URI.create("/control/worlds/" + worldId + "/jobs/" + created.getId()))
                    .body(toResponse(created));

        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        } catch (Exception e) {
            return bad("Failed to create job: " + e.getMessage());
        }
    }

    /**
     * Update job (limited to parameters)
     * PATCH /control/worlds/{worldId}/jobs/{jobId}
     */
    @PatchMapping("/{jobId}")
    public ResponseEntity<?> patch(
            @PathVariable String worldId,
            @PathVariable String jobId,
            @RequestBody Map<String, Object> updates) {

        var error = validateId(worldId, "worldId");
        if (error != null) return error;

        var error2 = validateId(jobId, "jobId");
        if (error2 != null) return error2;

        try {
            var updated = jobService.updateJob(jobId, job -> {
                if (!worldId.equals(job.getWorldId())) {
                    throw new IllegalArgumentException("Job does not belong to this world");
                }

                if (updates.containsKey("parameters")) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> params = (Map<String, String>) updates.get("parameters");
                    job.setParameters(params);
                }

                if (updates.containsKey("priority")) {
                    job.setPriority((Integer) updates.get("priority"));
                }
            });

            return updated
                    .<ResponseEntity<?>>map(j -> ResponseEntity.ok(toResponse(j)))
                    .orElseGet(() -> notFound("Job not found: " + jobId));

        } catch (ClassCastException e) {
            return bad("Invalid data type in update: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return bad(e.getMessage());
        } catch (Exception e) {
            return bad("Failed to update job: " + e.getMessage());
        }
    }

    /**
     * Cancel job (soft delete)
     * POST /control/worlds/{worldId}/jobs/{jobId}/cancel
     */
    @PostMapping("/{jobId}/cancel")
    public ResponseEntity<?> cancel(
            @PathVariable String worldId,
            @PathVariable String jobId) {

        var error = validateId(worldId, "worldId");
        if (error != null) return error;

        var error2 = validateId(jobId, "jobId");
        if (error2 != null) return error2;

        return jobService.getJob(jobId)
                .map(job -> {
                    if (!worldId.equals(job.getWorldId())) {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(Map.of("error", "Job not found in this world"));
                    }

                    boolean cancelled = jobService.deleteJob(jobId);
                    if (cancelled) {
                        return ResponseEntity.ok(Map.of(
                                "message", "Job cancelled",
                                "jobId", jobId
                        ));
                    } else {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(Map.of("error", "Failed to cancel job"));
                    }
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Job not found: " + jobId)));
    }

    /**
     * Delete job (hard delete)
     * DELETE /control/worlds/{worldId}/jobs/{jobId}
     */
    @DeleteMapping("/{jobId}")
    public ResponseEntity<?> delete(
            @PathVariable String worldId,
            @PathVariable String jobId) {

        var error = validateId(worldId, "worldId");
        if (error != null) return error;

        var error2 = validateId(jobId, "jobId");
        if (error2 != null) return error2;

        return jobService.getJob(jobId)
                .map(job -> {
                    if (!worldId.equals(job.getWorldId())) {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(Map.of("error", "Job not found in this world"));
                    }

                    boolean deleted = jobService.hardDeleteJob(jobId);
                    if (deleted) {
                        return ResponseEntity.noContent().build();
                    } else {
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(Map.of("error", "Failed to delete job"));
                    }
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Job not found: " + jobId)));
    }

    /**
     * Retry a failed job
     * POST /control/worlds/{worldId}/jobs/{jobId}/retry
     */
    @PostMapping("/{jobId}/retry")
    public ResponseEntity<?> retry(
            @PathVariable String worldId,
            @PathVariable String jobId) {

        var error = validateId(worldId, "worldId");
        if (error != null) return error;

        var error2 = validateId(jobId, "jobId");
        if (error2 != null) return error2;

        return jobService.getJob(jobId)
                .map(job -> {
                    if (!worldId.equals(job.getWorldId())) {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(Map.of("error", "Job not found in this world"));
                    }

                    if (!JobStatus.FAILED.name().equals(job.getStatus())) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(Map.of("error", "Only failed jobs can be retried"));
                    }

                    var updated = jobService.updateJob(jobId, j -> {
                        j.setStatus(JobStatus.PENDING.name());
                        j.setStartedAt(null);
                        j.setCompletedAt(null);
                        j.setErrorMessage(null);
                    });

                    return updated
                            .<ResponseEntity<?>>map(j -> ResponseEntity.ok(toResponse(j)))
                            .orElseGet(() -> ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                    .body(Map.of("error", "Failed to retry job")));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Job not found: " + jobId)));
    }
}
