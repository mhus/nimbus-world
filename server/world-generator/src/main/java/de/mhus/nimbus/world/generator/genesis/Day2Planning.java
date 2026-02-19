package de.mhus.nimbus.world.generator.genesis;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.shared.utils.CastUtil;
import de.mhus.nimbus.world.shared.region.RRegionService;
import de.mhus.nimbus.world.shared.workflow.MethodBasedWorkflow;
import de.mhus.nimbus.world.shared.workflow.OnSuccess;
import de.mhus.nimbus.world.shared.workflow.StatusRecord;
import de.mhus.nimbus.world.shared.workflow.WorkflowContext;
import de.mhus.nimbus.world.shared.workflow.WorkflowException;
import de.mhus.nimbus.world.shared.world.WDocumentService;
import de.mhus.nimbus.world.shared.world.WWorldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class Day2Planning extends MethodBasedWorkflow {

    private final WDocumentService documentService;

    @Override
    public String name() {
        return "genesis-day2-planning";
    }

    @Override
    public Map<String, Object> initialize(String worldId, Map<String, String> params) throws WorkflowException {

        var instructionsId = params.get(GenesisConst.INSTRUCTIONS_DOCUMENT_ID);
        if (Strings.isBlank(instructionsId)) {
            throw new WorkflowException(null, "instructions is required");
        }

        WorldId wid = WorldId.of(worldId).orElseThrow();
        if (documentService.findByDocumentId(wid, instructionsId).isEmpty()) {
            throw new WorkflowException(null, "instructions document not found: " + instructionsId);
        }

        return Map.of(
                GenesisConst.INSTRUCTIONS_DOCUMENT_ID, instructionsId
        );
    }

    @Override
    public void start(WorkflowContext context) throws WorkflowException {
        context.updateWorkflowStatus("translationInstruction");
        // TranslateInstructionJobExecutor
        context.enqueueJob("generator-translate-instruction", "", Map.of(
                GenesisConst.INSTRUCTIONS_DOCUMENT_ID, (String)context.getParameters().get(GenesisConst.INSTRUCTIONS_DOCUMENT_ID)
        ));
    }

    @OnSuccess("translationInstruction")
    public void onTranslationInstructionSuccess(WorkflowContext context) throws WorkflowException {
        // load documentId
        var translationDocumentId =context.getJobResultString("documentId").orElseThrow();
        if (documentService.findByDocumentId(WorldId.of(context.getWorldId()).orElseThrow(), translationDocumentId).isEmpty()) {
            throw new WorkflowException(null, "translated instruction document not found: " + translationDocumentId);
        }

        context.updateWorkflowStatus("applyInstruction");
        // ApplyTranslatedInstructionJobExecutor
        context.enqueueJob("generator-apply-translated-instruction", "", Map.of(
                "translationDocumentId", translationDocumentId
        ));
    }

    @OnSuccess("applyInstruction")
    public void onApplyInstructionSuccess(WorkflowContext context) throws WorkflowException {
        // Extract composition document ID from result
        var compositionDocumentId = context.getJobResultString("documentId")
                .orElseThrow(() -> new WorkflowException(null,
                        "ApplyTranslatedInstruction job result does not contain 'documentId'. " +
                        "Check if ApplyTranslatedInstructionJobExecutor completed successfully."));
        var totalGrids = context.getJobResultString("totalGrids").orElse("unknown");

        log.info("Composition created: documentId={}, totalGrids={}", compositionDocumentId, totalGrids);

        // Verify composition document exists
        if (documentService.findByDocumentId(WorldId.of(context.getWorldId()).orElseThrow(), compositionDocumentId).isEmpty()) {
            throw new WorkflowException(null, "composition document not found: " + compositionDocumentId);
        }

        // Store result data for later completion
        context.addRecord(Day2PlanningState.builder()
                .compositionDocumentId(compositionDocumentId)
                .totalGrids(totalGrids)
                .build());

        // Create schema image before completing
        context.updateWorkflowStatus("createSchemaImage");
        context.enqueueJob("hex-grid-schema-image", "", "",
                "Create Schema Image",
                Map.of("compositionId", compositionDocumentId));
    }

    @OnSuccess("createSchemaImage")
    public void onCreateSchemaImageSuccess(WorkflowContext context) throws WorkflowException {
        log.info("Schema image created successfully");

        // Retrieve stored composition result
        var state = context.getLastJournalRecord(Day2PlanningState.class)
                .orElseThrow(() -> new WorkflowException(null, "Planning state not found in journal"));

        // Complete with composition ID in result message
        // The composition ID will be passed to Day3Generation as a workflow parameter by the orchestrator
        context.doComplete(Map.of(
                "documentId", state.getCompositionDocumentId(),
                "totalGrids", state.getTotalGrids()
        ));
    }

    @Override
    public void finalize(WorkflowContext context, String status) throws WorkflowException {
    }
}
