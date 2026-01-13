package de.mhus.nimbus.world.generator.flat;

import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.generator.WFlatService;
import de.mhus.nimbus.world.shared.job.JobExecutionException;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import de.mhus.nimbus.world.shared.job.WJob;
import de.mhus.nimbus.world.shared.layer.WLayer;
import de.mhus.nimbus.world.shared.layer.WLayerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Job executor for exporting WFlat to WLayer GROUND type.
 * Writes flat terrain data back to layer chunks.
 *
 * WorldId is taken from job.getWorldId()
 *
 * Required parameters:
 * - flatId: Database ID of the WFlat to export
 *
 * Optional parameters:
 * - layerName: Name of the target GROUND layer (if not specified, uses the layer from which the flat was imported)
 * - deleteAfterExport: If true, deletes the WFlat after successful export (default: false)
 * - smoothCorners: If true, smooths corners of top GROUND blocks based on neighbor heights (default: true)
 * - optimizeFaces: If true, sets faceVisibility to hide non-visible block faces (default: true)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FlatExportJobExecutor implements JobExecutor {

    private static final String EXECUTOR_NAME = "flat-export";

    private final FlatExportService flatExportService;
    private final WFlatService flatService;
    private final WLayerService layerService;

    @Override
    public String getExecutorName() {
        return EXECUTOR_NAME;
    }

    @Override
    public JobResult execute(WJob job) throws JobExecutionException {
        try {
            log.info("Starting flat export job: jobId={}", job.getId());

            // Get worldId from job
            String worldId = job.getWorldId();

            // Extract and validate required parameters
            String flatId = getRequiredParameter(job, "flatId");

            // Extract optional layerDataId for compound key lookup
            String layerDataId = getOptionalParameter(job, "layerDataId", null);

            // Load flat using compound key or search
            WFlat flat = loadFlat(job, flatId);

            // Extract optional layerName (if not specified, use the layer from which flat was imported)
            String layerName = getOptionalParameter(job, "layerName", null);
            if (layerName == null || layerName.isBlank()) {
                // Use layer from flat's layerDataId
                String flatLayerDataId = flat.getLayerDataId();
                if (flatLayerDataId == null || flatLayerDataId.isBlank()) {
                    throw new JobExecutionException("No layerName specified and flat has no layerDataId");
                }

                // Find layer by layerDataId
                WLayer layer = layerService.findByWorldIdAndLayerDataId(worldId, flatLayerDataId)
                        .orElseThrow(() -> new JobExecutionException("Layer not found for layerDataId: " + flatLayerDataId));
                layerName = layer.getName();

                log.info("Using flat's original layer: layerDataId={}, layerName={}", flatLayerDataId, layerName);
            }

            // Extract optional parameters
            boolean deleteAfterExport = getOptionalBooleanParameter(job, "deleteAfterExport", false);
            boolean smoothCorners = getOptionalBooleanParameter(job, "smoothCorners", true);
            boolean optimizeFaces = getOptionalBooleanParameter(job, "optimizeFaces", true);

            log.info("Exporting flat: flatId={}, worldId={}, layerName={}, deleteAfterExport={}, smoothCorners={}, optimizeFaces={}",
                    flatId, worldId, layerName, deleteAfterExport, smoothCorners, optimizeFaces);

            // Execute export (use database ID)
            int exportedColumns = flatExportService.exportToLayer(flat.getId(), worldId, layerName, smoothCorners, optimizeFaces);

            // Delete flat if requested
            if (deleteAfterExport) {
                log.info("Deleting flat after export: flatId={}", flatId);
                flatService.deleteById(flat.getId());
                log.info("Flat deleted: flatId={}", flatId);
            }

            // Build success result
            String resultData = String.format(
                    "Successfully exported flat: flatId=%s, worldId=%s, layerName=%s, exportedColumns=%d, deleted=%s, smoothCorners=%s, optimizeFaces=%s",
                    flatId, worldId, layerName, exportedColumns, deleteAfterExport, smoothCorners, optimizeFaces
            );

            log.info("Flat export completed successfully: flatId={}, exportedColumns={}, deleted={}, smoothCorners={}, optimizeFaces={}",
                    flatId, exportedColumns, deleteAfterExport, smoothCorners, optimizeFaces);
            return JobResult.ofSuccess(resultData);

        } catch (JobExecutionException e) {
            log.error("Flat export job failed: {}", e.getMessage());
            throw e;
        } catch (IllegalArgumentException e) {
            log.error("Invalid parameters for flat export", e);
            throw new JobExecutionException("Invalid parameters: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Flat export failed", e);
            throw new JobExecutionException("Export failed: " + e.getMessage(), e);
        }
    }

    /**
     * Load WFlat by flatId with worldId from job.
     * Optionally uses layerDataId if provided.
     */
    private WFlat loadFlat(WJob job, String flatId) throws JobExecutionException {
        String worldId = job.getWorldId();
        String layerDataId = getOptionalParameter(job, "layerDataId", null);

        if (layerDataId != null && !layerDataId.isBlank()) {
            // Use compound lookup with worldId, layerDataId, and flatId
            return flatService.findByWorldIdAndLayerDataIdAndFlatId(worldId, layerDataId, flatId)
                    .orElseThrow(() -> new JobExecutionException("Flat not found: worldId=" + worldId +
                            ", layerDataId=" + layerDataId + ", flatId=" + flatId));
        } else {
            // Search for flat with matching flatId in this world
            return flatService.findByWorldId(worldId).stream()
                    .filter(f -> flatId.equals(f.getFlatId()))
                    .findFirst()
                    .orElseThrow(() -> new JobExecutionException("Flat not found: worldId=" + worldId +
                            ", flatId=" + flatId));
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

    /**
     * Get optional string parameter from job with default value.
     */
    private String getOptionalParameter(WJob job, String paramName, String defaultValue) {
        String value = job.getParameters().get(paramName);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }

    /**
     * Get optional boolean parameter from job with default value.
     */
    private boolean getOptionalBooleanParameter(WJob job, String paramName, boolean defaultValue) {
        String value = job.getParameters().get(paramName);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        // Parse boolean (true, false, 1, 0, yes, no)
        return "true".equalsIgnoreCase(value)
            || "1".equals(value)
            || "yes".equalsIgnoreCase(value);
    }
}
