package de.mhus.nimbus.world.shared.workflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for managing workflow journal entries.
 * Provides business logic for creating and querying immutable workflow journal records.
 * This service has data ownership over WWorkflowJournalEntry entities.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        value = "nimbus.services.workflows",
        havingValue = "true",
        matchIfMissing = false
)
public class WWorkflowJournalService {

    private final WWorkflowRecordRepository repository;
    @Getter
    private final ObjectMapper objectMapper;

    /**
     * Get all journal entries for a specific workflow, sorted by creation time ascending.
     *
     * @param worldId World identifier
     * @param workflowId Workflow identifier
     * @return List of journal entries ordered by createdAt ascending
     */
    public List<WWorkflowJournalRecord> getWorkflowJournalRecords(String worldId, String workflowId) {
        log.debug("Getting workflow journal entries: worldId={}, workflowId={}", worldId, workflowId);
        return repository.findByWorldIdAndWorkflowIdOrderByCreatedAtAsc(worldId, workflowId);
    }

    /**
     * Get all journal entries for a specific workflow and type, sorted by creation time ascending.
     *
     * @param worldId World identifier
     * @param workflowId Workflow identifier
     * @param type Entry type filter
     * @return List of journal entries ordered by createdAt ascending
     */
    public List<WWorkflowJournalRecord> getWorkflowJournalRecordsForType(String worldId, String workflowId, String type) {
        log.debug("Getting workflow journal entries: worldId={}, workflowId={}, type={}", worldId, workflowId, type);
        return repository.findByWorldIdAndWorkflowIdAndTypeOrderByCreatedAtAsc(worldId, workflowId, type);
    }

    /**
     * Get all journal entries for a world and type, sorted by creation time ascending.
     *
     * @param worldId World identifier
     * @param type Entry type filter
     * @return List of journal entries ordered by createdAt ascending
     */
    public List<WWorkflowJournalRecord> getWorkflowJournalRecordsForType(String worldId, String type) {
        log.debug("Getting workflow journal entries by type: worldId={}, type={}", worldId, type);
        return repository.findByWorldIdAndTypeOrderByCreatedAtAsc(worldId, type);
    }

    public WWorkflowJournalRecord addWorkflowJournalRecord(String worldId, String workflowId, JournalRecord entry) {
        if (entry == null) {
            throw new IllegalArgumentException("Journal entry cannot be null");
        }
        if (entry instanceof JournalStringRecord journalStringEntry) {
            String data = journalStringEntry.entryToString();
            return addWorkflowJournalRecord(worldId, workflowId, entry.getClass().getCanonicalName(), data);
        } else {
            return addWorkflowJournalRecord(worldId, workflowId, entry.getClass().getCanonicalName(), toJson(entry));
        }
    }

    /**
     * Convert object to JSON string.
     *
     * @param object Object to convert
     * @return JSON string
     */
    private String toJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize object to JSON", e);
            return "{}";
        }
    }

    /**
     * Add a new journal entry to a workflow.
     * Journal entries are immutable once created.
     *
     * @param worldId World identifier
     * @param workflowId Workflow identifier
     * @param type Entry type
     * @param data Entry data (JSON, text, or any relevant information)
     * @return The created journal entry
     */
    public WWorkflowJournalRecord addWorkflowJournalRecord(String worldId, String workflowId, String type, String data) {
        log.debug("Adding workflow journal entry: worldId={}, workflowId={}, type={}", worldId, workflowId, type);

        WWorkflowJournalRecord entry = WWorkflowJournalRecord.builder()
                .worldId(worldId)
                .workflowId(workflowId)
                .type(type)
                .data(data)
                .build();

        entry.touchCreate();

        WWorkflowJournalRecord saved = repository.save(entry);
        log.info("Created workflow journal entry: id={}, worldId={}, workflowId={}, type={}",
                saved.getId(), worldId, workflowId, type);

        return saved;
    }

    /**
     * Clear all journal entries for a specific workflow.
     * Use with caution as journal entries are meant to be immutable records.
     *
     * @param worldId World identifier
     * @param workflowId Workflow identifier
     */
    @Transactional
    public void clearWorkflowJournal(String worldId, String workflowId) {
        log.info("Clearing workflow journal: worldId={}, workflowId={}", worldId, workflowId);
        repository.deleteByWorldIdAndWorkflowId(worldId, workflowId);
    }
}
