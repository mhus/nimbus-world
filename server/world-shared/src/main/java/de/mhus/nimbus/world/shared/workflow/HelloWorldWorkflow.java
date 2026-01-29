package de.mhus.nimbus.world.shared.workflow;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@ConditionalOnProperty(
        value = "nimbus.services.workflows",
        havingValue = "true",
        matchIfMissing = false
)
public class HelloWorldWorkflow extends MethodBasedWorkflow {

    @Override
    public Map<String, String> initialize(String worldId, Map<String, String> params) throws WorkflowException {
        return params;
    }

    @Override
    public void start(WorkflowContext context) throws WorkflowException {
        context.addRecord(new NoteRecord("Workflow started"));
        context.updateWorkflowStatus(StatusRecord.COMPLETED);
    }

    @Override
    public void finalize(WorkflowContext context, String status) throws WorkflowException {
        context.addRecord(new NoteRecord("Workflow finalized with status: " + status));
    }
}
