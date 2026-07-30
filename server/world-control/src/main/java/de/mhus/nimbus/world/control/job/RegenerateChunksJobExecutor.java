package de.mhus.nimbus.world.control.job;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.world.shared.job.JobExecutionException;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import de.mhus.nimbus.world.shared.job.WJob;
import de.mhus.nimbus.world.shared.layer.WDirtyChunkService;
import de.mhus.nimbus.world.shared.layer.WLayer;
import de.mhus.nimbus.world.shared.layer.WLayerService;
import de.mhus.nimbus.world.shared.world.WChunkService;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import de.mhus.nimbus.world.shared.world.WHexGridService;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldService;
import de.mhus.nimbus.shared.types.WorldId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Job executor for marking chunks as dirty to trigger regeneration.
 *
 * Supports three modes (exactly one must be specified):
 *
 * 1. Specific chunks:
 *    - chunks (required): Comma-separated chunk keys, e.g. "-1:1,0:1,0:0"
 *
 * 2. HexGrid:
 *    - hexQ (required): Hex grid Q coordinate
 *    - hexR (required): Hex grid R coordinate
 *
 * 3. Layer:
 *    - layerName (required): Layer name
 *    - recreateLayer (optional): "true" to recreate the layer terrain before marking dirty (default: "false")
 *      Only applicable for MODEL type layers.
 *
 * Common parameters:
 * - reason (optional): Reason for marking dirty (default: "mcp-regenerate-chunks")
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RegenerateChunksJobExecutor implements JobExecutor {

    private final WDirtyChunkService dirtyChunkService;
    private final WWorldService worldService;
    private final WHexGridService hexGridService;
    private final WLayerService layerService;
    private final WChunkService chunkService;

    @Override
    public String getExecutorName() {
        return "regenerate-chunks";
    }

    @Override
    public JobResult execute(WJob job) throws JobExecutionException {
        try {
            Map<String, String> params = job.getParameters();
            String worldId = job.getWorldId();
            String reason = params.getOrDefault("reason", "mcp-regenerate-chunks");

            var wid = WorldId.of(worldId).orElseThrow(
                    () -> new JobExecutionException("Invalid worldId: " + worldId)
            );

            String chunks = params.get("chunks");
            String hexQ = params.get("hexQ");
            String layerName = params.get("layerName");

            int modeCount = (chunks != null ? 1 : 0) + (hexQ != null ? 1 : 0) + (layerName != null ? 1 : 0);
            if (modeCount == 0) {
                throw new JobExecutionException("One of 'chunks', 'hexQ'+'hexR', or 'layerName' must be specified");
            }
            if (modeCount > 1) {
                throw new JobExecutionException("Only one of 'chunks', 'hexQ'+'hexR', or 'layerName' may be specified");
            }

            if (chunks != null) {
                return executeChunks(worldId, chunks, reason);
            } else if (hexQ != null) {
                return executeHexGrid(wid, worldId, params, reason);
            } else {
                return executeLayer(wid, worldId, layerName, params, reason);
            }

        } catch (JobExecutionException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to regenerate chunks", e);
            throw new JobExecutionException("Regeneration failed: " + e.getMessage(), e);
        }
    }

    private JobResult executeChunks(String worldId, String chunks, String reason) {
        List<String> chunkKeys = Arrays.stream(chunks.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());

        if (chunkKeys.isEmpty()) {
            throw new IllegalArgumentException("No valid chunk keys provided");
        }

        dirtyChunkService.markChunksDirty(worldId, chunkKeys, reason);

        String msg = String.format("Marked %d chunks dirty: %s", chunkKeys.size(), chunkKeys);
        log.info(msg);
        return JobResult.success(msg);
    }

    private JobResult executeHexGrid(WorldId wid, String worldId, Map<String, String> params, String reason) throws JobExecutionException {
        String hexR = params.get("hexR");
        if (hexR == null) {
            throw new JobExecutionException("'hexR' is required when 'hexQ' is specified");
        }

        int q = Integer.parseInt(params.get("hexQ"));
        int r = Integer.parseInt(hexR);

        WWorld world = worldService.getByWorldId(wid).orElseThrow(
                () -> new JobExecutionException("World not found: " + worldId)
        );

        HexVector2 hexPos = HexVector2.builder().q(q).r(r).build();
        WHexGrid hexGrid = hexGridService.findByWorldIdAndPosition(worldId, hexPos).orElseThrow(
                () -> new JobExecutionException("HexGrid not found at q=" + q + " r=" + r)
        );

        Set<String> affected = dirtyChunkService.markHexGridDirty(world, hexGrid, reason);

        String msg = String.format("Marked %d chunks dirty for hexGrid q=%d r=%d", affected.size(), q, r);
        log.info(msg);
        return JobResult.success(msg);
    }

    private JobResult executeLayer(WorldId wid, String worldId, String layerName, Map<String, String> params, String reason) throws JobExecutionException {
        boolean recreateLayer = parseBooleanParameter(params, "recreateLayer", false);

        WWorld world = worldService.getByWorldId(wid).orElseThrow(
                () -> new JobExecutionException("World not found: " + worldId)
        );

        Optional<WLayer> layerOpt = layerService.findLayer(worldId, layerName);
        if (layerOpt.isEmpty()) {
            throw new JobExecutionException("Layer not found: " + layerName);
        }
        WLayer layer = layerOpt.get();

        int recreatedChunks = 0;
        if (recreateLayer) {
            if (layer.getLayerType() == de.mhus.nimbus.world.shared.layer.LayerType.MODEL) {
                recreatedChunks = layerService.recreateModelBasedLayer(worldId, layer.getLayerDataId(), false);
                if (recreatedChunks < 0) {
                    return JobResult.failure("Failed to recreate MODEL layer: " + layerName);
                }
            } else {
                log.info("recreateLayer ignored for non-MODEL layer type: {}", layer.getLayerType());
            }
        }

        // Determine affected chunks and mark dirty
        List<String> chunkKeys;
        if (layer.isAllChunks()) {
            chunkKeys = chunkService.findChunksByWorldId(worldId).stream()
                    .map(c -> c.getChunk())
                    .collect(Collectors.toList());
            log.info("Layer '{}' affects all chunks, marking {} existing chunks dirty", layerName, chunkKeys.size());
        } else {
            chunkKeys = layer.getAffectedChunks();
        }

        if (chunkKeys != null && !chunkKeys.isEmpty()) {
            dirtyChunkService.markChunksDirty(worldId, chunkKeys, reason);
        }

        int dirtyCount = chunkKeys != null ? chunkKeys.size() : 0;
        String msg = recreateLayer
                ? String.format("Recreated layer '%s' (%d terrain chunks), marked %d chunks dirty", layerName, recreatedChunks, dirtyCount)
                : String.format("Marked %d chunks dirty for layer '%s'", dirtyCount, layerName);
        log.info(msg);
        return JobResult.success(msg);
    }

    private boolean parseBooleanParameter(Map<String, String> params, String key, boolean defaultValue) {
        String value = params.get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }
}
