package de.mhus.nimbus.world.generator.reality;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.ai.model.AiChat;
import de.mhus.nimbus.world.ai.model.AiChatException;
import de.mhus.nimbus.world.ai.model.AiChatOptions;
import de.mhus.nimbus.world.ai.model.AiModelService;
import de.mhus.nimbus.world.shared.world.WDocument;
import de.mhus.nimbus.world.shared.world.WDocumentService;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Phase 0 of the Reality Workflow: turns a free-text Reality Instruction Document into a typed
 * {@link RealityPlan}. Same approach as the Genesis {@code TranslatorService} (instruction text →
 * AI → JSON → typed object), but the target structure is {@link RealityPlan}.
 * <p>
 * Input document: {@code w_documents}, collection {@link #INSTRUCTIONS_COLLECTION},
 * {@code worldId = @region:<id>}. The parsed plan can be persisted as JSON into
 * {@link #PLAN_COLLECTION} via {@link #savePlan}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RealityPlanParser {

    public static final String INSTRUCTIONS_COLLECTION = "reality_instructions";
    public static final String PLAN_COLLECTION = "reality_plan";

    private static final String PROMPT_TEMPLATE_PATH = "prompts/reality/parse-instruction.txt";

    private final AiModelService aiModelService;
    private final WDocumentService documentService;

    // Lenient mapper: AI JSON may contain comments/trailing commas and extra fields.
    private static final ObjectMapper PLAN_MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
            .build();

    /**
     * Load the instruction document of a region and parse it into a {@link RealityPlan}.
     *
     * @param regionId          the region collection world id (e.g. {@code @region:duskmoor})
     * @param instructionsDocId documentId of the {@code reality_instructions} document
     * @return parse result with the plan or errors
     */
    public RealityPlanResult parseFromDocument(WorldId regionId, String instructionsDocId) {
        Optional<WDocument> docOpt = documentService.findByDocumentId(regionId, instructionsDocId);
        if (docOpt.isEmpty()) {
            return RealityPlanResult.failure(
                    "Instruction document not found: worldId=" + regionId.getId()
                            + ", documentId=" + instructionsDocId);
        }
        String content = docOpt.get().getContent();
        if (Strings.isBlank(content)) {
            return RealityPlanResult.failure("Instruction document is empty: " + instructionsDocId);
        }
        return parse(content, null);
    }

    /**
     * Parse an instruction text into a {@link RealityPlan} using the AI model.
     *
     * @param instruction   the free-text reality instruction (markdown)
     * @param previousError error feedback from a previous attempt (optional, for a retry)
     * @return parse result with the plan or errors
     */
    public RealityPlanResult parse(String instruction, String previousError) {
        return parse(instruction, previousError, null);
    }

    /**
     * Parse an instruction text using a specific chat model.
     *
     * @param instruction   the free-text reality instruction (markdown)
     * @param previousError error feedback from a previous attempt (optional)
     * @param modelName     provider:model to use (e.g. "gemini:gemini-2.5-flash"); null = default chain
     */
    public RealityPlanResult parse(String instruction, String previousError, String modelName) {
        if (Strings.isBlank(instruction)) {
            return RealityPlanResult.failure("Instruction cannot be empty");
        }

        Optional<String> templateOpt = RealityAiSupport.loadTemplate(PROMPT_TEMPLATE_PATH);
        if (templateOpt.isEmpty()) {
            return RealityPlanResult.failure("Prompt template could not be loaded: " + PROMPT_TEMPLATE_PATH);
        }

        Optional<AiChat> chatOpt = createChatModel(modelName);
        if (chatOpt.isEmpty()) {
            return RealityPlanResult.failure("AI chat model not available (configure via AiModelService)");
        }

        String previousErrorSection = "";
        if (previousError != null && !previousError.isBlank()) {
            previousErrorSection = """
                    ## Previous Attempt

                    The previous attempt produced the following error. Fix it in your new output:

                    %s
                    """.formatted(previousError);
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("instruction", instruction);
        variables.put("previousErrorSection", previousErrorSection);

        Prompt prompt = PromptTemplate.from(templateOpt.get()).apply(variables);

        String response;
        try {
            response = chatOpt.get().ask(prompt.text());
        } catch (AiChatException e) {
            log.error("AI chat failed during reality plan parsing", e);
            return RealityPlanResult.failure("AI chat error: " + e.getMessage());
        }
        if (Strings.isBlank(response)) {
            return RealityPlanResult.failure("AI returned empty response");
        }

        return parseJson(RealityAiSupport.extractJson(response));
    }

    /**
     * Parse a (already cleaned) JSON string into a {@link RealityPlan}. Public so the JSON→object
     * mapping can be tested without hitting the AI model.
     *
     * @param json the plan JSON
     * @return parse result with the plan or a parse error (the JSON is kept for debugging)
     */
    public RealityPlanResult parseJson(String json) {
        if (Strings.isBlank(json)) {
            return RealityPlanResult.failure("Empty JSON");
        }
        try {
            RealityPlan plan = PLAN_MAPPER.readValue(json, RealityPlan.class);
            if (plan == null) {
                return RealityPlanResult.failure("Parser returned null plan", json);
            }
            log.info("Parsed RealityPlan: region={}, items={}, creatures={}, lore={}",
                    plan.getMeta() != null ? plan.getMeta().getRegionId() : "?",
                    plan.getItems() != null ? plan.getItems().size() : 0,
                    plan.getCreatures() != null ? plan.getCreatures().size() : 0,
                    plan.getLore() != null ? plan.getLore().size() : 0);
            return RealityPlanResult.success(plan, json);
        } catch (Exception e) {
            log.warn("Failed to parse RealityPlan JSON", e);
            return RealityPlanResult.failure(
                    "Failed to parse RealityPlan JSON: " + e.getMessage(), json);
        }
    }

    /**
     * Persist a parsed plan as a {@code reality_plan} JSON document in the region.
     *
     * @param regionId the region collection world id
     * @param json     the plan JSON to store
     * @return the documentId of the stored plan
     */
    public String savePlan(WorldId regionId, String json) {
        String documentId = UUID.randomUUID().toString();
        documentService.save(regionId, PLAN_COLLECTION, documentId, doc -> {
            doc.setName("reality-plan");
            doc.setTitle("Reality Plan");
            doc.setContent(json);
            doc.setFormat("json");
            doc.setType("reality_plan");
        });
        log.info("Saved reality plan: worldId={}, documentId={}", regionId.getId(), documentId);
        return documentId;
    }

    private Optional<AiChat> createChatModel(String modelName) {
        AiChatOptions options = AiChatOptions.builder()
                .temperature(0.2) // low temperature for deterministic, structured output
                .maxTokens(0)     // model maximum for large JSON
                .timeoutSeconds(180)
                .build();
        return RealityAiSupport.createChat(aiModelService, modelName, options);
    }
}
