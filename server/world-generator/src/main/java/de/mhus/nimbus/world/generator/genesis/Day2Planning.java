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
                "instructionsDocumentId", (String)context.getParameters().get(GenesisConst.INSTRUCTIONS_DOCUMENT_ID)
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
        String hexCoordinates = context.getJobResultAsString().orElseThrow();
        context.doComplete(hexCoordinates);
    }

    @Override
    public void finalize(WorkflowContext context, String status) throws WorkflowException {
    }
}
