package de.mhus.nimbus.world.generator.reality;

import de.mhus.nimbus.world.ai.model.AiChat;
import de.mhus.nimbus.world.ai.model.AiChatException;
import de.mhus.nimbus.world.ai.model.AiChatOptions;
import de.mhus.nimbus.world.ai.model.AiModelService;
import dev.langchain4j.model.input.Prompt;
import dev.langchain4j.model.input.PromptTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * B0 (phase 1) — the creative SEED generator. Turns a short instruction into a distinctive seed
 * (core direction, background powers, cast, style and a chapter outline) using a HIGH temperature so
 * regions diverge and surprise. The deep lore is elaborated later (phase 2) and the mechanical
 * catalog is derived from that lore (lore-first).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RealitySeedGenerator {

    private static final String PROMPT_TEMPLATE_PATH = "prompts/reality/seed.txt";
    /** High temperature: maximum divergence so two similar instructions yield different regions. */
    private static final double SEED_TEMPERATURE = 1.0;

    private final AiModelService aiModelService;
    private final RealityPlanParser parser; // reuse the lenient JSON -> RealityPlan mapping

    public RealityPlanResult generate(String instruction) {
        return generate(instruction, null);
    }

    /**
     * @param instruction the short reality instruction (markdown)
     * @param modelName   provider:model (null = default chain)
     * @return the seed as a RealityPlan (direction/backgroundPowers/cast/outline + meta/vision/style)
     */
    public RealityPlanResult generate(String instruction, String modelName) {
        if (Strings.isBlank(instruction)) {
            return RealityPlanResult.failure("Instruction cannot be empty");
        }
        Optional<String> templateOpt = RealityAiSupport.loadTemplate(PROMPT_TEMPLATE_PATH);
        if (templateOpt.isEmpty()) {
            return RealityPlanResult.failure("Seed prompt template not found: " + PROMPT_TEMPLATE_PATH);
        }
        Optional<AiChat> chatOpt = createChatModel(modelName);
        if (chatOpt.isEmpty()) {
            return RealityPlanResult.failure("AI chat model not available (configure via AiModelService)");
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("instruction", instruction);
        Prompt prompt = PromptTemplate.from(templateOpt.get()).apply(variables);

        String response;
        try {
            response = chatOpt.get().ask(prompt.text());
        } catch (AiChatException e) {
            log.error("AI chat failed during seed generation", e);
            return RealityPlanResult.failure("AI chat error: " + e.getMessage());
        }
        if (Strings.isBlank(response)) {
            return RealityPlanResult.failure("AI returned empty seed");
        }

        RealityPlanResult result = parser.parseJson(RealityAiSupport.extractJson(response));
        if (result.isSuccessful()) {
            RealityPlan p = result.getPlan();
            log.info("Seed generated: region={}, powers={}, outline chapters={}",
                    p.getMeta() != null ? p.getMeta().getRegionId() : "?",
                    p.getBackgroundPowers() == null ? 0 : p.getBackgroundPowers().size(),
                    p.getOutline() == null ? 0 : p.getOutline().size());
        }
        return result;
    }

    private Optional<AiChat> createChatModel(String modelName) {
        AiChatOptions options = AiChatOptions.builder()
                .temperature(SEED_TEMPERATURE)
                .maxTokens(0)
                .timeoutSeconds(180)
                .build();
        return RealityAiSupport.createChat(aiModelService, modelName, options);
    }
}
