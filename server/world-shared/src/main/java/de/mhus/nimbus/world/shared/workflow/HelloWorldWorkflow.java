package de.mhus.nimbus.world.shared.workflow;

import de.mhus.nimbus.shared.utils.CastUtil;
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
    public String name() {
        return "hello-world";
    }

    @Override
    public Map<String, Object> initialize(String worldId, Map<String, String> params) throws WorkflowException {
        String greeting = params.get("greeting");
        if (greeting == null || greeting.isBlank()) {
            throw new WorkflowException(null, "Parameter 'greeting' is required");
        }
        int sleep = CastUtil.toint(params.get("sleep"), 0);
        return Map.of(
                "greeting", greeting,
                "sleep", sleep
        );
    }

    @Override
    public void start(WorkflowContext context) throws WorkflowException {
        context.addRecord(new NoteRecord("Workflow started"));
        try {
            int sleep = (int)context.getParameters().get("sleep");
            if (sleep > 0) {
                Thread.sleep(sleep);
            }
        } catch (InterruptedException e) {
            throw new WorkflowException(context, "Workflow interrupted", e);
        }
        context.doComplete((String)context.getParameters().get("greeting"));
    }

    @Override
    public void finalize(WorkflowContext context, String status) throws WorkflowException {
        context.addRecord(new NoteRecord("Workflow finalized with status: " + status));
    }
}
