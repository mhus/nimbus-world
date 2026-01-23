package de.mhus.nimbus.world.control.api;

import de.mhus.nimbus.world.shared.rest.BaseEditorController;
import de.mhus.nimbus.world.shared.workflow.StartRecord;
import de.mhus.nimbus.world.shared.workflow.WWorkflowJournalRecord;
import de.mhus.nimbus.world.shared.workflow.WWorkflowJournalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST Controller for managing workflows.
 * Provides endpoints to list, view, and delete workflows based on journal entries.
 */
@RestController
@RequestMapping("/control/worlds/{worldId}/workflows")
@RequiredArgsConstructor
public class WorkflowController extends BaseEditorController {

    private final WWorkflowJournalService workflowJournalService;

    /**
     * Response DTO for workflow summary.
     */
    public record WorkflowSummary(
            String workflowId,
            String workflowName,
            Instant createdAt
    ) {}

    /**
     * Response DTO for workflow journal entry.
     */
    public record WorkflowJournalEntry(
            String id,
            String worldId,
            String workflowId,
            String type,
            String data,
            Instant createdAt
    ) {}

    /**
     * Get all workflows for a world (based on StartRecord entries).
     * GET /control/worlds/{worldId}/workflows
     */
    @GetMapping
    public ResponseEntity<?> listWorkflows(@PathVariable String worldId) {
        var error = validateId(worldId, "worldId");
        if (error != null) return error;

        try {
            // Get all StartRecord entries for this world
            String startRecordType = StartRecord.class.getCanonicalName();
            List<WWorkflowJournalRecord> startRecords = workflowJournalService
                    .getWorkflowJournalRecordsForType(worldId, startRecordType);

            // Map to workflow summaries
            List<WorkflowSummary> workflows = startRecords.stream()
                    .map(record -> new WorkflowSummary(
                            record.getWorkflowId(),
                            record.getData(), // StartRecord.entryToString() returns workflow name
                            record.getCreatedAt()
                    ))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(workflows);
        } catch (Exception e) {
            return bad("Failed to list workflows: " + e.getMessage());
        }
    }

    /**
     * Get all journal entries for a specific workflow.
     * GET /control/worlds/{worldId}/workflows/{workflowId}
     */
    @GetMapping("/{workflowId}")
    public ResponseEntity<?> getWorkflowJournal(
            @PathVariable String worldId,
            @PathVariable String workflowId) {

        var error = validateId(worldId, "worldId");
        if (error != null) return error;

        var error2 = validateId(workflowId, "workflowId");
        if (error2 != null) return error2;

        try {
            List<WWorkflowJournalRecord> records = workflowJournalService
                    .getWorkflowJournalRecords(worldId, workflowId);

            if (records.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Workflow not found: " + workflowId));
            }

            List<WorkflowJournalEntry> entries = records.stream()
                    .map(record -> new WorkflowJournalEntry(
                            record.getId(),
                            record.getWorldId(),
                            record.getWorkflowId(),
                            record.getType(),
                            record.getData(),
                            record.getCreatedAt()
                    ))
                    .collect(Collectors.toList());

            return ResponseEntity.ok(entries);
        } catch (Exception e) {
            return bad("Failed to get workflow journal: " + e.getMessage());
        }
    }

    /**
     * Delete a workflow (clear all journal entries).
     * DELETE /control/worlds/{worldId}/workflows/{workflowId}
     */
    @DeleteMapping("/{workflowId}")
    public ResponseEntity<?> deleteWorkflow(
            @PathVariable String worldId,
            @PathVariable String workflowId) {

        var error = validateId(worldId, "worldId");
        if (error != null) return error;

        var error2 = validateId(workflowId, "workflowId");
        if (error2 != null) return error2;

        try {
            // Verify workflow exists
            List<WWorkflowJournalRecord> records = workflowJournalService
                    .getWorkflowJournalRecords(worldId, workflowId);

            if (records.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Workflow not found: " + workflowId));
            }

            // Clear all journal entries for this workflow
            workflowJournalService.clearWorkflowJournal(worldId, workflowId);

            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            return bad("Failed to delete workflow: " + e.getMessage());
        }
    }
}
