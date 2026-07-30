package de.mhus.nimbus.world.generator.genesis;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.shared.utils.CastUtil;
import de.mhus.nimbus.world.generator.composer.build.HexComposition;
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

import java.util.HashMap;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.core.json.JsonReadFeature;

@Service
@Slf4j
@RequiredArgsConstructor
public class Day2Planning extends MethodBasedWorkflow {

    private final WDocumentService documentService;
    private final ObjectMapper objectMapper;

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

        // Optional epoch parameters (default: epoch=0, parentEpoch=null)
        int epoch = 0;
        Integer parentEpoch = null;
        if (Strings.isNotBlank(params.get(GenesisConst.EPOCH))) {
            epoch = Integer.parseInt(params.get(GenesisConst.EPOCH));
        }
        if (Strings.isNotBlank(params.get("parentEpoch"))) {
            parentEpoch = Integer.parseInt(params.get("parentEpoch"));
        }

        Map<String, Object> result = new HashMap<>();
        result.put(GenesisConst.INSTRUCTIONS_DOCUMENT_ID, instructionsId);
        result.put(GenesisConst.EPOCH, epoch);
        result.put("parentEpoch", parentEpoch);
        return result;
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

        // Fill missing flora/fauna values with random biome-appropriate data
        context.updateWorkflowStatus("fillModelRandomValues");
        context.enqueueJob("generator-fill-model-random-values", "", "",
                "Fill Flora/Fauna Values",
                Map.of("documentId", compositionDocumentId,
                       "worldId", context.getWorldId()));
    }

    @OnSuccess("fillModelRandomValues")
    public void onFillModelRandomValuesSuccess(WorkflowContext context) throws WorkflowException {
        // Update compositionDocumentId with the enriched document
        var enrichedDocumentId = context.getJobResultString("documentId")
                .orElseThrow(() -> new WorkflowException(null,
                        "FillModelWithRandomValues job result does not contain 'documentId'."));

        log.info("Flora/fauna values filled: enrichedDocumentId={}", enrichedDocumentId);

        WorldId wid = WorldId.of(context.getWorldId()).orElseThrow();

        // Verify enriched document exists
        if (documentService.findByDocumentId(wid, enrichedDocumentId).isEmpty()) {
            throw new WorkflowException(null, "enriched composition document not found: " + enrichedDocumentId);
        }

        // Write epoch/parentEpoch into composition document
        int epoch = (int) context.getParameters().get(GenesisConst.EPOCH);
        Integer parentEpoch = (Integer) context.getParameters().get("parentEpoch");
        updateCompositionEpoch(wid, "generator_composed", enrichedDocumentId, epoch, parentEpoch);
        log.info("Set composition epoch={}, parentEpoch={} in document {}", epoch, parentEpoch, enrichedDocumentId);

        // Update state with enriched document ID
        var state = context.getLastJournalRecord(Day2PlanningState.class)
                .orElseThrow(() -> new WorkflowException(null, "Planning state not found in journal"));
        context.addRecord(Day2PlanningState.builder()
                .compositionDocumentId(enrichedDocumentId)
                .totalGrids(state.getTotalGrids())
                .build());

        // Create schema image
        context.updateWorkflowStatus("createSchemaImage");
        context.enqueueJob("hex-grid-schema-image", "", "",
                "Create Schema Image",
                Map.of("compositionId", enrichedDocumentId));
    }

    /**
     * Update the composition document with epoch and parentEpoch values.
     */
    private void updateCompositionEpoch(WorldId wid, String collection, String documentId,
                                         int epoch, Integer parentEpoch) throws WorkflowException {
        try {
            documentService.save(wid, collection, documentId, doc -> {
                try {
                    ObjectMapper mapper = JsonMapper.builder()
                    .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                            .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
                            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                            .build();

                    HexComposition composition = mapper.readValue(doc.getContent(), HexComposition.class);
                    composition.setEpoch(epoch);
                    composition.setParentEpoch(parentEpoch);

                    doc.setContent(mapper.writeValueAsString(composition));
                } catch (Exception e) {
                    throw new RuntimeException("Failed to update composition epoch: " + e.getMessage(), e);
                }
            });
        } catch (RuntimeException e) {
            throw new WorkflowException(null, "Failed to update composition with epoch data: " + e.getMessage());
        }
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
