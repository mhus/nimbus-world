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
 * Job executor for transferring a single WLayerModel into WLayerTerrain storage.
 *
 * This job:
 * - Calculates affected chunks from model content
 * - Transfers model blocks to WLayerTerrain for each chunk
 * - Updates WLayer.affectedChunks if needed
 * - Optionally marks chunks as dirty
 *
 * Parameters:
 * - modelId (required): ID of the WLayerModel to transfer
 * - markChunksDirty (optional): "true" or "false" (default: "true")
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TransferModelToTerrainJobExecutor implements JobExecutor {

    private final WLayerService layerService;

    @Override
    public String getExecutorName() {
        return "transfer-model-to-terrain";
    }

    @Override
    public JobResult execute(WJob job) throws JobExecutionException {
        try {
            // Parse parameters
            Map<String, String> params = job.getParameters();

            String modelId = params.get("modelId");
            if (modelId == null || modelId.isBlank()) {
                throw new JobExecutionException("Missing required parameter: modelId");
            }

            boolean markChunksDirty = parseBooleanParameter(params, "markChunksDirty", true);

            log.info("Starting model transfer: modelId={} markChunksDirty={}", modelId, markChunksDirty);

            // Execute transfer
            int chunksProcessed = layerService.transferModelToTerrain(modelId, markChunksDirty);

            if (chunksProcessed < 0) {
                return JobResult.ofFailure("Model not found: " + modelId);
            }

            String resultMessage = String.format("Transferred model to terrain: modelId=%s chunks=%d",
                    modelId, chunksProcessed);

            log.info(resultMessage);
            return JobResult.ofSuccess(resultMessage);

        } catch (Exception e) {
            log.error("Failed to transfer model to terrain", e);
            throw new JobExecutionException("Transfer failed: " + e.getMessage(), e);
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
