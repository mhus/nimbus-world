package de.mhus.nimbus.world.generator.genesis;

import de.mhus.nimbus.generated.types.WorldInfo;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import de.mhus.nimbus.world.shared.layer.LayerType;
import de.mhus.nimbus.world.shared.layer.WLayerService;
import de.mhus.nimbus.world.shared.region.RRegionService;
import de.mhus.nimbus.world.shared.workflow.MethodBasedWorkflow;
import de.mhus.nimbus.world.shared.workflow.OnSuccess;
import de.mhus.nimbus.world.shared.workflow.WorkflowContext;
import de.mhus.nimbus.world.shared.workflow.WorkflowException;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 1. Create comosite structure
 * 2. Create World
 * 3. Create Layers
 * 4. Create Hex Grids with configuration
 * 5. Create Flats
 * 6. Export Flats into World
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class GenesisWorkflow extends MethodBasedWorkflow {

    public static final String DESCRIPTION = "description";
    public static final String WORLD_ID = "worldId";

    private final WWorldService worldService;
    private final RRegionService regionService;

    @Override
    public Map<String, String> initialize(String worldIdDoNotUse, Map<String, String> params) throws WorkflowException {
        // init parameters
        var worldId = params.get(WORLD_ID);
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
        var description = params.get(DESCRIPTION);
        if (Strings.isBlank(description)) {
            throw new WorkflowException(null, "Description is required");
        }
        var region = regionService.getByName(regionId).orElseThrow();
        if (!region.isEnabled()) {
            throw new WorkflowException(null, "Region is not enabled: " + regionId);
        }

        return Map.of(
                WORLD_ID, worldId,
                DESCRIPTION, description
        );
    }

    @Override
    public void start(WorkflowContext context) throws WorkflowException {
        // first step create composite structure from description
        context.updateWorkflowStatus("createComposite");
        context.enqueueJob("genesisCreateComposite", "", Map.of(
                WORLD_ID, context.getParameters().get(WORLD_ID),
                DESCRIPTION, context.getParameters().get(DESCRIPTION)
        ));
    }

    @OnSuccess("createComposite")
    public void onCreateCompositeSuccess(WorkflowContext context, Map<String, String> result) throws WorkflowException {
        String modelId = result.get(JobExecutor.PREVIOUS_JOB_RESULT);
        context.addRecord(new ModelId(modelId));
        // second step create world and layers
        context.updateWorkflowStatus("createWorld");
        context.enqueueJob("genesisCreateWorld", "", Map.of(
                WORLD_ID, context.getParameters().get(WORLD_ID)
        ));
    }

    @OnSuccess("createWorld")
    public void onCreateWorldSuccess(WorkflowContext context, Map<String, String> result) throws WorkflowException {
        // dings = JobExecutor.stringToMap()
        // create all flats in a job loop

    }

    @Override
    protected void onFailure(WorkflowContext context, String status, Map<String, String> data) {

    }

    @Override
    public void finalize(WorkflowContext context, String status) throws WorkflowException {

    }
}
