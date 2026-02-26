package de.mhus.nimbus.world.generator.mcp.tools;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.generator.mcp.McpJobException;
import de.mhus.nimbus.world.generator.mcp.McpJobExecutor;
import de.mhus.nimbus.world.generator.mcp.McpJobTimeoutException;
import de.mhus.nimbus.world.generator.mcp.McpToolException;
import de.mhus.nimbus.world.shared.job.JobExecutorRegistry;
import de.mhus.nimbus.world.shared.job.WJob;
import de.mhus.nimbus.world.shared.job.WJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class JobTools {

    private final McpJobExecutor mcpJobExecutor;
    private final JobExecutorRegistry executorRegistry;
    private final WJobService jobService;

    @Tool(name = "execute_job", description = "Execute a job synchronously. Blocks until the job completes, fails, or times out. Returns job result or error.")
    public Map<String, Object> executeJob(
            @ToolParam(description = "World ID") String worldId,
            @ToolParam(description = "Executor name to use") String executor,
            @ToolParam(description = "Optional layer name", required = false) String layer,
            @ToolParam(description = "Executor-specific parameters", required = false) Map<String, String> parameters,
            @ToolParam(description = "Timeout in seconds (max 600, default 300)", required = false) Integer timeoutSeconds) {
        log.debug("MCP: Execute job: worldId={}, executor={}", worldId, executor);

        WorldId.of(worldId).orElseThrow(
                () -> new McpToolException("Invalid worldId: " + worldId)
        );

        if (!executorRegistry.hasExecutor(executor)) {
            throw new McpToolException("Executor not found: " + executor);
        }

        long timeoutMs = 300000; // Default: 5 minutes
        if (timeoutSeconds != null) {
            timeoutMs = timeoutSeconds * 1000L;
        }

        if (timeoutMs > 600000) {
            throw new McpToolException("timeout exceeds maximum of 600 seconds");
        }

        try {
            McpJobExecutor.JobExecutionResult result = McpJobExecutor.builder()
                    .worldId(worldId)
                    .layer(layer)
                    .executor(executor)
                    .parameters(parameters)
                    .timeout(timeoutMs)
                    .build(mcpJobExecutor)
                    .executeAndWait();

            Map<String, Object> response = new HashMap<>();
            response.put("jobId", result.jobId());
            response.put("durationMs", result.durationMs());
            response.put("startedAt", result.startedAt());
            response.put("completedAt", result.completedAt());

            return switch (result.status()) {
                case SUCCESS -> {
                    response.put("status", "COMPLETED");
                    response.put("result", result.result());
                    yield response;
                }
                case FAILURE -> {
                    response.put("status", "FAILED");
                    response.put("error", result.error());
                    yield response;
                }
                case TIMEOUT -> {
                    response.put("status", "RUNNING");
                    response.put("message", "Job exceeded timeout");
                    yield response;
                }
            };

        } catch (McpJobTimeoutException e) {
            Map<String, Object> response = new HashMap<>();
            response.put("jobId", e.getJobId());
            response.put("status", "RUNNING");
            response.put("message", e.getMessage());
            return response;

        } catch (McpJobException e) {
            throw new McpToolException("Job execution failed: " + e.getMessage());
        }
    }

    @Tool(name = "get_job_status", description = "Get the status of a previously executed job by its job ID")
    public Map<String, Object> getJobStatus(
            @ToolParam(description = "Job ID") String jobId) {
        log.debug("MCP: Get job status: jobId={}", jobId);

        Optional<WJob> jobOpt = jobService.getJob(jobId);

        if (jobOpt.isEmpty()) {
            throw new McpToolException("job not found");
        }

        WJob job = jobOpt.get();
        Long duration = null;
        if (job.getStartedAt() != null && job.getCompletedAt() != null) {
            duration = job.getCompletedAt().toEpochMilli() - job.getStartedAt().toEpochMilli();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("jobId", job.getId());
        response.put("status", job.getStatus());
        response.put("result", job.getResult());
        response.put("error", job.getErrorMessage());
        response.put("durationMs", duration);
        response.put("startedAt", job.getStartedAt());
        response.put("completedAt", job.getCompletedAt());
        return response;
    }
}
