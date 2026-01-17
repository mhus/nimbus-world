package de.mhus.nimbus.world.control.job;

import de.mhus.nimbus.world.control.service.repair.ResourceRepairService;
import de.mhus.nimbus.world.shared.job.JobExecutionException;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import de.mhus.nimbus.world.shared.job.WJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Job executor for deleting orphaned world resources.
 *
 * This job finds and deletes resources for worlds that no longer exist:
 * 1. Collects all worldIds referenced in resources (assets, chunks, layers, etc.)
 * 2. Filters to main world IDs (removes instance IDs)
 * 3. Checks which worlds still exist in the database
 * 4. Deletes all resources for worlds that no longer exist
 *
 * This is useful for cleanup after world deletions that may have failed or been interrupted.
 *
 * Parameters: None required
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeleteOrphanedWorldResourcesJobExecutor implements JobExecutor {

    private final ResourceRepairService repairService;

    @Override
    public String getExecutorName() {
        return "delete-orphaned-world-resources";
    }

    @Override
    public JobResult execute(WJob job) throws JobExecutionException {
        try {
            log.info("Starting orphaned world resources deletion job");

            // Execute cleanup
            List<ResourceRepairService.ProcessResult> results = repairService.deleteOrphanedWorldResources();

            // Build result message
            StringBuilder resultMessage = new StringBuilder();
            resultMessage.append("Orphaned world resources cleanup:\n");

            long successCount = results.stream().filter(ResourceRepairService.ProcessResult::success).count();
            long totalCount = results.size();

            resultMessage.append(String.format("Summary: %d/%d operations succeeded\n\n", successCount, totalCount));

            // Add details for each result
            for (ResourceRepairService.ProcessResult result : results) {
                resultMessage.append("- ")
                        .append(result.serviceName())
                        .append(": ")
                        .append(result.success() ? "SUCCESS" : "FAILED")
                        .append(" (").append(result.message()).append(")")
                        .append("\n");
            }

            String finalMessage = resultMessage.toString();
            log.info("Orphaned world resources deletion completed:\n{}", finalMessage);

            // Job succeeds even if some operations failed (partial cleanup is acceptable)
            return JobResult.ofSuccess(finalMessage);

        } catch (Exception e) {
            log.error("Failed to execute orphaned world resources deletion job", e);
            throw new JobExecutionException("Orphaned world resources deletion failed: " + e.getMessage(), e);
        }
    }
}
