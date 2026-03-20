package de.mhus.nimbus.world.control.job;

import de.mhus.nimbus.world.control.service.UniverseClientService;
import de.mhus.nimbus.world.shared.job.JobExecutionException;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import de.mhus.nimbus.world.shared.job.WJob;
import de.mhus.nimbus.world.shared.world.WWorldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Job executor that syncs a world with the universe.
 * Registers or unregisters the world based on its universeSync and enabled flags.
 *
 * The worldId is taken from the job context (job.getWorldId()).
 * No additional parameters are needed.
 *
 * Job type: "universe-sync-world"
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UniverseSyncWorldJobExecutor implements JobExecutor {

    private final WWorldService worldService;
    private final UniverseClientService universeClientService;

    @Override
    public String getExecutorName() {
        return "universe-sync-world";
    }

    @Override
    public JobResult execute(WJob job) throws JobExecutionException {
        String worldId = job.getWorldId();
        if (worldId == null || worldId.isBlank()) {
            return JobResult.failure("worldId is missing from job context");
        }

        var worldOpt = worldService.getByWorldId(worldId);
        if (worldOpt.isEmpty()) {
            return JobResult.failure("World not found: " + worldId);
        }

        var result = universeClientService.syncWorld(worldOpt.get());
        if (result.ok()) {
            log.info("Universe sync job completed for world '{}': {}", worldId, result.name());
            return JobResult.success("Synced: " + result.name());
        } else {
            log.warn("Universe sync job failed for world '{}': {}", worldId, result.error());
            return JobResult.failure(result.error());
        }
    }
}
