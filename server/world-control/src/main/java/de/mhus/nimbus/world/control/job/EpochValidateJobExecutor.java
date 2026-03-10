package de.mhus.nimbus.world.control.job;

import de.mhus.nimbus.world.control.service.epoch.ResourceEpochService;
import de.mhus.nimbus.world.shared.job.JobExecutionException;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import de.mhus.nimbus.world.shared.job.WJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Job executor for epoch validation.
 * Checks epoch consistency across all resource types in a world.
 *
 * Parameters:
 * - worldId: Provided by job.getWorldId()
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EpochValidateJobExecutor implements JobExecutor {

    private final ResourceEpochService resourceEpochService;

    @Override
    public String getExecutorName() {
        return "epoch-validate";
    }

    @Override
    public JobResult execute(WJob job) throws JobExecutionException {
        try {
            String worldId = job.getWorldId();
            if (worldId == null || worldId.isBlank()) {
                throw new JobExecutionException("Missing worldId in job");
            }

            log.info("Starting epoch validation for world {}", worldId);

            var results = resourceEpochService.validate(worldId);

            StringBuilder report = new StringBuilder();
            report.append("Epoch validation for world ").append(worldId).append(":\n");

            boolean allSuccess = true;
            for (var r : results) {
                report.append("- ")
                        .append(r.typeName())
                        .append(": ")
                        .append(r.success() ? "OK" : "ISSUES")
                        .append(" - ")
                        .append(r.message())
                        .append("\n");
                if (!r.success()) {
                    allSuccess = false;
                }
            }

            String finalMessage = report.toString();
            log.info("Epoch validation completed:\n{}", finalMessage);

            if (!allSuccess) {
                return JobResult.failure(finalMessage);
            }
            return JobResult.success(finalMessage);
        } catch (JobExecutionException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to execute epoch validation job", e);
            throw new JobExecutionException("Epoch validation failed: " + e.getMessage(), e);
        }
    }
}
