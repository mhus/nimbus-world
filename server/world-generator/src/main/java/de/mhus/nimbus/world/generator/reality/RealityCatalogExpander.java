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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * B1 — catalog expansion. Takes the (usually small) parsed {@link RealityPlan} and expands it, in
 * memory, into a complete Minecraft-scale catalog: fills the item classes (tier ladder), adds items
 * across categories up to the coverage targets, wires coherence relations (source/recipe) and adds
 * the configured super-items — all themed to the region's vision/lore/style.
 * <p>
 * This is a {@code RealityPlan -> RealityPlan} transformation and does NOT touch the database
 * (Stage B of the pipeline). Existing entries are preserved by the prompt contract.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RealityCatalogExpander {

    private static final String PROMPT_TEMPLATE_PATH = "prompts/reality/expand-catalog.txt";

    private final AiModelService aiModelService;
    private final RealityPlanParser parser; // reuse parseJson

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private String cachedTemplate;

    /**
     * Expand the catalog of a plan. If {@code controls.expandCatalog} is explicitly false, the plan
     * is returned unchanged (no AI call).
     *
     * @param plan the parsed (partial) plan
     * @return the expanded plan, or a failure with errors
     */
    public RealityPlanResult expand(RealityPlan plan) {
        return expand(plan, null);
    }

    /**
     * Expand the catalog of a plan using a specific chat model.
     *
     * @param plan      the parsed (partial) plan
     * @param modelName provider:model to use (e.g. "cortecs:deepseek-v4-pro"); null = default chain
     * @return the expanded plan, or a failure with errors
     */
    public RealityPlanResult expand(RealityPlan plan, String modelName) {
        if (plan == null) {
            return RealityPlanResult.failure("RealityPlan is null");
        }
        if (!expandEnabled(plan)) {
            log.info("Catalog expansion disabled (controls.expandCatalog=false) - keeping plan as-is");
            return RealityPlanResult.success(plan, null);
        }

        Optional<String> templateOpt = loadTemplate();
        if (templateOpt.isEmpty()) {
            return RealityPlanResult.failure("Expansion prompt template not found: " + PROMPT_TEMPLATE_PATH);
        }

        String currentJson;
        try {
            currentJson = MAPPER.writeValueAsString(plan);
        } catch (Exception e) {
            return RealityPlanResult.failure("Failed to serialize plan for expansion: " + e.getMessage());
        }

        Optional<AiChat> chatOpt = createChatModel(modelName);
        if (chatOpt.isEmpty()) {
            return RealityPlanResult.failure("AI chat model not available (configure via AiModelService)");
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("currentPlan", currentJson);
        variables.put("coverageDirective", buildCoverageDirective(plan));
        variables.put("superItemsDirective", buildSuperItemsDirective(plan));
        Prompt prompt = PromptTemplate.from(templateOpt.get()).apply(variables);

        String response;
        try {
            response = chatOpt.get().ask(prompt.text());
        } catch (AiChatException e) {
            log.error("AI chat failed during catalog expansion", e);
            return RealityPlanResult.failure("AI chat error: " + e.getMessage());
        }
        if (Strings.isBlank(response)) {
            return RealityPlanResult.failure("AI returned empty response");
        }

        RealityPlanResult result = parser.parseJson(cleanJsonResponse(response));
        if (result.isSuccessful()) {
            RealityPlan expanded = result.getPlan();
            log.info("Catalog expanded: items {} -> {}, classes {} -> {}",
                    size(plan.getItems()), size(expanded.getItems()),
                    size(plan.getItemClasses()), size(expanded.getItemClasses()));
        }
        return result;
    }

    private boolean expandEnabled(RealityPlan plan) {
        if (plan.getMeta() != null && plan.getMeta().getControls() != null) {
            Boolean flag = plan.getMeta().getControls().getExpandCatalog();
            return flag == null || flag; // default: enabled
        }
        return true;
    }

    /** Build the per-category coverage directive from controls (or a preset-default hint). */
    String buildCoverageDirective(RealityPlan plan) {
        RealityPlan.GenerationControls controls = plan.getMeta() != null ? plan.getMeta().getControls() : null;
        StringBuilder sb = new StringBuilder();
        // Target total: explicit targetItemCount wins, else fall back to maxItems as an upper bound.
        Integer target = null;
        if (controls != null) {
            if (controls.getTargetItemCount() != null && controls.getTargetItemCount() > 0) {
                target = controls.getTargetItemCount();
            } else if (controls.getMaxItems() != null && controls.getMaxItems() > 0) {
                target = controls.getMaxItems();
            }
        }
        boolean hasCoverage = controls != null && controls.getCategoryCoverage() != null
                && !controls.getCategoryCoverage().isEmpty();
        if (target != null) {
            sb.append("Aim for about ").append(target)
                    .append(" items in total — this is a HARD CAP, do not exceed it.\n");
        }
        if (hasCoverage) {
            sb.append("Per-category targets:\n");
            controls.getCategoryCoverage().forEach((cat, n) ->
                    sb.append("- ").append(cat).append(": ~").append(n).append('\n'));
        }
        if (target == null && !hasCoverage) {
            sb.append("No explicit per-category targets given — use the default preset ranges below.");
        }
        return sb.toString().trim();
    }

    /** Build the super-items directive from controls (default: one_up). */
    String buildSuperItemsDirective(RealityPlan plan) {
        List<String> supers = null;
        if (plan.getMeta() != null && plan.getMeta().getControls() != null) {
            supers = plan.getMeta().getControls().getSuperItems();
        }
        if (supers == null || supers.isEmpty()) {
            return "Include the super-item 'one_up'.";
        }
        return "Include these super-items: " + String.join(", ", supers) + ".";
    }

    private Optional<AiChat> createChatModel(String modelName) {
        AiChatOptions options = AiChatOptions.builder()
                .temperature(0.3)     // a bit of variety for creative catalog filling
                .maxTokens(0)         // model maximum for a large plan
                .timeoutSeconds(300)  // full-catalog output is large and slow
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
                log.error("Expansion prompt template not found: {}", PROMPT_TEMPLATE_PATH);
                return Optional.empty();
            }
            cachedTemplate = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return Optional.of(cachedTemplate);
        } catch (IOException e) {
            log.error("Failed to load expansion prompt template", e);
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

    private static int size(List<?> list) {
        return list == null ? 0 : list.size();
    }
}
