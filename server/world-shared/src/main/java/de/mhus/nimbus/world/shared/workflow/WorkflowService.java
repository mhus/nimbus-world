package de.mhus.nimbus.world.shared.workflow;

import de.mhus.nimbus.shared.utils.LocationService;
import de.mhus.nimbus.world.shared.job.NextJob;
import de.mhus.nimbus.world.shared.job.WJobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing workflows.
 * Orchestrates workflow lifecycle: initialization, start, and event handling.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        value = "nimbus.services.workflows",
        havingValue = "true",
        matchIfMissing = false
)
public class WorkflowService {

    private final WWorkflowJournalService journalService;
    private final List<Workflow> workflows;
    private WJobService jobService;
    private LocationService locationService;

    /**
     * Create and initialize a workflow.
     * Generates a new UUID for the workflow and calls initialize() on the workflow implementation.
     * If initialization fails, no journal entry is created.
     *
     * @param workflowName Name of the workflow to create
     * @param worldId World identifier
     * @param params Initialization parameters
     * @return Workflow identifier for the created workflow instance
     * @throws WorkflowException If workflow not found or initialization fails
     */
    public String createWorkflow(String workflowName, String worldId, Map<String, String> params) throws WorkflowException {
        log.info("Creating workflow: name={}, worldId={}", workflowName, worldId);

        Workflow workflow = findWorkflow(workflowName)
                .orElseThrow(() -> new WorkflowException(null, "Workflow not found: " + workflowName));

        // Generate workflow ID from UUID
        String workflowId = UUID.randomUUID().toString();

        try {
            Map<String,String> parameters = workflow.initialize(worldId, params != null ? params : new HashMap<>());

            // Create initial status entry
            WorkflowStart start = WorkflowStart.builder()
                    .workflow(workflowName)
                    .build();
            journalService.addWorkflowJournalEntry(worldId, workflowId, start);
            WorkflowParameters workflowParameters = WorkflowParameters.builder()
                    .parameters(parameters)
                    .build();
            journalService.addWorkflowJournalEntry(worldId, workflowId, workflowParameters);

            // Create initial status entry
            WorkflowStatus status = WorkflowStatus.builder()
                    .status(WorkflowStatus.CREATED)
                    .build();
            journalService.addWorkflowJournalEntry(worldId, workflowId, status);

            log.info("Workflow created: workflowId={}, name={}, worldId={}", workflowId, workflowName, worldId);

            fireStartEvent(workflowName, worldId, workflowId);

            return workflowId;

        } catch (WorkflowException e) {
            log.error("Failed to initialize workflow: name={}, worldId={}, workflowId={}", workflowName, worldId, workflowId, e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error initializing workflow: name={}, worldId={}, workflowId={}", workflowName, worldId, workflowId, e);
            throw new WorkflowException(workflowId, "Failed to initialize workflow: " + e.getMessage(), e);
        }
    }

    public void fireJob(WorkflowContext context, String executor, String type, String server, Map<String,String > parameters) {

        var onSuccess = NextJob.builder()
                .executor(WorkflowEventJobExecutor.NAME)
                .type(WorkflowEvent.SUCCESS + ":" + context.getWorkflowName() + ":" + context.getWorkflowId())
                .server(locationService.getApplicationServiceName())
                .parameters(Map.of())
                .build();
        var onError = NextJob.builder()
                .executor(WorkflowEventJobExecutor.NAME)
                .type(WorkflowEvent.FAILURE + ":" + context.getWorkflowName() + ":" + context.getWorkflowId())
                .server(locationService.getApplicationServiceName())
                .parameters(Map.of())
                .build();

        jobService.createJob(
                context.getWorldId(),
                executor,
                type,
                parameters,
                server,
                5,
                0,
                onSuccess,
                onError
        );
    }

    private void fireStartEvent(String workflowName, String worldId, String workflowId) {
        jobService.createJob(
                worldId,
                WorkflowEventJobExecutor.NAME,
                WorkflowEvent.START + ":" + workflowName + ":" + workflowId,
                Map.of(),
                locationService.getApplicationServiceName(),
                5,
                0,
                null,
                null
        );
    }

    /**
     * Start a workflow.
     * Loads the workflow context and calls start() on the workflow implementation.
     *
     * @param workflowName Name of the workflow
     * @param worldId World identifier
     * @param workflowId Workflow identifier
     * @throws WorkflowException If workflow not found or start fails
     */
    public String startWorkflow(String worldId, String workflowName, String workflowId) throws WorkflowException {
        log.info("Starting workflow: name={}, worldId={}, workflowId={}", workflowName, worldId, workflowId);

        Workflow workflow = findWorkflow(workflowName)
                .orElseThrow(() -> new WorkflowException(workflowId, "Workflow not found: " + workflowName));
        // Reload context with new status
        WorkflowContext context = loadWorkflowContext(worldId, workflowId, workflowName);
        try {
            // Update status to RUNNING
            WorkflowStatus status = WorkflowStatus.builder()
                    .status("RUNNING")
                    .build();
            journalService.addWorkflowJournalEntry(worldId, workflowId, status);

            workflow.start(context);
            log.info("Workflow started successfully: workflowId={}", workflowId);
        } catch (Exception e) {
            log.error("Unexpected error starting workflow: workflowId={}", workflowId, e);
            updateWorkflowStatus(worldId, workflowId, "FAILED");
        }
        checkForFinalize(workflow, context);
        return context.getStatus();
    }

    /**
     * Send an event to a workflow.
     * Loads the workflow context and calls event() on the workflow implementation.
     *
     * @param workflowName Name of the workflow
     * @param worldId World identifier
     * @param workflowId Workflow identifier
     * @param event Event to send
     * @return Updated workflow status after processing the event
     * @throws WorkflowException If workflow not found or event handling fails
     */
    public String processEvent(String workflowName, String worldId, String workflowId, WorkflowEvent event) throws WorkflowException {
        log.debug("Sending event to workflow: workflowId={}, event={}", workflowId, event.getEvent());

        Workflow workflow = findWorkflow(workflowName)
                .orElseThrow(() -> new WorkflowException(workflowId, "Workflow not found: " + workflowName));

        WorkflowContext context = loadWorkflowContext(worldId, workflowId, workflowName);
        String status = context.getStatus();
        if (isStatusFinal(status)) {
            log.warn("Cannot process event for finalized workflow: workflowId={}, status={}, event={}", workflowId, status, event.getEvent());
            return status;
        }

        try {
            // Log event to journal
            journalService.addWorkflowJournalEntry(worldId, workflowId, event);

            // Reload context with new event
            context = loadWorkflowContext(worldId, workflowId, workflowName);

            workflow.event(context, event);
            log.debug("Event handled successfully: workflowId={}, event={}", workflowId, event.getEvent());
        } catch (Exception e) {
            log.error("Unexpected error handling event: workflowId={}, event={}", workflowId, event.getEvent(), e);
        }
        checkForFinalize(workflow, context);
        return context.getStatus();
    }

    private void checkForFinalize(Workflow workflow, WorkflowContext context) {
        context.reloadJournal();
        String status = context.getStatus();
        if (isStatusFinal(status)) {
            try {
                workflow.finalize(context, status);
            } catch (Exception e) {
                log.error("Error during workflow finalization: workflowId={}", context.getWorkflowId(), e);
            }
            log.info("Workflow finalized: workflowId={}, status={}", context.getWorkflowId(), status);
            // journalService.clearWorkflowJournal(context.getWorldId(), context.getWorkflowId());
        }
    }

    private boolean isStatusFinal(String status) {
        return "COMPLETED".equals(status) || "FAILED".equals(status) || "CANCELLED".equals(status);
    }

    /**
     * Get workflow context with all journal entries.
     *
     * @param worldId World identifier
     * @param workflowId Workflow identifier
     * @return Workflow context
     */
    public WorkflowContext loadWorkflowContext(String worldId, String workflowId, String workflowName) {
        List<WWorkflowJournalEntry> journal = journalService.getWorkflowJournalEntries(worldId, workflowId);

        return WorkflowContext.builder()
                .worldId(worldId)
                .workflowId(workflowId)
                .workflowName(workflowName)
                .journal(journal)
                .build();
    }

    /**
     * Update workflow status.
     *
     * @param worldId World identifier
     * @param workflowId Workflow identifier
     * @param status New status
     */
    public void updateWorkflowStatus(String worldId, String workflowId, String status) {
        WorkflowStatus workflowStatus = WorkflowStatus.builder()
                .status(status)
                .build();
        journalService.addWorkflowJournalEntry(worldId, workflowId, workflowStatus);
        log.debug("Updated workflow status: workflowId={}, status={}", workflowId, status);
    }

    /**
     * Find workflow by name.
     *
     * @param name Workflow name
     * @return Optional workflow
     */
    private Optional<Workflow> findWorkflow(String name) {
        return workflows.stream()
                .filter(w -> w.name().equals(name))
                .findFirst();
    }

}
