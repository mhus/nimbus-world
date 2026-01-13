package de.mhus.nimbus.world.control.job;

import de.mhus.nimbus.world.shared.job.JobExecutionException;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import de.mhus.nimbus.world.shared.job.WJob;
import de.mhus.nimbus.world.shared.layer.WLayerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Job executor for recreating a complete MODEL-based layer from all WLayerModel documents.
 *
 * This job:
 * - Deletes all existing WLayerTerrain chunks for the layer
 * - Recalculates affected chunks from all WLayerModel documents
 * - Regenerates WLayerTerrain for each affected chunk
 * - Updates WLayer.affectedChunks completely (replaces, not merges)
 * - Optionally marks chunks as dirty
 *
 * Use cases:
 * - After changing model order
 * - After deleting a model
 * - After major changes to multiple models
 * - To fix inconsistent terrain data
 *
 * Parameters:
 * - layerDataId (required): Layer data ID to recreate
 * - markChunksDirty (optional): "true" or "false" (default: "true")
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RecreateModelBasedLayerJobExecutor implements JobExecutor {

    private final WLayerService layerService;

    @Override
    public String getExecutorName() {
        return "recreate-model-based-layer";
    }

    @Override
    public JobResult execute(WJob job) throws JobExecutionException {
        try {
            // Parse parameters
            Map<String, String> params = job.getParameters();

            String layerDataId = params.get("layerDataId");
            if (layerDataId == null || layerDataId.isBlank()) {
                throw new JobExecutionException("Missing required parameter: layerDataId");
            }

            boolean markChunksDirty = parseBooleanParameter(params, "markChunksDirty", true);

            log.info("Starting model-based layer recreation: layerDataId={} markChunksDirty={}",
                    layerDataId, markChunksDirty);

            // Execute recreation
            int chunksProcessed = layerService.recreateModelBasedLayer(layerDataId, markChunksDirty);

            if (chunksProcessed < 0) {
                return JobResult.ofFailure("Layer not found or not a MODEL layer: " + layerDataId);
            }

            String resultMessage = String.format(
                    "Recreated model-based layer: layerDataId=%s chunks=%d",
                    layerDataId, chunksProcessed);

            log.info(resultMessage);
            return JobResult.ofSuccess(resultMessage);

        } catch (Exception e) {
            log.error("Failed to recreate model-based layer", e);
            throw new JobExecutionException("Recreation failed: " + e.getMessage(), e);
        }
    }

    private boolean parseBooleanParameter(Map<String, String> params, String key, boolean defaultValue) {
        String value = params.get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }
}
