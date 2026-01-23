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
public class WorkflowEventJobExecutor implements JobExecutor {

    public static final String NAME = "workflow-event-job-executor";

    private final WorkflowService workflowService;
    private final WWorkflowJournalService journalService;

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
            if (!workflowService.existsWorkflow(job.getWorldId(), workflowId)) {
                throw new JobExecutionException("Workflow does not exist: " + workflowId);
            }
            var eventObject = WorkflowEvent.builder()
                    .event(event)
                    .data(job.getParameters() != null ? job.getParameters() : Map.of())
                    .build();
            journalService.addWorkflowJournalRecord(job.getWorldId(), workflowId, eventObject);
            status = workflowService.processEvent(
                    job.getWorldId(),
                    workflowName,
                    workflowId,
                    eventObject
            );
        }
        if (status == null || StatusRecord.FAILED.equals(status))
            return JobResult.ofFailure(status);
        return JobResult.ofSuccess(status);
    }

}
