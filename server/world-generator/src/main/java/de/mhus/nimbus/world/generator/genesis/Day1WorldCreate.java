package de.mhus.nimbus.world.generator.genesis;

import de.mhus.nimbus.generated.types.Rotation;
import de.mhus.nimbus.generated.types.WorldInfo;
import de.mhus.nimbus.generated.types.WorldInfoEntryPointDTO;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.shared.utils.TypeUtil;
import de.mhus.nimbus.world.shared.layer.LayerType;
import de.mhus.nimbus.world.shared.layer.WLayerService;
import de.mhus.nimbus.world.shared.region.RRegionService;
import de.mhus.nimbus.world.shared.workflow.MethodBasedWorkflow;
import de.mhus.nimbus.world.shared.workflow.ResultRecord;
import de.mhus.nimbus.world.shared.workflow.StatusRecord;
import de.mhus.nimbus.world.shared.workflow.WorkflowContext;
import de.mhus.nimbus.world.shared.workflow.WorkflowException;
import de.mhus.nimbus.world.shared.world.WWorldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class Day1WorldCreate extends MethodBasedWorkflow  {

    private final WWorldService worldService;
    private final RRegionService regionService;
    private final WLayerService layerService;

    @Override
    public String name() {
        return "genesis-day1-world-create";
    }

    @Override
    public Map<String, Object> initialize(String worldIdDontUse, Map<String, String> params) throws WorkflowException {
        // init parameters
        var worldId = params.get(GenesisConst.WORLD_ID);
        if (Strings.isBlank(worldId)) {
            throw new WorkflowException(null, "WorldId is required");
        }
        if (worldService.existsWorld(worldId)) {
            throw new WorkflowException(null, "WorldId already exists: " + worldId);
        }
        var world = WorldId.of(worldId).orElseThrow();
        if (world.isCollection()) {
            throw new WorkflowException(null, "WorldId must not be a collection: " + worldId);
        }
        var regionId = world.getRegionId();
        var region = regionService.getByName(regionId).orElseThrow();
        if (!region.isEnabled()) {
            throw new WorkflowException(null, "Region is not enabled: " + regionId);
        }

        return Map.of(
                GenesisConst.WORLD_ID, worldId
        );
    }

    @Override
    public void start(WorkflowContext context) throws WorkflowException {

        // create the world
        var worldId = WorldId.of((String)context.getParameters().get(GenesisConst.WORLD_ID)).orElseThrow();
        worldService.createWorld(worldId,
                WorldInfo.builder()
                        .chunkSize(32)
                        .hexGridSize(400)
                        .start(TypeUtil.vector3(-10000d,0d,-10000d))
                        .stop(TypeUtil.vector3(10000d,300d,10000d))
                        .title("World " + worldId.getWorldName())
                        .entryPoint(
                                WorldInfoEntryPointDTO.builder()
                                        .area(TypeUtil.area(0,60,0,0,0,0))
                                        .rotation(Rotation.builder().y(0).build())
                                        .build()
                        )
                        .description("Genesis world")
                        .build()
        );
        // create layers
        var groundLayer = layerService.createLayer(
                worldId.getId(),
                "ground",
                LayerType.GROUND,
                100,
                true,
                List.of(),
                true
        );
        var floraLayer = layerService.createLayer(
                worldId.getId(),
                "flora",
                LayerType.GROUND,
                10,
                true,
                List.of(),
                false
        );
        var structuresLayer = layerService.createLayer(
                worldId.getId(),
                "structures",
                LayerType.MODEL,
                20,
                true,
                List.of(),
                false
        );

        context.addRecord(new ResultRecord(
                Map.of(
                        "worldId", worldId.getId(),
                        "groundLayerId", groundLayer.getLayerDataId(),
                        "floraLayerId", floraLayer.getLayerDataId(),
                        "structuresLayerId", structuresLayer.getLayerDataId()
                )
        ));

        context.updateWorkflowStatus(StatusRecord.COMPLETED);
    }

    @Override
    public void finalize(WorkflowContext context, String status) throws WorkflowException {

    }
}
