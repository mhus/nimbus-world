package de.mhus.nimbus.world.control.job;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.control.service.repair.ResourceRepairService;
import de.mhus.nimbus.world.shared.job.JobExecutionException;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import de.mhus.nimbus.world.shared.job.WJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Job executor for resource repair operations.
 * Finds and fixes database issues:
 * - Duplicate entries (e.g., with and without _schema field)
 * - Orphaned storage references
 * - Invalid or corrupted entries
 *
 * Job Type Format:
 * - "repair-resources" → Repair all resource types
 * - "repair-resources:asset" → Repair only assets
 * - "repair-resources:asset,backdrop,storage" → Repair multiple specific types
 *
 * Available resource types:
 * - asset
 * - backdrop
 * - blocktype
 * - item
 * - itemtype
 * - itemposition
 * - entity
 * - entitymodel
 * - model
 * - ground
 * - storage
 *
 * Parameters:
 * - worldId: Provided by job.getWorldId()
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ResourceRepairJobExecutor implements JobExecutor {

    private final ResourceRepairService repairService;

    @Override
    public String getExecutorName() {
        return "repair-resources";
    }

    @Override
    public JobResult execute(WJob job) throws JobExecutionException {
        try {
            // Parse resource types from job type (format: "repair-resources:asset,storage")
            List<String> types = parseResourceTypesFromJobType(job.getType());

            // Parse worldId
            String worldIdStr = job.getWorldId();
            if (worldIdStr == null || worldIdStr.isBlank()) {
                throw new JobExecutionException("Missing worldId in job");
            }

            WorldId worldId;
            try {
                WorldId.validate(worldIdStr);
                worldId = WorldId.of(worldIdStr).orElseThrow(
                        () -> new JobExecutionException("Invalid worldId: " + worldIdStr)
                );
            } catch (Exception e) {
                throw new JobExecutionException("Invalid worldId: " + worldIdStr, e);
            }

            if (types.isEmpty()) {
                log.info("Starting resource repair job: worldId={} (all types)", worldId);
            } else {
                log.info("Starting resource repair job: worldId={} (types: {})", worldId, types);
            }

            // Execute repair
            var results = repairService.repair(worldId, types);
            StringBuilder report = new StringBuilder();
            report.append("Resource repair for world ").append(worldId);
            if (!types.isEmpty()) {
                report.append(" (types: ").append(types).append(")");
            }
            report.append(":\n");

            results.forEach(
                    r -> report.append("- ")
                            .append(r.serviceName())
                            .append(": ")
                            .append(r.success() ? "SUCCESS" : "FAILED")
                            .append(" - ")
                            .append(r.message())
                            .append("\n")
            );

            String finalMessage = report.toString();
            log.info("Resource repair completed:\n{}", finalMessage);

            return JobResult.ofSuccess(finalMessage);
        } catch (JobExecutionException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to execute resource repair job", e);
            throw new JobExecutionException("Resource repair job failed: " + e.getMessage(), e);
        }
    }

    /**
     * Parse resource types from job type string.
     * Format: "resourceRepair" → empty list (all types)
     *         "resourceRepair:asset,storage" → ["asset", "storage"]
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
