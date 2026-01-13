package de.mhus.nimbus.world.generator.flat;

import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.job.JobExecutionException;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import de.mhus.nimbus.world.shared.job.WJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Job executor for updating the border of an existing WFlat.
 * Reimports only the border (outer edge) cells from the layer.
 *
 * WorldId is taken from job.getWorldId()
 *
 * Required parameters:
 * - flatId: Identifier of the existing WFlat to update
 * - layerName: Name of the GROUND layer to import border from
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FlatUpdateBorderJobExecutor implements JobExecutor {

    private static final String EXECUTOR_NAME = "flat-update-border";

    private final FlatCreateService flatCreateService;

    @Override
    public String getExecutorName() {
        return EXECUTOR_NAME;
    }

    @Override
    public JobResult execute(WJob job) throws JobExecutionException {
        try {
            log.info("Starting flat update-border job: jobId={}", job.getId());

            // Get worldId from job
            String worldId = job.getWorldId();

            // Extract and validate required parameters
            String flatId = getRequiredParameter(job, "flatId");
            String layerName = getRequiredParameter(job, "layerName");

            log.info("Updating flat border: worldId={}, flatId={}, layerName={}", worldId, flatId, layerName);

            // Execute border update
            WFlat flat = flatCreateService.updateBorder(worldId, layerName, flatId);

            // Build success result
            String resultData = String.format(
                    "Successfully updated flat border: id=%s, flatId=%s, worldId=%s, layerName=%s, size=%dx%d",
                    flat.getId(), flatId, flat.getWorldId(), layerName, flat.getSizeX(), flat.getSizeZ()
            );

            log.info("Flat update-border completed successfully: flatId={}, id={}", flatId, flat.getId());
            return JobResult.ofSuccess(resultData);

        } catch (IllegalArgumentException e) {
            log.error("Invalid parameters for flat update-border", e);
            throw new JobExecutionException("Invalid parameters: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Flat update-border failed", e);
            throw new JobExecutionException("Update failed: " + e.getMessage(), e);
        }
    }

    /**
     * Get required string parameter from job.
     */
    private String getRequiredParameter(WJob job, String paramName) throws JobExecutionException {
        String value = job.getParameters().get(paramName);
        if (value == null || value.isBlank()) {
            throw new JobExecutionException("Missing required parameter: " + paramName);
        }
        return value;
    }
}
