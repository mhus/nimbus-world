package de.mhus.nimbus.world.control.job;

import de.mhus.nimbus.world.control.service.delete.DeleteWorldResources;
import de.mhus.nimbus.world.control.service.repair.ResourceRepairService;
import de.mhus.nimbus.world.shared.job.JobExecutionException;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import de.mhus.nimbus.world.shared.job.WJob;
import de.mhus.nimbus.world.shared.world.WWorldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Job executor for deleting all resources associated with a world.
 *
 * This job deletes world data for a given worldId.
 * Resource types can be specified in the job type field.
 *
 * Job Type Format:
 * - "delete-world-resources" → Delete all resource types
 * - "delete-world-resources:assets" → Delete only assets
 * - "delete-world-resources:assets,chunks,layers" → Delete multiple specific types
 *
 * Available resource types:
 * - assets (with storage)
 * - blockTypes
 * - layers (models and terrain with storage)
 * - entities (models and instances)
 * - itemPositions
 * - hexGrids
 * - chunks (with storage)
 *
 * IMPORTANT: The WWorld entity itself must already be deleted before this job runs.
 * If the WWorld still exists, this job will fail with an error.
 *
 * This ensures that the world is first logically deleted (WWorld entity removed),
 * and then all associated resources are cleaned up asynchronously via this job.
 *
 * Parameters:
 * - worldId (required): World ID whose resources should be deleted
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeleteWorldResourcesJobExecutor implements JobExecutor {

    private final WWorldService worldService;
    private final ResourceRepairService repairService;

    @Override
    public String getExecutorName() {
        return "delete-world-resources";
    }

    @Override
    public JobResult execute(WJob job) throws JobExecutionException {
        try {
            // Parse parameters
            Map<String, String> params = job.getParameters();

            String worldId = params.get("worldId");
            if (worldId == null || worldId.isBlank()) {
                throw new JobExecutionException("Missing required parameter: worldId");
            }

            // Parse resource types from job type (format: "delete-world-resources:assets,chunks")
            List<String> resourceTypes = parseResourceTypesFromJobType(job.getType());

            if (resourceTypes.isEmpty()) {
                log.info("Starting world resources deletion for worldId={} (all types)", worldId);
            } else {
                log.info("Starting world resources deletion for worldId={} (types: {})", worldId, resourceTypes);
            }

            // CRITICAL: Verify that the WWorld entity no longer exists
            // If it still exists, the world hasn't been properly deleted yet
            if (worldService.getByWorldId(worldId).isPresent()) {
                throw new JobExecutionException("Cannot delete world resources: WWorld entity still exists for worldId=" + worldId +
                        ". Please delete the WWorld entity first before cleaning up resources.");
            }

            // Execute deletion services
            StringBuilder resultMessage = new StringBuilder();
            resultMessage.append("Deleted resources for world ").append(worldId);
            if (!resourceTypes.isEmpty()) {
                resultMessage.append(" (types: ").append(resourceTypes).append(")");
            }
            resultMessage.append(":\n");

            repairService.deleteWorldResources(worldId, resourceTypes).forEach(
                    serviceResult -> resultMessage.append("- ")
                            .append(serviceResult.serviceName())
                            .append(": ")
                            .append(serviceResult.success() ? "SUCCESS" : "FAILED")
                            .append(" (").append(serviceResult.message()).append(")")
                            .append("\n")
            );

            String finalMessage = resultMessage.toString();
            log.info("World resources deletion completed:\n{}", finalMessage);

            return JobResult.ofSuccess(finalMessage);

        } catch (JobExecutionException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to execute world resources deletion job", e);
            throw new JobExecutionException("World resources deletion failed: " + e.getMessage(), e);
        }
    }

    /**
     * Parse resource types from job type string.
     * Format: "delete-world-resources" → empty list (all types)
     *         "delete-world-resources:assets,chunks" → ["assets", "chunks"]
     *
     * @param jobType Job type string
     * @return List of resource types (empty = all types)
     */
    private List<String> parseResourceTypesFromJobType(String jobType) {
        if (jobType == null || jobType.isBlank()) {
            return Collections.emptyList();
        }

        // Check if types are specified after colon
        int colonIndex = jobType.indexOf(':');
        if (colonIndex < 0) {
            return Collections.emptyList();
        }

        // Extract types after colon
        String typesString = jobType.substring(colonIndex + 1).trim();
        if (typesString.isEmpty()) {
            return Collections.emptyList();
        }

        // Split by comma and trim
        return Arrays.stream(typesString.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
