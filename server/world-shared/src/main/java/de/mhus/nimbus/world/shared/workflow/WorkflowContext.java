package de.mhus.nimbus.world.shared.workflow;

import de.mhus.nimbus.shared.utils.CastUtil;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Context for workflow execution.
 * Contains world identifier and journal entries.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class WorkflowContext {

    WWorkflowJournalService  journalService;

    WorkflowService workflowService;
    /**
     * World identifier where this workflow executes.
     */
    private String worldId;

    /**
     * Workflow identifier.
     */
    private String workflowId;

    /**
     * Workflow Name.
     */
    private String workflowName;

    /**
     * Journal entries for this workflow.
     * Ordered by creation time ascending.
     */
    private List<WWorkflowJournalRecord> journal;

    /**
     * The event that triggered the current workflow execution step, if applicable.
     */
    private WorkflowEvent event;

    @Builder.Default
    private List<Job> jobQueue = new ArrayList<>();

    public Class<?> createJournalRecordClass(String type) {
        try {
            return Class.forName(type);
        } catch (ClassNotFoundException e) {
            throw new WorkflowException(workflowId, "Unknown journal entry type: " + type, e);
        }
    }

    public JournalRecord fromJson(String data, Class<? extends JournalRecord> clazz) {
        try {
            return journalService.getObjectMapper().readValue(data, clazz);
        } catch (Exception e) {
            throw new WorkflowException(workflowId, "Cannot parse journal entry data: " + data, e);
        }
    }

    public String getStatus() {
        return getLastJournalRecord(StatusRecord.class)
                .map(entry -> ((StatusRecord) entry).getStatus())
                .orElse("unknown"); // sould not happen
    }

    public Map<String, Object> getParameters() {
        return getLastJournalRecord(WorkflowParameters.class)
                .map(entry -> ((WorkflowParameters) entry).getParameters())
                .orElse(Map.of());
    }

    public <T extends JournalRecord> Optional<T> getLastJournalRecord(Class<T> type) {
        return getLastJournalRecord(type.getCanonicalName()).map(
                entry -> (T)entry.toJournalRecord(this));
    }

    public Optional<WWorkflowJournalRecord> getLastJournalRecord(String type) {
        return journal.stream().filter(
                entry -> entry.getType().equals(type)
        ).reduce((first, second) -> second);
    }

    public void reloadJournal() {
        this.journal = journalService.getWorkflowJournalRecords(worldId, workflowId);
    }

    /**
     * Need to reload journal after this!
     *
     * @param status
     */
    public void updateWorkflowStatus(String status) {
        workflowService.updateWorkflowStatus(
                worldId,
                workflowId,
                status
        );
    }

    /**
     * Enqueue a job for execution. The job will be executed after the current workflow step is completed.
     *
     * @param executor the job executor to use for this job
     * @param type the job type
     * @param parameters the parameters for the job
     */
    public void enqueueJob(String executor, String type, Map<String, String> parameters) {
        enqueueJob(new Job(getWorldId(), executor, type, null, null, parameters));
    }

    /**
     * Enqueue a job for execution. The job will be executed after the current workflow step is completed.
     *
     * @param executor the job executor to use for this job
     * @param type the job type
     * @param location the location of the job to execute. See LocationService
     * @param titleSuffix optional suffix to append to the generated job title (e.g., "GROUND for Grid 0;0")
     * @param parameters the parameters for the job
     */
    public void enqueueJob(String executor, String type, String location, String titleSuffix, Map<String, String> parameters) {
        enqueueJob(new Job(getWorldId(), executor, type, location, titleSuffix, parameters));
    }

    public void enqueueJob(Job job) {
        jobQueue.add(job);
    }

    /**
     * Add a journal entry to the workflow journal. This will be stored in the database and can be used to track the workflow execution history.
     * @param record the journal record to add, can be used to store any information about the workflow execution, e.g. intermediate results, debug information, etc.
     */
    public void addRecord(JournalRecord record) {
        journalService.addWorkflowJournalRecord(
                worldId,
                workflowId,
                record
        );
    }

    /**
     * Add a note to the workflow journal. Can be used to store any information about the workflow execution, e.g. intermediate results, debug information, etc.
     * @param note the note to add to the journal
     */
    public void addNote(String note) {
        addRecord(new NoteRecord(note));
    }

    /**
     * Complete the workflow with a success status and a result.
     * @param result the result to store in the journal, can be used to store any information about the workflow completion, e.g. output data or summary of the workflow execution.
     */
    public void doComplete(String result) {
        addRecord(new ResultRecord(result));
        updateWorkflowStatus(StatusRecord.COMPLETED);
    }

    /**
     * Complete the workflow with a failure status and a result.
     * @param result the result to store in the journal, can be used to store error details or other information about the failure.
     */
    public void doFail(String result) {
        addRecord(new ResultRecord(result));
        updateWorkflowStatus(StatusRecord.FAILED);
    }

    /**
     * Complete the workflow with a success status and a result.
     * @param result the result to store in the journal, can be used to store any information about the workflow completion, e.g. output data or summary of the workflow execution.
     */
    public void doComplete(Map<String,Object> result) {
        addRecord(new ResultRecord(result));
        updateWorkflowStatus(StatusRecord.COMPLETED);
    }

    /**
     * Complete the workflow with a failure status and a result.
     * @param result the result to store in the journal, can be used to store error details or other information about the failure.
     */
    public void doFail(Map<String, Object> result) {
        addRecord(new ResultRecord(result));
        updateWorkflowStatus(StatusRecord.FAILED);
    }

    /**
     * Helper methods to access job result from event data.
     * Assumes that the previous job result is stored in event data with key JobExecutor.PREVIOUS_JOB_RESULT.
     */
    public Optional<String> getJobResultAsString() {
        if (getEvent() == null || getEvent().getData() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(getEvent().getData().get(JobExecutor.PREVIOUS_JOB_RESULT));
    }

    /**
     * Helper method to get job result as map. Assumes that the job result is stored as JSON string in event data with key JobExecutor.PREVIOUS_JOB_RESULT.
     *
     * @return the job result as map, or empty map if the job result is not available or cannot be parsed as JSON.
     */
    public Map<String, Object> getJobResultAsMap() {
        try {
            return CastUtil.stringToMap(getJobResultAsString().orElse("{}"));
        } catch (Exception e) {
            return Map.of();
        }
    }

    /**
     * Helper method to get a specific value from the job result map.
     *
     * @param key the key to look for in the job result map
     * @return the value associated with the key in the job result map, or empty if the key is not present or the job result is not available.
     */
    public Optional<Object> getJobResultFromMap(String key) {
        return Optional.ofNullable(getJobResultAsMap().get(key));
    }

    /**
     * Helper method to get a specific value from the job result map as string.
     * @param key the key to look for in the job result map
     * @return the value associated with the key in the job result map as string, or empty if the key is not present or the job result is not available.
     */
    public Optional<String> getJobResultString(String key) {
        var result = getJobResultAsMap().get(key);
        if (result == null) {
            return Optional.empty();
        }
        return Optional.of(String.valueOf(result));
    }

    /**
     * Helper method to get job error message from event data. Assumes that the previous job error message is stored in event data with key JobExecutor.PREVIOUS_JOB_ERROR_MESSAGE.
     * @return the job error message, or null if not available.
     */
    public String getJobError() {
        return getEvent().getData().get(JobExecutor.PREVIOUS_JOB_ERROR_MESSAGE);
    }

    public record Job(String worldId, String executor, String type, String location, String titleSuffix, Map<String, String> parameters) {
    }
}
