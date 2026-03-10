package de.mhus.nimbus.world.control.job;

import de.mhus.nimbus.world.control.service.epoch.ResourceEpochService;
import de.mhus.nimbus.world.shared.job.JobExecutionException;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import de.mhus.nimbus.world.shared.job.WJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Job executor for epoch deletion.
 * Removes an epoch from all resource documents (chunks, layers, entities, items, hexgrids).
 *
 * Parameters:
 * - worldId: Provided by job.getWorldId()
 * - epoch: Epoch number to remove (job.getParameters().get("epoch"))
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EpochDeleteJobExecutor implements JobExecutor {

    private final ResourceEpochService resourceEpochService;

    @Override
    public String getExecutorName() {
        return "epoch-delete";
    }

    @Override
    public JobResult execute(WJob job) throws JobExecutionException {
        try {
            String worldId = job.getWorldId();
            if (worldId == null || worldId.isBlank()) {
                throw new JobExecutionException("Missing worldId in job");
            }

            var params = job.getParameters();
            if (params == null) {
                throw new JobExecutionException("Missing parameters in job");
            }

            String epochStr = params.get("epoch");
            if (epochStr == null || epochStr.isBlank()) {
                throw new JobExecutionException("Missing parameter 'epoch'");
            }

            int epoch;
            try {
                epoch = Integer.parseInt(epochStr);
            } catch (NumberFormatException e) {
                throw new JobExecutionException("Invalid epoch parameter: " + epochStr);
            }

            log.info("Starting epoch deletion for world {}: epoch={}", worldId, epoch);

            var results = resourceEpochService.delete(worldId, epoch);

            StringBuilder report = new StringBuilder();
            report.append("Epoch deletion for world ").append(worldId)
                    .append(" (epoch=").append(epoch).append("):\n");

            boolean allSuccess = true;
            for (var r : results) {
                report.append("- ")
                        .append(r.typeName())
                        .append(": ")
                        .append(r.success() ? "OK" : "FAILED")
                        .append(" - ")
                        .append(r.message())
                        .append("\n");
                if (!r.success()) {
                    allSuccess = false;
                }
            }

            String finalMessage = report.toString();
            log.info("Epoch deletion completed:\n{}", finalMessage);

            if (!allSuccess) {
                return JobResult.failure(finalMessage);
            }
            return JobResult.success(finalMessage);
        } catch (JobExecutionException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to execute epoch deletion job", e);
            throw new JobExecutionException("Epoch deletion failed: " + e.getMessage(), e);
        }
    }
}
