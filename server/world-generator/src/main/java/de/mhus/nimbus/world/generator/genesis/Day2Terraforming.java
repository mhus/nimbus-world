package de.mhus.nimbus.world.generator.genesis;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import de.mhus.nimbus.world.shared.region.RRegionService;
import de.mhus.nimbus.world.shared.workflow.MethodBasedWorkflow;
import de.mhus.nimbus.world.shared.workflow.OnSuccess;
import de.mhus.nimbus.world.shared.workflow.StatusRecord;
import de.mhus.nimbus.world.shared.workflow.WorkflowContext;
import de.mhus.nimbus.world.shared.workflow.WorkflowException;
import de.mhus.nimbus.world.shared.world.WWorldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;

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
public class Day2Terraforming extends MethodBasedWorkflow {

    private final WWorldService worldService;
    private final RRegionService regionService;

    @Override
    public Map<String, String> initialize(String worldId, Map<String, String> params) throws WorkflowException {

        var description = params.get(GenesisConst.DESCRIPTION);
        if (Strings.isBlank(description)) {
            throw new WorkflowException(null, "Description is required");
        }

        return Map.of(
                GenesisConst.DESCRIPTION, description
        );
    }

    @Override
    public void start(WorkflowContext context) throws WorkflowException {
        // first step create composite structure from description
        context.updateWorkflowStatus("createComposite");
        context.enqueueJob("genesisCreateComposite", "", Map.of(
                GenesisConst.DESCRIPTION, context.getParameters().get(GenesisConst.DESCRIPTION)
        ));
    }

    @OnSuccess("createComposite")
    public void onCreateCompositeSuccess(WorkflowContext context, Map<String, String> result) throws WorkflowException {
        // TODO create all flats, export all flats and flat images
        // generate and archive composite images
        context.updateWorkflowStatus(StatusRecord.COMPLETED);
    }

    @Override
    public void finalize(WorkflowContext context, String status) throws WorkflowException {
    }
}
