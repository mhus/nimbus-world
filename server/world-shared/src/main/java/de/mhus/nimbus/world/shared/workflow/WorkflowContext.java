package de.mhus.nimbus.world.shared.workflow;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    WWorkflowJournalService journalService;

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
    private List<WWorkflowJournalEntry> journal;

    public Class<?> createJournalEntity(String type) {
        try {
            return Class.forName(type);
        } catch (ClassNotFoundException e) {
            throw new WorkflowException(workflowId, "Unknown journal entry type: " + type, e);
        }
    }

    public JournalEntry fromJson(String data, Class<? extends JournalEntry> clazz) {
        try {
            return journalService.getObjectMapper().readValue(data, clazz);
        } catch (Exception e) {
            throw new WorkflowException(workflowId, "Cannot parse journal entry data: " + data, e);
        }
    }

    public String getStatus() {
        return getLastJournalEntry(WorkflowStatus.class)
                .map(entry -> ((WorkflowStatus) entry).getStatus())
                .orElse("unknown"); // sould not happen
    }

    public Map<String, String> getParameters() {
        return getLastJournalEntry(WorkflowParameters.class)
                .map(entry -> ((WorkflowParameters) entry).getParameters())
                .orElse(Map.of());
    }

    public Optional<JournalEntry> getLastJournalEntry(Class<? extends JournalEntry> type) {
        return getLastJournalEntry(type.getCanonicalName()).map(
                entry -> entry.toJournalEntry(this));
    }

    public Optional<WWorkflowJournalEntry> getLastJournalEntry(String type) {
        return journal.stream().filter(
                entry -> entry.getType().equals(type)
        ).reduce((first, second) -> second);
    }

    public void reloadJournal() {
        this.journal = journalService.getWorkflowJournalEntries(worldId, workflowId);
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

}
