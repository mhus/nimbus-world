package de.mhus.nimbus.world.shared.workflow;

import java.util.Map;

/**
 * Interface for workflow implementations.
 * Workflows are Spring beans that implement this interface.
 */
public interface Workflow {

    /**
     * Get the workflow name.
     * Default implementation returns the canonical class name.
     *
     * @return Workflow name
     */
    default String name() {
        return getClass().getCanonicalName();
    }

    /**
     * Initialize the workflow before starting.
     * This method is called before the workflow is started.
     * If this method throws an exception, the workflow fails and no journal entry is created.
     *
     * @param worldId World identifier
     * @param params Initialization parameters
     * @throws WorkflowException If initialization fails
     */
    Map<String, String> initialize(String worldId, Map<String, String> params) throws WorkflowException;

    /**
     * Start the workflow execution.
     * This method is called after successful initialization.
     *
     * @param context Workflow context with journal entries
     * @throws WorkflowException If workflow execution fails
     */
    void start(WorkflowContext context) throws WorkflowException;

    /**
     * Handle workflow event.
     * This method is called when an event is received for this workflow.
     *
     * @param context Workflow context with journal entries
     * @param event Event to process
     * @throws WorkflowException If event handling fails
     */
    void event(WorkflowContext context, WorkflowEvent event) throws WorkflowException;

    /**
     * Finalize the workflow execution.
     * This method is called when the workflow is completed or terminated.
     *
     * @param context
     * @param status
     * @throws WorkflowException
     */
    void finalize(WorkflowContext context, String status) throws WorkflowException;

}
