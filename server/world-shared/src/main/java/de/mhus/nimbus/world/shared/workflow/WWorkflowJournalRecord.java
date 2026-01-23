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
@Document(collection = "w_workflow_record")
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
public class WWorkflowJournalRecord implements Identifiable {

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
     * Record type (e.g., "started", "progress", "completed", "error").
     * Used to categorize and filter journal entries.
     */
    @Indexed
    private String type;

    /**
     * Journal Record data (JSON, text, or any relevant information).
     * Content depends on the type of journal Record.
     */
    private String data;

    /**
     * Creation timestamp of this journal Record.
     * Journal entries are immutable and ordered by creation time.
     */
    @Indexed
    private Instant createdAt;

    /**
     * Transient field for caching deserialized journal Record.
     * Not stored in MongoDB.
     */
    @Transient
    private JournalRecord journalRecord;

    /**
     * Initialize creation timestamp for new journal Record.
     */
    public void touchCreate() {
        createdAt = Instant.now();
    }

    public JournalRecord toJournalRecord(WorkflowContext context) {
        if (journalRecord == null) {
            try {
                Class<?> clazz = context.createJournalRecordClass(type);
                if (JournalStringRecord.class.isAssignableFrom(clazz)) {
                    JournalStringRecord record = (JournalStringRecord) clazz.getDeclaredConstructor().newInstance();
                    record.stringToRecord(data);
                    return record;
                } else {
                    return context.fromJson(data, (Class<? extends JournalRecord>) clazz);
                }
            } catch (WorkflowException e) {
                throw e;
            } catch (Exception e) {
                throw new WorkflowException(workflowId, "Cannot convert journal record to object: " + data, e);
            }
        }
        return journalRecord;
    }
}
