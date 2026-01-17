package de.mhus.nimbus.world.control.job;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.control.service.delete.DeleteWorldResources;
import de.mhus.nimbus.world.control.service.repair.ResourceRepairService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import de.mhus.nimbus.world.shared.job.JobExecutionException;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import de.mhus.nimbus.world.shared.job.WJob;

import java.util.*;

/**
 * Job executor for repairing resources across all worlds.
 *
 * This job finds all worlds that have resources and runs repair operations on each.
 * Useful for database-wide cleanup operations.
 *
 * Job Type Format:
 * - "repair-all-worlds-resource" → Repair all resource types for all worlds
 * - "repair-all-worlds-resource:asset" → Repair only assets for all worlds
 * - "repair-all-worlds-resource:asset,storage" → Repair specific types for all worlds
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
 * Parameters: None required
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ResourceRepairAllWorldsJobExecutor implements JobExecutor {

    private final ResourceRepairService repairService;
    private final List<DeleteWorldResources> deleteServices;

    @Override
    public String getExecutorName() {
        return "repair-all-worlds-resource";
    }

    @Override
    public JobResult execute(WJob job) throws JobExecutionException {
        try {
            // Parse resource types from job type
            List<String> types = parseResourceTypesFromJobType(job.getType());

            if (types.isEmpty()) {
                log.info("Starting resource repair for all worlds (all types)");
            } else {
                log.info("Starting resource repair for all worlds (types: {})", types);
            }

            // 1. Collect all known worldIds from all delete services
            Set<String> allKnownWorldIds = new HashSet<>();

            for (DeleteWorldResources service : deleteServices) {
                try {
                    List<String> serviceWorldIds = service.getKnownWorldIds();
                    log.debug("Service {} reports {} worldIds", service.name(), serviceWorldIds.size());
                    allKnownWorldIds.addAll(serviceWorldIds);
                } catch (Exception e) {
                    log.warn("Failed to get worldIds from service {}: {}", service.name(), e.getMessage());
                }
            }

            log.info("Found {} distinct worldIds in resources", allKnownWorldIds.size());

            // 2. Filter to main world IDs (remove instance IDs)
            Set<String> mainWorldIds = new HashSet<>();
            for (String worldId : allKnownWorldIds) {
                var parsed = WorldId.of(worldId);
                if (parsed.isPresent()) {
                    mainWorldIds.add(parsed.get().mainWorld().getId());
                } else {
                    mainWorldIds.add(worldId);
                }
            }

            log.info("After filtering to main worlds: {} worldIds", mainWorldIds.size());

            // 3. Run repair for each world
            StringBuilder resultMessage = new StringBuilder();
            resultMessage.append("Resource repair for all worlds");
            if (!types.isEmpty()) {
                resultMessage.append(" (types: ").append(types).append(")");
            }
            resultMessage.append(":\n\n");

            int totalWorlds = mainWorldIds.size();
            int successfulWorlds = 0;
            int failedWorlds = 0;

            for (String worldIdStr : mainWorldIds) {
                try {
                    WorldId worldId = WorldId.of(worldIdStr).orElseThrow();
                    log.info("Repairing world: {}", worldId);

                    List<ResourceRepairService.ProcessResult> worldResults = repairService.repair(worldId, types);

                    // Check if all repairs succeeded for this world
                    long successCount = worldResults.stream()
                            .filter(ResourceRepairService.ProcessResult::success)
                            .count();

                    if (successCount == worldResults.size()) {
                        successfulWorlds++;
                    } else {
                        failedWorlds++;
                    }

                    // Add summary for this world
                    resultMessage.append(String.format("World %s: %d/%d repairs succeeded\n",
                            worldId, successCount, worldResults.size()));

                    // Add details if any failed
                    if (successCount < worldResults.size()) {
                        for (ResourceRepairService.ProcessResult result : worldResults) {
                            if (!result.success()) {
                                resultMessage.append("  - ")
                                        .append(result.serviceName())
                                        .append(": FAILED - ")
                                        .append(result.message())
                                        .append("\n");
                            }
                        }
                    }

                } catch (Exception e) {
                    failedWorlds++;
                    log.error("Failed to repair world {}: {}", worldIdStr, e.getMessage(), e);
                    resultMessage.append(String.format("World %s: FAILED - %s\n",
                            worldIdStr, e.getMessage()));
                }
            }

            // Add final summary
            resultMessage.append(String.format("\nSummary: %d/%d worlds repaired successfully, %d failed\n",
                    successfulWorlds, totalWorlds, failedWorlds));

            String finalMessage = resultMessage.toString();
            log.info("Resource repair for all worlds completed:\n{}", finalMessage);

            return JobResult.ofSuccess(finalMessage);

        } catch (Exception e) {
            log.error("Failed to execute resource repair for all worlds", e);
            throw new JobExecutionException("Resource repair for all worlds failed: " + e.getMessage(), e);
        }
    }

    /**
     * Parse resource types from job type string.
     * Format: "resourceRepairAllWorlds" → empty list (all types)
     *         "resourceRepairAllWorlds:asset,storage" → ["asset", "storage"]
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
