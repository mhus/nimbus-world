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
 * Parameters:
 * - types (optional): Comma-separated list of types to repair (default: all types)
 * - dryRun (optional): "true" or "false" (default: "false") - only report issues without fixing
 * - worldId: Provided by job.getWorldId()
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ResourceRepairJobExecutor implements JobExecutor {

    private final ResourceRepairService repairService;

    @Override
    public String getExecutorName() {
        return "resourceRepair";
    }

    @Override
    public JobResult execute(WJob job) throws JobExecutionException {
        try {
            // Parse parameters
            Map<String, String> params = job.getParameters();
            boolean dryRun = parseBooleanParameter(params, "dryRun", false);
            List<String> types = parseTypesParameter(params);

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

            log.info("Starting resource repair job: worldId={} types={} dryRun={}",
                    worldId, types.isEmpty() ? "all" : types, dryRun);

            // Execute repair
            ResourceRepairService.RepairResult result = repairService.repair(worldId, types, dryRun);

            // Return result
            if (result.success()) {
                String resultMessage = String.format(
                        "Resource repair completed %s: worldId=%s types=%s " +
                        "duplicates=%d/%d orphanedStorage=%d/%d totalIssues=%d/%d",
                        dryRun ? "(DRY RUN)" : "successfully",
                        worldId,
                        types.isEmpty() ? "all" : types,
                        result.duplicatesRemoved(), result.duplicatesFound(),
                        result.orphanedStorageRemoved(), result.orphanedStorageFound(),
                        result.totalIssuesFixed(), result.totalIssuesFound()
                );
                log.info(resultMessage);
                return JobResult.ofSuccess(resultMessage);
            } else {
                String errorMessage = String.format(
                        "Resource repair failed: worldId=%s error=%s",
                        worldId, result.errorMessage()
                );
                log.error(errorMessage);
                return JobResult.ofFailure(errorMessage);
            }

        } catch (JobExecutionException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to execute resource repair job", e);
            throw new JobExecutionException("Resource repair job failed: " + e.getMessage(), e);
        }
    }

    /**
     * Parse boolean parameter with default value.
     */
    private boolean parseBooleanParameter(Map<String, String> params, String key, boolean defaultValue) {
        String value = params.get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value.trim());
    }

    /**
     * Parse types parameter (comma-separated list).
     */
    private List<String> parseTypesParameter(Map<String, String> params) {
        String typesParam = params.get("types");
        if (typesParam == null || typesParam.isBlank()) {
            return Collections.emptyList();
        }

        return Arrays.stream(typesParam.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
