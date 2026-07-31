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
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * C2 — AI balance judge. Assesses a {@link RealityPlan} for balance (price ↔ tier/rarity ↔ utility,
 * rarity distribution, tier progression, super-item power, economy consistency) WITH tolerance, and
 * returns a {@link JudgeVerdict} with concrete, actionable findings that the refine step (B2) can act
 * on. Read-only over the plan; no DB writes.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RealityJudge {

    private static final String PROMPT_TEMPLATE_PATH = "prompts/reality/judge-balance.txt";

    private final AiModelService aiModelService;

    private static final ObjectMapper SERIALIZE_MAPPER = JsonMapper.builder().build();
    private static final ObjectMapper VERDICT_MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
            .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
            .build();

    private String cachedTemplate;

    /**
     * Judge the balance of a plan.
     *
     * @param plan the (expanded) plan to assess
     * @return a verdict; {@link JudgeVerdict#isConclusive()} is false if the AI step failed
     */
    public JudgeVerdict judge(RealityPlan plan) {
        return judge(plan, null);
    }

    /**
     * Judge the balance of a plan using a specific chat model.
     *
     * @param plan      the plan to assess
     * @param modelName provider:model to use (e.g. "cortecs:deepseek-v4-pro"); null = default chain
     * @return a verdict; {@link JudgeVerdict#isConclusive()} is false if the AI step failed
     */
    public JudgeVerdict judge(RealityPlan plan, String modelName) {
        if (plan == null) {
            return JudgeVerdict.failure("RealityPlan is null");
        }

        Optional<String> templateOpt = loadTemplate();
        if (templateOpt.isEmpty()) {
            return JudgeVerdict.failure("Judge prompt template not found: " + PROMPT_TEMPLATE_PATH);
        }

        String planJson;
        try {
            planJson = SERIALIZE_MAPPER.writeValueAsString(plan);
        } catch (Exception e) {
            return JudgeVerdict.failure("Failed to serialize plan for judging: " + e.getMessage());
        }

        Optional<AiChat> chatOpt = createChatModel(modelName);
        if (chatOpt.isEmpty()) {
            return JudgeVerdict.failure("AI chat model not available (configure via AiModelService)");
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("plan", planJson);
        Prompt prompt = PromptTemplate.from(templateOpt.get()).apply(variables);

        String response;
        try {
            response = chatOpt.get().ask(prompt.text());
        } catch (AiChatException e) {
            log.error("AI chat failed during balance judging", e);
            return JudgeVerdict.failure("AI chat error: " + e.getMessage());
        }
        if (Strings.isBlank(response)) {
            return JudgeVerdict.failure("AI returned empty response");
        }

        try {
            JudgeVerdict verdict = VERDICT_MAPPER.readValue(cleanJsonResponse(response), JudgeVerdict.class);
            if (verdict == null) {
                return JudgeVerdict.failure("Judge returned null verdict");
            }
            log.info("Balance verdict: acceptable={}, score={}, findings={} (major={})",
                    verdict.isAcceptable(), verdict.getScore(),
                    verdict.getFindings() == null ? 0 : verdict.getFindings().size(),
                    verdict.majorFindings().size());
            return verdict;
        } catch (Exception e) {
            log.warn("Failed to parse judge verdict JSON", e);
            return JudgeVerdict.failure("Failed to parse verdict: " + e.getMessage());
        }
    }

    private Optional<AiChat> createChatModel(String modelName) {
        AiChatOptions options = AiChatOptions.builder()
                .temperature(0.2) // deterministic, critical assessment
                .maxTokens(0)
                .timeoutSeconds(180)
                .build();
        if (!Strings.isBlank(modelName)) {
            return aiModelService.createChat(modelName, options);
        }
        Optional<AiChat> chat = aiModelService.createChat("default:reality", options);
        if (chat.isPresent()) {
            return chat;
        }
        return aiModelService.createChat("default:chat", options);
    }

    private Optional<String> loadTemplate() {
        if (cachedTemplate != null) {
            return Optional.of(cachedTemplate);
        }
        try {
            ClassPathResource resource = new ClassPathResource(PROMPT_TEMPLATE_PATH);
            if (!resource.exists()) {
                log.error("Judge prompt template not found: {}", PROMPT_TEMPLATE_PATH);
                return Optional.empty();
            }
            cachedTemplate = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return Optional.of(cachedTemplate);
        } catch (IOException e) {
            log.error("Failed to load judge prompt template", e);
            return Optional.empty();
        }
    }

    private String cleanJsonResponse(String response) {
        String cleaned = response.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring("```json".length());
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring("```".length());
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        return cleaned.trim();
    }
}
