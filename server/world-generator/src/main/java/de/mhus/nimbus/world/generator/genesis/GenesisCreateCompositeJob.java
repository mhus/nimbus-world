package de.mhus.nimbus.world.generator.genesis;

import de.mhus.nimbus.world.shared.job.JobExecutionException;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import de.mhus.nimbus.world.shared.job.WJob;

public class GenesisCreateCompositeJob implements JobExecutor {

    @Override
    public String getExecutorName() {
        return "genesisCreateComposite";
    }

    @Override
    public JobResult execute(WJob job) throws JobExecutionException {
        String worldId = job.getWorldId();
        String description = job.getParameters().get(GenesisConst.DESCRIPTION);
        // TODO
        // to JSON schema via langchain4j
        // to Model via HexComposer
        // if error back to langchain4j for error correction
        // save to WAnything for worldId, return WAnything id
        // max trys 3?
        return null;
    }
}
