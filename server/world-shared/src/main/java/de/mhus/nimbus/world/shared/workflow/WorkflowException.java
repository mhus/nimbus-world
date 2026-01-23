package de.mhus.nimbus.world.shared.workflow;

/**
 * Exception thrown during workflow execution.
 */
public class WorkflowException extends RuntimeException {

    private final String workflowId;

    public WorkflowException(String workflowId, String message) {
        super(message);
        this.workflowId = workflowId;
    }

    public WorkflowException(String workflowId, String message, Throwable cause) {
        super(message, cause);
        this.workflowId = workflowId;
    }

    public String getWorkflowId() {
        return workflowId;
    }
}
