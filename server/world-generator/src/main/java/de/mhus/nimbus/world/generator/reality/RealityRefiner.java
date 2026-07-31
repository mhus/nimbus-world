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
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * B2 — refine loop. Repeatedly evaluates a {@link RealityPlan} with the mechanical validator (C1)
 * and the AI judge (C2), and asks the AI to revise the plan using the findings as feedback, until it
 * is valid (no C1 errors) and balance-accepted (C2), or the iteration limit is reached. Pure
 * in-memory {@code RealityPlan -> RealityPlan}; no DB writes.
 * <p>
 * The chat model (judge + revise) is selectable via {@link RefineOptions#getModelName()} so the same
 * loop can be run with different providers (e.g. Gemini vs. cortecs/DeepSeek) for comparison.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RealityRefiner {

    private static final String PROMPT_TEMPLATE_PATH = "prompts/reality/refine-plan.txt";
    private static final int MAX_WARNINGS_IN_FEEDBACK = 25;

    private final AiModelService aiModelService;
    private final RealityPlanParser parser;
    private final RealityValidator validator;
    private final RealityJudge judge;

    private static final ObjectMapper SERIALIZE_MAPPER = JsonMapper.builder().build();

    private String cachedTemplate;

    public RefineResult refine(RealityPlan plan) {
        return refine(plan, RefineOptions.defaults());
    }

    /**
     * Run the refine loop.
     *
     * @param plan    the (expanded) plan to refine
     * @param options loop options (model, max iterations, judge on/off)
     * @return the refined plan plus convergence info and a trace
     */
    public RefineResult refine(RealityPlan plan, RefineOptions options) {
        List<String> log = new ArrayList<>();
        if (plan == null) {
            return RefineResult.builder().converged(false).log(List.of("plan is null")).build();
        }
        int maxIterations = Math.max(1, options.getMaxIterations());

        RealityPlan current = plan;
        ValidationReport report = validator.validate(current);
        JudgeVerdict verdict = options.isUseJudge() ? judge.judge(current, options.getModelName()) : null;
        log.add(describe(0, report, verdict));

        int iterations = 0;
        while (iterations < maxIterations && !isConverged(report, verdict)) {
            String feedback = buildFeedback(report, verdict);
            Optional<RealityPlan> revised = revise(current, feedback, options.getModelName());
            if (revised.isEmpty()) {
                log.add("revise step failed - stopping");
                break;
            }
            current = revised.get();
            iterations++;
            report = validator.validate(current);
            verdict = options.isUseJudge() ? judge.judge(current, options.getModelName()) : null;
            log.add(describe(iterations, report, verdict));
        }

        boolean converged = isConverged(report, verdict);
        log.add(converged ? "converged" : "not converged (limit reached or revise failed)");
        return RefineResult.builder()
                .plan(current)
                .iterations(iterations)
                .converged(converged)
                .finalReport(report)
                .finalVerdict(verdict)
                .log(log)
                .build();
    }

    /** Converged = no structural errors AND balance accepted (or judge inconclusive/disabled). */
    private boolean isConverged(ValidationReport report, JudgeVerdict verdict) {
        boolean structural = report != null && report.isValid();
        boolean balance = verdict == null || !verdict.isConclusive() || verdict.isAcceptable();
        return structural && balance;
    }

    private String buildFeedback(ValidationReport report, JudgeVerdict verdict) {
        StringBuilder sb = new StringBuilder();
        if (report != null && !report.errors().isEmpty()) {
            sb.append("Structural errors (MUST fix):\n");
            for (ValidationIssue e : report.errors()) {
                sb.append("- [").append(e.getCode()).append("] ").append(e.getMessage()).append('\n');
            }
        }
        if (report != null && !report.warnings().isEmpty()) {
            sb.append("Warnings (fix if straightforward):\n");
            int n = 0;
            for (ValidationIssue w : report.warnings()) {
                if (n++ >= MAX_WARNINGS_IN_FEEDBACK) {
                    sb.append("- … (").append(report.warnings().size() - MAX_WARNINGS_IN_FEEDBACK)
                            .append(" more warnings)\n");
                    break;
                }
                sb.append("- ").append(w.getMessage()).append('\n');
            }
        }
        if (verdict != null && verdict.isConclusive() && verdict.getFindings() != null) {
            sb.append("Balance findings:\n");
            for (JudgeFinding f : verdict.getFindings()) {
                sb.append("- [").append(f.getSeverity()).append("] ").append(f.getRef())
                        .append(": ").append(f.getIssue()).append(" -> ").append(f.getSuggestion()).append('\n');
            }
        }
        if (sb.length() == 0) {
            sb.append("No specific issues; improve overall coherence and balance.");
        }
        return sb.toString();
    }

    private Optional<RealityPlan> revise(RealityPlan current, String feedback, String modelName) {
        Optional<String> templateOpt = loadTemplate();
        if (templateOpt.isEmpty()) {
            log.error("Refine prompt template not found: {}", PROMPT_TEMPLATE_PATH);
            return Optional.empty();
        }
        String planJson;
        try {
            planJson = SERIALIZE_MAPPER.writeValueAsString(current);
        } catch (Exception e) {
            log.error("Failed to serialize plan for refine", e);
            return Optional.empty();
        }
        Optional<AiChat> chatOpt = createChatModel(modelName);
        if (chatOpt.isEmpty()) {
            log.warn("No chat model available for refine (model={})", modelName);
            return Optional.empty();
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("issues", feedback);
        variables.put("plan", planJson);
        Prompt prompt = PromptTemplate.from(templateOpt.get()).apply(variables);

        String response;
        try {
            response = chatOpt.get().ask(prompt.text());
        } catch (AiChatException e) {
            log.error("AI chat failed during refine", e);
            return Optional.empty();
        }
        if (Strings.isBlank(response)) {
            return Optional.empty();
        }
        RealityPlanResult result = parser.parseJson(cleanJsonResponse(response));
        return result.isSuccessful() ? Optional.of(result.getPlan()) : Optional.empty();
    }

    private Optional<AiChat> createChatModel(String modelName) {
        AiChatOptions options = AiChatOptions.builder()
                .temperature(0.2)
                .maxTokens(0)
                .timeoutSeconds(300) // revise emits the full plan
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

    private String describe(int iteration, ValidationReport report, JudgeVerdict verdict) {
        StringBuilder sb = new StringBuilder("iter ").append(iteration).append(": ");
        sb.append(report == null ? "no report"
                : report.errors().size() + " errors, " + report.warnings().size() + " warnings");
        if (verdict != null) {
            sb.append(verdict.isConclusive()
                    ? "; judge score=" + verdict.getScore() + " acceptable=" + verdict.isAcceptable()
                    : "; judge inconclusive");
        }
        return sb.toString();
    }

    private Optional<String> loadTemplate() {
        if (cachedTemplate != null) {
            return Optional.of(cachedTemplate);
        }
        try {
            ClassPathResource resource = new ClassPathResource(PROMPT_TEMPLATE_PATH);
            if (!resource.exists()) {
                return Optional.empty();
            }
            cachedTemplate = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return Optional.of(cachedTemplate);
        } catch (IOException e) {
            log.error("Failed to load refine prompt template", e);
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
