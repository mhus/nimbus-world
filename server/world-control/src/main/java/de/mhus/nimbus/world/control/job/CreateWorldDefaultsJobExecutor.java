package de.mhus.nimbus.world.control.job;

import de.mhus.nimbus.generated.types.Block;
import de.mhus.nimbus.generated.types.HexGrid;
import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.generated.types.Vector3;
import de.mhus.nimbus.generated.types.Vector3Int;
import de.mhus.nimbus.generated.types.WorldInfo;
import de.mhus.nimbus.world.shared.job.JobExecutionException;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import de.mhus.nimbus.world.shared.job.WJob;
import de.mhus.nimbus.world.shared.layer.LayerBlock;
import de.mhus.nimbus.world.shared.layer.LayerChunkData;
import de.mhus.nimbus.world.shared.layer.LayerType;
import de.mhus.nimbus.world.shared.layer.WDirtyChunkService;
import de.mhus.nimbus.world.shared.layer.WLayer;
import de.mhus.nimbus.world.shared.layer.WLayerService;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import de.mhus.nimbus.world.shared.world.WHexGridRepository;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Job executor for creating default world entities.
 *
 * Creates initial world data:
 * - 3 default layers (ground, terrain, flora)
 * - Default hex grid at position 0:0
 * - Initial ground chunk at 0:0 with blocks at height 65
 * - Sets default entry point to 10,67,10
 *
 * Parameters:
 * - worldId (required): World ID to initialize
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CreateWorldDefaultsJobExecutor implements JobExecutor {

    private final WLayerService layerService;
    private final WHexGridRepository hexGridRepository;
    private final WWorldService worldService;
    private final WDirtyChunkService dirtyChunkService;

    @Override
    public String getExecutorName() {
        return "create-world-defaults";
    }

    @Override
    public JobResult execute(WJob job) throws JobExecutionException {
        try {
            // Parse parameters
            Map<String, String> params = job.getParameters();

            String worldId = params.get("worldId");
            if (worldId == null || worldId.isBlank()) {
                throw new JobExecutionException("Missing required parameter: worldId");
            }

            log.info("Creating default entities for world: {}", worldId);

            StringBuilder resultMessage = new StringBuilder();
            resultMessage.append("Created default entities for world ").append(worldId).append(":\n");

            // 1. Create default layers
            log.info("Creating default layers for world {}", worldId);
            try {
                // Layer 1: ground (order 10, baseGround=true)
                WLayer groundLayer = layerService.createLayer(worldId, "ground", LayerType.GROUND, 10, true, null, true);
                resultMessage.append("- Layer 'ground' created\n");
                log.debug("Created default layer 'ground' for world {}", worldId);

                // Layer 2: terrain (order 20, baseGround=true)
                layerService.createLayer(worldId, "terrain", LayerType.GROUND, 20, true, null, true);
                resultMessage.append("- Layer 'terrain' created\n");
                log.debug("Created default layer 'terrain' for world {}", worldId);

                // Layer 3: flora (order 30, baseGround=false)
                layerService.createLayer(worldId, "flora", LayerType.GROUND, 30, true, null, false);
                resultMessage.append("- Layer 'flora' created\n");
                log.debug("Created default layer 'flora' for world {}", worldId);

                // 2. Create default hex grid at 0:0
                log.info("Creating default hex grid at 0:0 for world {}", worldId);
                HexVector2 hexPosition = HexVector2.builder()
                        .q(0)
                        .r(0)
                        .build();

                HexGrid hexGridData = HexGrid.builder()
                        .position(hexPosition)
                        .name("Spawn Area")
                        .description("Default spawn hex grid")
                        .build();

                WHexGrid hexGrid = WHexGrid.builder()
                        .worldId(worldId)
                        .publicData(hexGridData)
                        .enabled(true)
                        .build();

                hexGrid.syncPositionKey();
                hexGrid.touchCreate();
                hexGridRepository.save(hexGrid);
                resultMessage.append("- Hex grid at 0:0 created\n");
                log.debug("Created default hex grid at 0:0 for world {}", worldId);

                // 3. Create initial ground chunk at 0:0 with a plateau from 0,0 to 31,31 at height 65
                log.info("Creating initial ground chunk at 0:0 for world {}", worldId);
                String chunkKey = "0:0";
                List<LayerBlock> blocks = new ArrayList<>();

                // Create a 32x32 flat ground plateau at height 65 with block type "n:g"
                // Plateau covers coordinates from 0,0 to 31,31 (chunk size)
                for (int x = 0; x < 32; x++) {
                    for (int z = 0; z < 32; z++) {
                        Block block = Block.builder()
                                .position(Vector3Int.builder()
                                        .x(x)
                                        .y(65)
                                        .z(z)
                                        .build())
                                .blockTypeId("n:g")
                                .build();

                        LayerBlock layerBlock = LayerBlock.builder()
                                .block(block)
                                .group(0)
                                .build();

                        blocks.add(layerBlock);
                    }
                }

                LayerChunkData chunkData = LayerChunkData.builder()
                        .cx(0)
                        .cz(0)
                        .blocks(blocks)
                        .build();

                layerService.saveTerrainChunk(worldId, groundLayer.getLayerDataId(), chunkKey, chunkData);
                resultMessage.append("- Initial ground plateau at 0:0 created (32x32 = 1024 blocks at height 65)\n");
                log.debug("Created initial ground plateau at 0:0 with {} blocks for world {}", blocks.size(), worldId);

                // Mark chunk 0:0 as dirty for regeneration
                dirtyChunkService.markChunkDirty(worldId, chunkKey, "initial_world_setup");
                resultMessage.append("- Chunk 0:0 marked as dirty for regeneration\n");
                log.debug("Marked chunk 0:0 as dirty for world {}", worldId);

                // 4. Set default entry point to 10,67,10
                log.info("Setting default entry point to 10,67,10 for world {}", worldId);
                worldService.updateWorld(de.mhus.nimbus.shared.types.WorldId.of(worldId).orElseThrow(), world -> {
                    WorldInfo publicData = world.getPublicData();
                    if (publicData == null) {
                        publicData = new WorldInfo();
                        world.setPublicData(publicData);
                    }

                    Vector3 entryPoint = Vector3.builder()
                            .x(10.0f)
                            .y(67.0f)
                            .z(10.0f)
                            .build();

                    publicData.setStart(entryPoint);
                });
                resultMessage.append("- Default entry point set to 10,67,10\n");
                log.debug("Set default entry point to 10,67,10 for world {}", worldId);

                String finalMessage = resultMessage.toString();
                log.info("Successfully created default entities for world {}:\n{}", worldId, finalMessage);

                return JobResult.ofSuccess(finalMessage);

            } catch (Exception e) {
                String errorMsg = String.format("Failed to create default entities: %s", e.getMessage());
                log.error(errorMsg, e);
                throw new JobExecutionException(errorMsg, e);
            }

        } catch (JobExecutionException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to execute create-world-defaults job", e);
            throw new JobExecutionException("Failed to create world defaults: " + e.getMessage(), e);
        }
    }
}
