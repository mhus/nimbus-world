package de.mhus.nimbus.world.control.job;

import de.mhus.nimbus.world.control.service.epoch.ResourceEpochService;
import de.mhus.nimbus.world.shared.job.JobExecutionException;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import de.mhus.nimbus.world.shared.job.WJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Job executor for epoch creation.
 * Propagates a new epoch to all resource types by adding it to documents
 * that contain the source epoch.
 *
 * Parameters:
 * - worldId: Provided by job.getWorldId()
 * - sourceEpoch: Epoch to copy from (job.getParameters().get("sourceEpoch"))
 * - newEpoch: New epoch number to add (job.getParameters().get("newEpoch"))
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EpochCreateJobExecutor implements JobExecutor {

    private final ResourceEpochService resourceEpochService;

    @Override
    public String getExecutorName() {
        return "epoch-create";
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

            String sourceEpochStr = params.get("sourceEpoch");
            String newEpochStr = params.get("newEpoch");
            if (sourceEpochStr == null || sourceEpochStr.isBlank()) {
                throw new JobExecutionException("Missing parameter 'sourceEpoch'");
            }
            if (newEpochStr == null || newEpochStr.isBlank()) {
                throw new JobExecutionException("Missing parameter 'newEpoch'");
            }

            int sourceEpoch;
            int newEpoch;
            try {
                sourceEpoch = Integer.parseInt(sourceEpochStr);
                newEpoch = Integer.parseInt(newEpochStr);
            } catch (NumberFormatException e) {
                throw new JobExecutionException(
                        "Invalid epoch parameters: sourceEpoch=" + sourceEpochStr + ", newEpoch=" + newEpochStr);
            }

            log.info("Starting epoch creation for world {}: sourceEpoch={}, newEpoch={}",
                    worldId, sourceEpoch, newEpoch);

            var results = resourceEpochService.create(worldId, sourceEpoch, newEpoch);

            StringBuilder report = new StringBuilder();
            report.append("Epoch creation for world ").append(worldId)
                    .append(" (source=").append(sourceEpoch)
                    .append(", new=").append(newEpoch).append("):\n");

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
            log.info("Epoch creation completed:\n{}", finalMessage);

            if (!allSuccess) {
                return JobResult.failure(finalMessage);
            }
            return JobResult.success(finalMessage);
        } catch (JobExecutionException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to execute epoch creation job", e);
            throw new JobExecutionException("Epoch creation failed: " + e.getMessage(), e);
        }
    }
}
