package de.mhus.nimbus.world.control.job;

import de.mhus.nimbus.world.control.service.duplicate.DuplicateToWorld;
import de.mhus.nimbus.world.shared.job.JobExecutionException;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import de.mhus.nimbus.world.shared.job.WJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Job executor for duplicating a world from source to target.
 *
 * This job duplicates all world data from a source world to a target world:
 * - Assets (with storage)
 * - Block types
 * - Layers (models and terrain with storage)
 * - Entities (models and instances)
 * - Item positions
 *
 * The target world must already exist before duplication begins.
 *
 * Parameters:
 * - sourceWorldId (required): World ID to copy from
 * - targetWorldId (required): World ID to copy to (must already exist)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DuplicateWorldJobExecutor implements JobExecutor {

    private final List<DuplicateToWorld> duplicateServices;

    @Override
    public String getExecutorName() {
        return "duplicate-world";
    }

    @Override
    public JobResult execute(WJob job) throws JobExecutionException {
        try {
            // Parse parameters
            Map<String, String> params = job.getParameters();

            String sourceWorldId = params.get("sourceWorldId");
            if (sourceWorldId == null || sourceWorldId.isBlank()) {
                throw new JobExecutionException("Missing required parameter: sourceWorldId");
            }

            String targetWorldId = params.get("targetWorldId");
            if (targetWorldId == null || targetWorldId.isBlank()) {
                throw new JobExecutionException("Missing required parameter: targetWorldId");
            }

            if (sourceWorldId.equals(targetWorldId)) {
                throw new JobExecutionException("Source and target world IDs must be different");
            }

            log.info("Starting world duplication: sourceWorldId={} targetWorldId={}",
                    sourceWorldId, targetWorldId);

            // Execute all duplication services in order
            StringBuilder resultMessage = new StringBuilder();
            resultMessage.append("Duplicated world from ").append(sourceWorldId)
                    .append(" to ").append(targetWorldId).append(":\n");

            for (DuplicateToWorld service : duplicateServices) {
                log.info("Executing duplication service: {}", service.name());

                try {
                    service.duplicate(sourceWorldId, targetWorldId);
                    resultMessage.append("- ").append(service.name()).append(": OK\n");

                } catch (Exception e) {
                    String errorMsg = String.format("Failed to duplicate %s: %s",
                            service.name(), e.getMessage());
                    log.error(errorMsg, e);
                    resultMessage.append("- ").append(service.name()).append(": FAILED - ")
                            .append(e.getMessage()).append("\n");

                    // Continue with other services even if one fails
                    // This allows partial duplication and better error reporting
                }
            }

            String finalMessage = resultMessage.toString();
            log.info("World duplication completed:\n{}", finalMessage);

            return JobResult.success(finalMessage);

        } catch (JobExecutionException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to execute world duplication job", e);
            throw new JobExecutionException("World duplication failed: " + e.getMessage(), e);
        }
    }
}
