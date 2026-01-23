package de.mhus.nimbus.world.shared.workflow;

import de.mhus.nimbus.world.shared.job.JobExecutionException;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import de.mhus.nimbus.world.shared.job.WJob;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        value = "nimbus.services.workflows",
        havingValue = "true",
        matchIfMissing = false
)
public class WorkflowStartJobExecutor implements JobExecutor {

    public static final String NAME = "workflow-start-job-executor";

    private final WorkflowService workflowService;

    @Override
    public String getExecutorName() {
        return NAME;
    }

    @Override
    public JobResult execute(WJob job) throws JobExecutionException {
        var workflowName = job.getType();
        var workflowId = workflowService.createWorkflow(
                job.getWorldId(),
                workflowName,
                job.getParameters() != null ? job.getParameters() : Map.of()
        );
        return JobResult.ofSuccess(workflowId);
    }

}
