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
    private final WJobService jobService;
    private final LocationService locationService;

    /**
     * Create and initialize a workflow.
     * Generates a new UUID for the workflow and calls initialize() on the workflow implementation.
     * If initialization fails, no journal entry is created.
     *
     * @param workflowName Name of the workflow to create
     * @param worldId World identifier
     * @param params Initialization parameters
     * @param jobId Optional a jobId to link the workflow to a job
     * @return Workflow identifier for the created workflow instance
     * @throws WorkflowException If workflow not found or initialization fails
     */
    public String createWorkflow(String worldId, String workflowName, Map<String, String> params, String jobId) throws WorkflowException {
        log.info("Creating workflow: name={}, worldId={}, jobId={}", workflowName, worldId, jobId);

        Workflow workflow = findWorkflow(workflowName)
                .orElseThrow(() -> new WorkflowException(null, "Workflow not found: " + workflowName));

        // Generate workflow ID from UUID
        String workflowId = UUID.randomUUID().toString();

        try {
            Map<String,String> parameters = workflow.initialize(worldId, params != null ? params : new HashMap<>());

            // Create initial status entry
            journalService.addWorkflowJournalRecord(worldId, workflowId, new StartRecord(workflowName));
            if (jobId != null) {
                journalService.addWorkflowJournalRecord(worldId, workflowId, new JobIdRecord(jobId));
            }
            WorkflowParameters workflowParameters = WorkflowParameters.builder()
                    .parameters(parameters)
                    .build();
            journalService.addWorkflowJournalRecord(worldId, workflowId, workflowParameters);

            // Create initial status entry
            StatusRecord status = StatusRecord.builder()
                    .status(StatusRecord.CREATED)
                    .build();
            journalService.addWorkflowJournalRecord(worldId, workflowId, status);

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

    private void fireJob(WorkflowContext context, String executor, String type, String location, Map<String,String > parameters) {

        var onSuccess = NextJob.builder()
                .executor(WorkflowEventJobExecutor.NAME)
                .type(WorkflowEvent.SUCCESS + ":" + context.getWorkflowName() + ":" + context.getWorkflowId())
                .location(locationService.getApplicationServiceName())
                .parameters(Map.of())
                .build();
        var onError = NextJob.builder()
                .executor(WorkflowEventJobExecutor.NAME)
                .type(WorkflowEvent.FAILURE + ":" + context.getWorkflowName() + ":" + context.getWorkflowId())
                .location(locationService.getApplicationServiceName())
                .parameters(Map.of())
                .build();

        jobService.createJob(
                context.getWorldId(),
                executor,
                type,
                parameters,
                location,
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
            workflow.start(context);
            log.info("Workflow started successfully: workflowId={}", workflowId);
        } catch (Exception e) {
            log.error("Unexpected error starting workflow: workflowId={}", workflowId, e);
            updateWorkflowStatus(worldId, workflowId, "FAILED");
        }
        checkAfterRunTask(workflow, context);
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
            journalService.addWorkflowJournalRecord(worldId, workflowId, event);

            // Reload context with new event
            context = loadWorkflowContext(worldId, workflowId, workflowName);

            workflow.event(context, event);
            log.debug("Event handled successfully: workflowId={}, event={}", workflowId, event.getEvent());
        } catch (Exception e) {
            log.error("Unexpected error handling event: workflowId={}, event={}", workflowId, event.getEvent(), e);
        }
        checkAfterRunTask(workflow, context);
        return context.getStatus();
    }

    private void checkAfterRunTask(Workflow workflow, WorkflowContext context) {
        context.reloadJournal();
        String status = context.getStatus();
        if (isStatusFinal(status)) {
            try {
                workflow.finalize(context, status);
            } catch (Exception e) {
                log.error("Error during workflow finalization: workflowId={}", context.getWorkflowId(), e);
            }
            log.info("Workflow finalized: workflowId={}, status={}", context.getWorkflowId(), status);

            var jobId = context.getLastJournalRecord(JobIdRecord.class).orElse(null);
            if (jobId != null) {
                var result = context.getLastJournalRecord(ResultRecord.class).orElse(null);
                log.info("Marking linked job as completed: jobId={}, workflowId={}", jobId.getJobId(), context.getWorkflowId());
                if (isStatusFailed(status)) {
                    jobService.markJobFailed(jobId.getJobId(), result == null ? null : result.getResult());
                } else {
                    jobService.markJobCompleted(jobId.getJobId(), result == null ? null : result.getResult());
                }
            }

            // journalService.clearWorkflowJournal(context.getWorldId(), context.getWorkflowId());
            return;
        }

        for (WorkflowContext.Job job : context.getJobQueue()) {
            fireJob(context, job.executor(), job.type(), job.location(), job.parameters());
        }


    }

    private boolean isStatusFinal(String status) {
        return StatusRecord.COMPLETED.equals(status) || StatusRecord.FAILED.equals(status) || StatusRecord.TERMINATED.equals(status);
    }

    private boolean isStatusFailed(String status) {
        return StatusRecord.FAILED.equals(status) || StatusRecord.TERMINATED.equals(status);
    }

    /**
     * Get workflow context with all journal entries.
     *
     * @param worldId World identifier
     * @param workflowId Workflow identifier
     * @return Workflow context
     */
    public WorkflowContext loadWorkflowContext(String worldId, String workflowId, String workflowName) {
        List<WWorkflowJournalRecord> journal = journalService.getWorkflowJournalRecords(worldId, workflowId);

        return WorkflowContext.builder()
                .journalService(journalService)
                .workflowService(this)
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
        StatusRecord workflowStatus = StatusRecord.builder()
                .status(status)
                .build();
        try {
            Thread.sleep(1);  // Ensure different timestamp
        } catch (InterruptedException e) {}
        journalService.addWorkflowJournalRecord(worldId, workflowId, workflowStatus);
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

    public boolean existsWorkflow(String worldId, String workflowId) {
        return journalService.getWorkflowJournalRecordsForType(worldId, workflowId, StartRecord.class.getCanonicalName() ).size() > 0;
    }

}
