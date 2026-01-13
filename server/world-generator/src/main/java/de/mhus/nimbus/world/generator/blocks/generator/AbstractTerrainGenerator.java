package de.mhus.nimbus.world.generator.blocks.generator;

import de.mhus.nimbus.generated.types.Block;
import de.mhus.nimbus.generated.types.RotationXY;
import de.mhus.nimbus.generated.types.Vector3Int;
import de.mhus.nimbus.world.shared.job.JobExecutionException;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import de.mhus.nimbus.world.shared.job.WJob;
import de.mhus.nimbus.world.shared.layer.LayerBlock;
import de.mhus.nimbus.world.shared.layer.LayerChunkData;
import de.mhus.nimbus.world.shared.layer.WDirtyChunkService;
import de.mhus.nimbus.world.shared.layer.WLayer;
import de.mhus.nimbus.world.shared.layer.WLayerService;
import de.mhus.nimbus.world.shared.world.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;

/**
 * Abstract base class for terrain generators.
 * Implements JobExecutor pattern for async generation with Template Method design.
 */
@Slf4j
public abstract class AbstractTerrainGenerator implements JobExecutor {

    @Autowired
    protected WWorldService worldService;

    @Autowired
    protected WHexGridService hexGridService;

    @Autowired
    protected WLayerService layerService;

    @Autowired
    protected WDirtyChunkService dirtyChunkService;

    protected record GeneratorContext(
            String worldId,
            String gridPosition,
            String layerName,
            String layerDataId
    ) {}

    @Override
    public final JobResult execute(WJob job) throws JobExecutionException {
        try {
            GeneratorContext context = parseParameters(job);

            WWorld world = loadWorld(context.worldId());
            WHexGrid hexGrid = loadHexGrid(context.worldId(), context.gridPosition());

            configureGenerator(hexGrid.getGeneratorParameters());

            int blockCount = generateTerrain(world, hexGrid, context);

            log.info("Generator {} completed: world={} grid={} blocks={}",
                    getExecutorName(), context.worldId(), context.gridPosition(), blockCount);

            return JobResult.ofSuccess("Generated " + blockCount + " blocks");

        } catch (Exception e) {
            log.error("Generator {} failed", getExecutorName(), e);
            throw new JobExecutionException("Generation failed: " + e.getMessage(), e);
        }
    }

    protected abstract void configureGenerator(Map<String, String> genParams);

    protected abstract int generateTerrain(WWorld world, WHexGrid hexGrid, GeneratorContext context)
            throws JobExecutionException;

    protected abstract int getTerrainHeight(int worldX, int worldZ);

    protected abstract List<Float> calculateOffsets(int x, int y, int z, int[][] heightMap, int centerHeight);

    protected GeneratorContext parseParameters(WJob job) throws JobExecutionException {
        Map<String, String> params = job.getParameters();

        String grid = params.get("grid");
        if (grid == null || grid.isBlank()) {
            throw new JobExecutionException("Missing required parameter: grid");
        }

        String layerName = params.get("layer");
        if (layerName == null || layerName.isBlank()) {
            throw new JobExecutionException("Missing required parameter: layer");
        }

        Optional<WLayer> layer = layerService.findLayer(job.getWorldId(), layerName);
        if (layer.isEmpty()) {
            throw new JobExecutionException("Layer not found: " + layerName);
        }

        String layerDataId = layer.get().getLayerDataId();
        if (layerDataId == null) {
            throw new JobExecutionException("Layer has no layerDataId: " + layerName);
        }

        return new GeneratorContext(job.getWorldId(), grid, layerName, layerDataId);
    }

    protected WWorld loadWorld(String worldId) throws JobExecutionException {
        return worldService.getByWorldId(worldId)
                .orElseThrow(() -> new JobExecutionException("World not found: " + worldId));
    }

    protected WHexGrid loadHexGrid(String worldId, String gridPosition) throws JobExecutionException {
        try {
            de.mhus.nimbus.generated.types.HexVector2 hexPos = HexMathUtil.parsePositionKey(gridPosition);
            return hexGridService.findByWorldIdAndPosition(worldId, hexPos)
                    .orElseThrow(() -> new JobExecutionException(
                            "HexGrid not found: world=" + worldId + " position=" + gridPosition));
        } catch (IllegalArgumentException e) {
            throw new JobExecutionException("Invalid grid position: " + gridPosition, e);
        }
    }

    protected String getParameter(Map<String, String> params, String key, String defaultValue) {
        return params.getOrDefault(key, defaultValue);
    }

    protected int getIntParameter(Map<String, String> params, String key, int defaultValue) {
        String value = params.get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid int parameter {}: {}, using default {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    protected long getLongParameter(Map<String, String> params, String key, long defaultValue) {
        String value = params.get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid long parameter {}: {}, using default {}", key, value, defaultValue);
            return defaultValue;
        }
    }

    protected void saveChunk(String worldId, String layerDataId, String chunkKey, List<LayerBlock> blocks) {
        String[] parts = chunkKey.split(":");
        int cx = Integer.parseInt(parts[0]);
        int cz = Integer.parseInt(parts[1]);

        LayerChunkData chunkData = LayerChunkData.builder()
                .cx(cx)
                .cz(cz)
                .blocks(blocks)
                .build();

        layerService.saveTerrainChunk(worldId, layerDataId, chunkKey, chunkData);
        dirtyChunkService.markChunkDirty(worldId, chunkKey, getExecutorName());

        log.debug("Saved chunk: world={} chunk={} blocks={}", worldId, chunkKey, blocks.size());
    }

    protected Block createBlock(int x, int y, int z, String blockTypeId) {
        return Block.builder()
                .blockTypeId(blockTypeId)
                .position(Vector3Int.builder().x(x).y(y).z(z).build())
                .rotation(RotationXY.builder().x(0).y(0).build())
                .build();
    }

    protected LayerBlock createLayerBlock(Block block) {
        return LayerBlock.builder()
                .block(block)
                .group(0)
                .build();
    }

    protected float clamp(float value) {
        return Math.max(-1.0f, Math.min(1.0f, value));
    }
}
