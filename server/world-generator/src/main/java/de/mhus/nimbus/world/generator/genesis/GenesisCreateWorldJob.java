package de.mhus.nimbus.world.generator.genesis;

import de.mhus.nimbus.generated.types.WorldInfo;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.job.JobExecutionException;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import de.mhus.nimbus.world.shared.job.WJob;
import de.mhus.nimbus.world.shared.layer.LayerType;
import de.mhus.nimbus.world.shared.layer.WLayerService;
import de.mhus.nimbus.world.shared.world.WWorldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class GenesisCreateWorldJob implements JobExecutor {

    private final WWorldService worldService;
    private final WLayerService layerService;

    @Override
    public String getExecutorName() {
        return "genesisCreateWorld";
    }

    @Override
    public JobResult execute(WJob job) throws JobExecutionException {

        // create the world
        var worldId = WorldId.of(job.getParameters().get(GenesisWorkflow.WORLD_ID)).orElseThrow();
        worldService.createWorld(worldId,
                WorldInfo.builder()
                        .chunkSize(32)
                        .hexGridSize(400)
                        .description("Genesis world")
                        .build()
        );
        // create layers
        var groundLayer = layerService.createLayer(
                worldId.getId(),
                "ground",
                LayerType.GROUND,
                10,
                true,
                List.of(),
                true
        );
        var floraLayer = layerService.createLayer(
                worldId.getId(),
                "flora",
                LayerType.GROUND,
                20,
                true,
                List.of(),
                false
        );

        return JobResult.success(Map.of(
                "worldId", worldId.getId(),
                "groundLayerId", groundLayer.getLayerDataId(),
                "floraLayerId", floraLayer.getLayerDataId()
        ));
    }
}
