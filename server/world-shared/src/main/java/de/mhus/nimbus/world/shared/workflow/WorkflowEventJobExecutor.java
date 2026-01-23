package de.mhus.nimbus.world.shared.workflow;

import de.mhus.nimbus.world.shared.job.JobExecutionException;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import de.mhus.nimbus.world.shared.job.WJob;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WorkflowEventJobExecutor implements JobExecutor {

    public static final String NAME = "workflow-event-job-executor";

    private final WorkflowService workflowService;

    @Override
    public String getExecutorName() {
        return NAME;
    }

    @Override
    public JobResult execute(WJob job) throws JobExecutionException {

        var parts = job.getType().split(":");
        var event = parts[0];
        var workflowName = parts[1];
        var workflowId = parts[2];

        String status = null;
        if (event.equals(WorkflowEvent.START)) {
            // Start workflow
            status = workflowService.startWorkflow(
                    job.getWorldId(),
                    workflowName,
                    workflowId
            );
        } else {
            status = workflowService.processEvent(
                    job.getWorldId(),
                    workflowName,
                    workflowId,
                    WorkflowEvent.builder()
                            .event(event)
                            .data(job.getParameters() != null ? job.getParameters() : Map.of())
                            .build()
            );
        }
        if (status == null || WorkflowStatus.FAILED.equals(status))
            return JobResult.ofFailure(status);
        return JobResult.ofSuccess(status);
    }

}
