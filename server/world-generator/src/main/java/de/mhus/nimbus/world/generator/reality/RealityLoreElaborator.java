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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * B1 (phase 2) — the lore elaborator. Walks the seed's {@link RealityPlan.Chapter} outline and writes
 * each chapter as full deep-lore text at a LOW temperature, feeding the seed plus a rolling summary of
 * previous chapters as context (book-style outline-then-expand). The elaborated chapters are appended
 * to {@code plan.lore}. Pure {@code RealityPlan -> RealityPlan}, no DB writes.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RealityLoreElaborator {

    private static final String PROMPT_TEMPLATE_PATH = "prompts/reality/elaborate-chapter.txt";
    private static final double ELABORATE_TEMPERATURE = 0.3;
    private static final int SUMMARY_CHARS = 300;

    private final AiModelService aiModelService;

    public RealityPlanResult elaborate(RealityPlan plan) {
        return elaborate(plan, null);
    }

    public RealityPlanResult elaborate(RealityPlan plan, String modelName) {
        if (plan == null) {
            return RealityPlanResult.failure("RealityPlan is null");
        }
        List<RealityPlan.Chapter> outline = plan.getOutline();
        if (outline == null || outline.isEmpty()) {
            log.info("No outline to elaborate - keeping plan as-is");
            return RealityPlanResult.success(plan, null);
        }
        Optional<String> templateOpt = RealityAiSupport.loadTemplate(PROMPT_TEMPLATE_PATH);
        if (templateOpt.isEmpty()) {
            return RealityPlanResult.failure("Elaborate prompt template not found: " + PROMPT_TEMPLATE_PATH);
        }
        Optional<AiChat> chatOpt = createChatModel(modelName);
        if (chatOpt.isEmpty()) {
            return RealityPlanResult.failure("AI chat model not available (configure via AiModelService)");
        }
        AiChat chat = chatOpt.get();
        String seedContext = buildSeedContext(plan);

        List<RealityPlan.LoreEntry> lore = new ArrayList<>();
        if (plan.getLore() != null) {
            lore.addAll(plan.getLore()); // keep any seed lore
        }
        StringBuilder rollingSummary = new StringBuilder();
        int elaborated = 0;

        for (RealityPlan.Chapter chapter : outline) {
            if (chapter == null || (Strings.isBlank(chapter.getTitle()) && Strings.isBlank(chapter.getKey()))) {
                continue;
            }
            String title = Strings.isBlank(chapter.getTitle()) ? chapter.getKey() : chapter.getTitle();
            Map<String, Object> vars = new HashMap<>();
            vars.put("seedContext", seedContext);
            vars.put("previousSummary", rollingSummary.length() == 0 ? "(none yet)" : rollingSummary.toString());
            vars.put("chapterTitle", title);
            vars.put("chapterGoal", chapter.getGoal() == null ? "" : chapter.getGoal());
            Prompt prompt = PromptTemplate.from(templateOpt.get()).apply(vars);

            try {
                String text = chat.ask(prompt.text());
                if (Strings.isBlank(text)) {
                    log.warn("Empty chapter text for '{}'", title);
                    continue;
                }
                RealityPlan.LoreEntry entry = new RealityPlan.LoreEntry();
                entry.setTitle(title);
                entry.setKind(Strings.isBlank(chapter.getKind()) ? "history" : chapter.getKind());
                entry.setContent(text.trim());
                lore.add(entry);
                rollingSummary.append("- ").append(title).append(": ").append(truncate(text.trim())).append('\n');
                elaborated++;
            } catch (AiChatException e) {
                log.warn("Failed to elaborate chapter '{}'", title, e);
                // continue with the remaining chapters
            }
        }

        plan.setLore(lore);
        log.info("Elaborated {} of {} chapters into lore", elaborated, outline.size());
        return RealityPlanResult.success(plan, null);
    }

    /** Compact, readable canon brief from the seed (direction + powers + cast + vision). */
    String buildSeedContext(RealityPlan plan) {
        StringBuilder sb = new StringBuilder();
        if (!Strings.isBlank(plan.getVision())) {
            sb.append("Vision: ").append(plan.getVision()).append('\n');
        }
        if (plan.getDirection() != null && !Strings.isBlank(plan.getDirection().getPremise())) {
            sb.append("Direction: ").append(plan.getDirection().getPremise()).append('\n');
        }
        if (plan.getBackgroundPowers() != null) {
            for (RealityPlan.BackgroundPower p : plan.getBackgroundPowers()) {
                if (p == null || Strings.isBlank(p.getName())) {
                    continue;
                }
                sb.append("Background power '").append(p.getName()).append("' (")
                        .append(nz(p.getInfluence())).append("/").append(nz(p.getStatus())).append("): ")
                        .append(nz(p.getGoal())).append('\n');
            }
        }
        if (plan.getCast() != null) {
            for (RealityPlan.CastMember c : plan.getCast()) {
                if (c == null || Strings.isBlank(c.getName())) {
                    continue;
                }
                sb.append("Cast: ").append(c.getName()).append(" — ").append(nz(c.getRole())).append('\n');
            }
        }
        return sb.toString().trim();
    }

    private Optional<AiChat> createChatModel(String modelName) {
        AiChatOptions options = AiChatOptions.builder()
                .temperature(ELABORATE_TEMPERATURE)
                .maxTokens(0)
                .timeoutSeconds(300)
                .build();
        return RealityAiSupport.createChat(aiModelService, modelName, options);
    }

    private static String truncate(String s) {
        String flat = s.replaceAll("\\s+", " ").trim();
        return flat.length() <= SUMMARY_CHARS ? flat : flat.substring(0, SUMMARY_CHARS) + "…";
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

}
