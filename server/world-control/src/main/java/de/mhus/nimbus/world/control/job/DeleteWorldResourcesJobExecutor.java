package de.mhus.nimbus.world.control.job;

import de.mhus.nimbus.world.control.service.delete.DeleteWorldResources;
import de.mhus.nimbus.world.shared.job.JobExecutionException;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import de.mhus.nimbus.world.shared.job.WJob;
import de.mhus.nimbus.world.shared.world.WWorldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Job executor for deleting all resources associated with a world.
 *
 * This job deletes all world data for a given worldId:
 * - Assets (with storage)
 * - Block types
 * - Layers (models and terrain with storage)
 * - Entities (models and instances)
 * - Item positions
 * - Hex grids
 * - Chunks (with storage)
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

    private final List<DeleteWorldResources> deleteServices;
    private final WWorldService worldService;

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

            log.info("Starting world resources deletion for worldId={}", worldId);

            // CRITICAL: Verify that the WWorld entity no longer exists
            // If it still exists, the world hasn't been properly deleted yet
            if (worldService.getByWorldId(worldId).isPresent()) {
                throw new JobExecutionException("Cannot delete world resources: WWorld entity still exists for worldId=" + worldId +
                        ". Please delete the WWorld entity first before cleaning up resources.");
            }

            // Execute all deletion services in order
            StringBuilder resultMessage = new StringBuilder();
            resultMessage.append("Deleted resources for world ").append(worldId).append(":\n");

            for (DeleteWorldResources service : deleteServices) {
                log.info("Executing deletion service: {}", service.name());

                try {
                    service.deleteWorldResources(worldId);
                    resultMessage.append("- ").append(service.name()).append(": OK\n");

                } catch (Exception e) {
                    String errorMsg = String.format("Failed to delete %s: %s",
                            service.name(), e.getMessage());
                    log.error(errorMsg, e);
                    resultMessage.append("- ").append(service.name()).append(": FAILED - ")
                            .append(e.getMessage()).append("\n");

                    // Continue with other services even if one fails
                    // This allows partial cleanup and better error reporting
                }
            }

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
}
