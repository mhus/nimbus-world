package de.mhus.nimbus.world.shared.workflow;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for workflow journal entries.
 */
@Repository
public interface WWorkflowJournalRepository extends MongoRepository<WWorkflowJournalEntry, String> {

    /**
     * Find all journal entries for a specific workflow, ordered by creation time ascending.
     *
     * @param worldId World identifier
     * @param workflowId Workflow identifier
     * @return List of journal entries ordered by createdAt ascending
     */
    List<WWorkflowJournalEntry> findByWorldIdAndWorkflowIdOrderByCreatedAtAsc(String worldId, String workflowId);

    /**
     * Find all journal entries for a specific workflow and type, ordered by creation time ascending.
     *
     * @param worldId World identifier
     * @param workflowId Workflow identifier
     * @param type Entry type
     * @return List of journal entries ordered by createdAt ascending
     */
    List<WWorkflowJournalEntry> findByWorldIdAndWorkflowIdAndTypeOrderByCreatedAtAsc(String worldId, String workflowId, String type);

    /**
     * Find all journal entries for a world and type, ordered by creation time ascending.
     *
     * @param worldId World identifier
     * @param type Entry type
     * @return List of journal entries ordered by createdAt ascending
     */
    List<WWorkflowJournalEntry> findByWorldIdAndTypeOrderByCreatedAtAsc(String worldId, String type);

    /**
     * Delete all journal entries for a specific workflow.
     *
     * @param worldId World identifier
     * @param workflowId Workflow identifier
     */
    void deleteByWorldIdAndWorkflowId(String worldId, String workflowId);
}
