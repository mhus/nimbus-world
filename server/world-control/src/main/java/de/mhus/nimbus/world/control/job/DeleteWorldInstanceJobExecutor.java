package de.mhus.nimbus.world.control.job;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.job.JobExecutionException;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import de.mhus.nimbus.world.shared.job.WJob;
import de.mhus.nimbus.world.shared.world.WWorldInstanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Job executor for deleting a world instance.
 *
 * Deletes the WWorldInstance entity identified by the full instance worldId.
 * The job is typically created when the last active player leaves an instance.
 *
 * Parameters:
 * - instanceWorldId (required): Full worldId including instance part (e.g., "region:world::uuid")
 * - force (optional): If "true", delete regardless of active players. Default: false.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeleteWorldInstanceJobExecutor implements JobExecutor {

    public static final String EXECUTOR_NAME = "delete-world-instance";
    public static final String PARAM_INSTANCE_WORLD_ID = "instanceWorldId";
    public static final String PARAM_FORCE = "force";

    private final WWorldInstanceService instanceService;

    @Override
    public String getExecutorName() {
        return EXECUTOR_NAME;
    }

    @Override
    public JobResult execute(WJob job) throws JobExecutionException {
        try {
            Map<String, String> params = job.getParameters();

            String instanceWorldId = params.get(PARAM_INSTANCE_WORLD_ID);
            if (Strings.isBlank(instanceWorldId)) {
                throw new JobExecutionException("Missing required parameter: " + PARAM_INSTANCE_WORLD_ID);
            }

            // Validate it's actually an instance worldId
            WorldId worldId = WorldId.unchecked(instanceWorldId);
            if (!worldId.isInstance()) {
                throw new JobExecutionException("WorldId is not an instance: " + instanceWorldId);
            }

            boolean force = "true".equalsIgnoreCase(params.get(PARAM_FORCE));

            // Without force: only delete if no active players remain
            if (!force) {
                var instanceOpt = instanceService.findByInstanceId(instanceWorldId);
                if (instanceOpt.isPresent() && !instanceOpt.get().hasNoActivePlayers()) {
                    log.info("Instance {} still has active players, skipping deletion (use force=true to override)",
                            instanceWorldId);
                    return JobResult.success("Instance still has active players, deletion skipped: " + instanceWorldId);
                }
            }

            log.info("Deleting world instance: {} (force={})", instanceWorldId, force);

            boolean deleted = instanceService.delete(instanceWorldId);

            if (deleted) {
                log.info("World instance deleted successfully: {}", instanceWorldId);
                return JobResult.success("Deleted instance: " + instanceWorldId);
            } else {
                log.info("World instance not found (already deleted?): {}", instanceWorldId);
                return JobResult.success("Instance not found (already deleted): " + instanceWorldId);
            }

        } catch (JobExecutionException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to delete world instance", e);
            throw new JobExecutionException("World instance deletion failed: " + e.getMessage(), e);
        }
    }
}
