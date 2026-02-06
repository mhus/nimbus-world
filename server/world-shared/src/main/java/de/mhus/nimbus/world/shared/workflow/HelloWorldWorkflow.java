package de.mhus.nimbus.world.shared.workflow;

import de.mhus.nimbus.shared.utils.CastUtil;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * A simple "Hello World" workflow implementation.
 *
 * This workflow initializes with a greeting message and optional sleep duration and error rate.
 * It enqueues a job to simulate processing and completes with the greeting message upon success.
 *
 * Executor: workflow-job-executor
 * Type: hello-world
 * Parameter:
 * - greeting (required): The greeting message to use in the workflow.
 * - sleep (optional): Time in seconds to sleep before completing the job (default: 0).
 * - errorRate (optional): Probability (0.0 to 1.0) of simulating a job failure (default: 0).
 */
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
        double errorRate = CastUtil.todouble(params.get("errorRate"), 0);
        return Map.of(
                "greeting", greeting,
                "sleep", sleep,
                "errorRate", errorRate
        );
    }

    @Override
    public void start(WorkflowContext context) throws WorkflowException {
        context.addNote("Workflow started");
        int sleep = (int)context.getParameters().get("sleep");
        double errorRate = (double)context.getParameters().get("errorRate");
        context.enqueueJob("hello-world","", CastUtil.mapStringOfString(
                "sleep", sleep,
                "errorRate", errorRate
        ));
        context.updateWorkflowStatus("waiting-for-job");
    }

    @OnSuccess("waiting-for-job")
    public void onJobSuccess(WorkflowContext context) throws WorkflowException {
        String greeting = context.getJobResultString("message").orElseThrow();
        context.addNote("Job completed successfully");
        context.doComplete(greeting);
    }

    @Override
    public void finalize(WorkflowContext context, String status) throws WorkflowException {
        context.addRecord(new NoteRecord("Workflow finalized with status: " + status));
    }
}
