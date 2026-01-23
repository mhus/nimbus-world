package de.mhus.nimbus.world.shared.workflow;

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
@AllArgsConstructor
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

    public Map<String, String> getParameters() {
        return getLastJournalRecord(WorkflowParameters.class)
                .map(entry -> ((WorkflowParameters) entry).getParameters())
                .orElse(Map.of());
    }

    public Optional<JournalRecord> getLastJournalRecord(Class<? extends JournalRecord> type) {
        return getLastJournalRecord(type.getCanonicalName()).map(
                entry -> entry.toJournalRecord(this));
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

    public void enqueueJob(String executor, String type, Map<String, String> parameters) {
        jobQueue.add(new Job(executor, type, null, parameters));
    }

    public void enqueueJob(String executor, String type, String location, Map<String, String> parameters) {
        jobQueue.add(new Job(executor, type, location, parameters));
    }

    public void addRecord(JournalRecord record) {
        journalService.addWorkflowJournalRecord(
                worldId,
                workflowId,
                record
        );
    }


    public record Job(String executor, String type, String location, Map<String, String> parameters) {
    }
}
