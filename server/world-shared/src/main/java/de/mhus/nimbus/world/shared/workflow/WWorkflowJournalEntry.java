package de.mhus.nimbus.world.shared.workflow;

import de.mhus.nimbus.shared.persistence.ActualSchemaVersion;
import de.mhus.nimbus.shared.types.Identifiable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * MongoDB Entity for workflow journal entries.
 * Journal entries are immutable records of workflow execution steps.
 * A workflow consists of a list of journal entries that document its progression.
 */
@Document(collection = "w_workflow_journal")
@ActualSchemaVersion("1.0.0")
@CompoundIndexes({
        @CompoundIndex(name = "world_workflow_created_idx",
                def = "{ 'worldId': 1, 'workflowId': 1, 'createdAt': 1 }"),
        @CompoundIndex(name = "world_workflow_type_idx",
                def = "{ 'worldId': 1, 'workflowId': 1, 'type': 1, 'createdAt': 1 }"),
        @CompoundIndex(name = "world_type_created_idx",
                def = "{ 'worldId': 1, 'type': 1, 'createdAt': 1 }")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WWorkflowJournalEntry implements Identifiable {

    @Id
    private String id;

    /**
     * World identifier where this workflow belongs.
     */
    @Indexed
    private String worldId;

    /**
     * Workflow identifier to group related journal entries.
     */
    @Indexed
    private String workflowId;

    /**
     * Entry type (e.g., "started", "progress", "completed", "error").
     * Used to categorize and filter journal entries.
     */
    @Indexed
    private String type;

    /**
     * Journal entry data (JSON, text, or any relevant information).
     * Content depends on the type of journal entry.
     */
    private String data;

    /**
     * Creation timestamp of this journal entry.
     * Journal entries are immutable and ordered by creation time.
     */
    @Indexed
    private Instant createdAt;

    /**
     * Transient field for caching deserialized journal entry.
     * Not stored in MongoDB.
     */
    @Transient
    private JournalEntry journalEntry;

    /**
     * Initialize creation timestamp for new journal entry.
     */
    public void touchCreate() {
        createdAt = Instant.now();
    }

    public JournalEntry toJournalEntry(WorkflowContext context) {
        if (journalEntry == null) {
            try {
                Class<?> clazz = context.createJournalEntity(type);
                if (JournalStringEntry.class.isAssignableFrom(clazz)) {
                    JournalStringEntry entry = (JournalStringEntry) clazz.getDeclaredConstructor().newInstance();
                    entry.stringToEntry(data);
                    return entry;
                } else {
                    return context.fromJson(data, (Class<? extends JournalEntry>) clazz);
                }
            } catch (WorkflowException e) {
                throw e;
            } catch (Exception e) {
                throw new WorkflowException(workflowId, "Cannot convert journal entry to object: " + data, e);
            }
        }
        return journalEntry;
    }
}
