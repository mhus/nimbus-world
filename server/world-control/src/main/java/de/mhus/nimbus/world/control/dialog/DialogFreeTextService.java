package de.mhus.nimbus.world.control.dialog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import tools.jackson.databind.ObjectMapper;
import de.mhus.nimbus.world.ai.model.AiChat;
import de.mhus.nimbus.world.ai.model.AiChatOptions;
import de.mhus.nimbus.world.ai.model.AiModelService;
import de.mhus.nimbus.world.control.dialog.DialogDtos.*;
import de.mhus.nimbus.world.shared.world.WAnythingService;
import de.mhus.nimbus.world.shared.world.WLeaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Handles free text input from players during NPC dialogs.
 * Uses AI to interpret player input, match intents, and generate NPC responses.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DialogFreeTextService {

    private static final int MAX_INPUT_LENGTH = 500;
    private static final int DEFAULT_MAX_HISTORY = 10;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AiModelService aiModelService;
    private final WLeaseService leaseService;
    private final DialogEffectExecutor effectExecutor;
    private final DialogService dialogService;
    private final DialogTextService dialogTextService;

    /**
     * Check if free text is enabled for the current context and node.
     */
    public boolean isFreeTextEnabled(DialogContext ctx, DialogNode node) {
        // Node-level override
        if (node.freeTextAllowed() != null) {
            return node.freeTextAllowed();
        }
        // NPC profile level
        if (ctx.getNpcProfile() != null && ctx.getNpcProfile().freeText() != null) {
            Boolean enabled = ctx.getNpcProfile().freeText().enabled();
            if (enabled != null) return enabled && dialogTextService.isAiAvailable(ctx);
        }
        return false;
    }

    /**
     * Process a free text input from the player.
     *
     * @return DialogNodeResponse with the NPC's response
     * @throws DialogService.DialogException if input is invalid or AI unavailable
     */
    public DialogNodeResponse processInput(DialogContext ctx, String playerInput, String currentNodeId) {
        // Validate input
        if (playerInput == null || playerInput.isBlank()) {
            throw new DialogService.DialogException("Empty input");
        }
        if (playerInput.length() > MAX_INPUT_LENGTH) {
            throw new DialogService.DialogException("Input too long (max " + MAX_INPUT_LENGTH + " characters)");
        }

        // Sanitize input (strip HTML)
        String sanitized = playerInput.replaceAll("<[^>]*>", "").trim();

        // Get current node
        DialogNode currentNode = ctx.getActiveSituation().nodes().get(currentNodeId);
        if (currentNode == null) {
            throw new DialogService.DialogException("Current node not found: " + currentNodeId);
        }

        // Check rate limit
        checkRateLimit(ctx);

        // Build prompt and call AI
        String modelName = resolveModelName(ctx);
        Optional<AiChat> chatOpt = aiModelService.createChat(modelName, AiChatOptions.builder()
                .systemMessage(buildFreeTextSystemPrompt(ctx, currentNode))
                .temperature(0.8)
                .maxTokens(getMaxTokens(ctx))
                .timeoutSeconds(30)
                .build());

        if (chatOpt.isEmpty()) {
            throw new DialogService.DialogException("AI not available");
        }

        String userPrompt = buildFreeTextUserPrompt(ctx, sanitized, currentNode);

        try {
            String response = chatOpt.get().ask(userPrompt);
            FreeTextAiResponse aiResponse = parseAiResponse(response);

            // Validate and execute effects
            List<Effect> allowedEffects = filterAllowedEffects(aiResponse.effects(), ctx);
            effectExecutor.executeAll(allowedEffects, ctx);

            // Update history
            updateHistory(ctx, sanitized, aiResponse.npcText());

            // Increment request counter
            incrementRequestCount(ctx);

            // Check if intent matched
            if (aiResponse.matchedIntent() != null && !aiResponse.matchedIntent().isBlank()) {
                return handleIntentMatch(ctx, currentNode, aiResponse);
            }

            // No intent match: stay on current node, return AI text with same options
            return buildFreeTextResponse(ctx, currentNode, currentNodeId, aiResponse.npcText());

        } catch (DialogService.DialogException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Free text AI processing failed: {}", e.getMessage());
            throw new DialogService.DialogException("Failed to process input");
        }
    }

    // --- AI prompt building ---

    private String buildFreeTextSystemPrompt(DialogContext ctx, DialogNode currentNode) {
        var sb = new StringBuilder();
        NpcProfile profile = ctx.getNpcProfile();

        sb.append("Du bist ").append(ctx.getNpcTitle()).append(" in einer Fantasy-Welt.\n");

        if (profile != null) {
            if (profile.personality() != null)
                sb.append("Persoenlichkeit: ").append(profile.personality()).append("\n");
            if (profile.background() != null)
                sb.append("Hintergrund: ").append(profile.background()).append("\n");
            if (profile.speechStyle() != null)
                sb.append("Sprechstil: ").append(profile.speechStyle()).append("\n");
        }

        // Situation context
        if (ctx.getActiveSituation() != null && ctx.getActiveSituation().aiContext() != null) {
            sb.append("\nSituation: ").append(ctx.getActiveSituation().aiContext()).append("\n");
        }

        // NPC state
        if (ctx.getNpcState() != null && !ctx.getNpcState().isEmpty()) {
            sb.append("Dein Zustand: ").append(ctx.getNpcState()).append("\n");
        }

        // Player memory
        List<String> remembers = ctx.getPlayerRemembers();
        if (!remembers.isEmpty()) {
            sb.append("Du erinnerst dich: ").append(String.join("; ", remembers)).append("\n");
        }

        // Boundaries
        if (profile != null && profile.freeText() != null) {
            if (!profile.freeText().boundaries().isEmpty()) {
                sb.append("\nGRENZEN:\n");
                for (String boundary : profile.freeText().boundaries()) {
                    sb.append("- ").append(boundary).append("\n");
                }
            }
            if (!profile.freeText().forbiddenTopics().isEmpty()) {
                sb.append("Verbotene Themen: ").append(
                        String.join(", ", profile.freeText().forbiddenTopics())).append("\n");
            }
        }

        // Available intents
        sb.append("\nVERFUEGBARE OPTIONEN (Intents):\n");
        for (DialogOption opt : currentNode.options()) {
            if (opt.intent() != null) {
                sb.append("- \"").append(opt.intent()).append("\" -> ").append(opt.text());
                if (opt.next() == null) sb.append(" (beendet Dialog)");
                sb.append("\n");
            }
        }

        sb.append("""

                AUFGABE:
                Antworte als JSON mit genau diesen Feldern:
                {
                  "npcText": "Deine Antwort im Charakter",
                  "matchedIntent": "intent_name oder null wenn kein Intent passt",
                  "effects": [{"type": "addMemory", "text": "..."}],
                  "reasoning": "Kurze interne Erklaerung"
                }
                Nur erlaubte Effect-Typen: addMemory, setMemory.
                matchedIntent MUSS exakt einem der oben gelisteten Intents entsprechen oder null sein.
                """);

        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String buildFreeTextUserPrompt(DialogContext ctx, String playerInput, DialogNode currentNode) {
        var sb = new StringBuilder();

        // Conversation history
        Map<String, Object> progressData = ctx.getDialogLease().getLeaseData();
        Object historyObj = progressData != null ? progressData.get("freeTextHistory") : null;
        if (historyObj instanceof List<?> history && !history.isEmpty()) {
            sb.append("Bisheriger Verlauf:\n");
            for (Object entry : history) {
                if (entry instanceof Map<?, ?> map) {
                    sb.append(map.get("role")).append(": ").append(map.get("text")).append("\n");
                }
            }
            sb.append("\n");
        }

        sb.append("Der Spieler sagt: \"").append(playerInput).append("\"");
        return sb.toString();
    }

    // --- Response parsing ---

    @JsonIgnoreProperties(ignoreUnknown = true)
    record FreeTextAiResponse(
            String npcText,
            String matchedIntent,
            List<Effect> effects,
            String reasoning
    ) {
        FreeTextAiResponse {
            if (effects == null) effects = List.of();
        }
    }

    private FreeTextAiResponse parseAiResponse(String rawResponse) {
        try {
            // Try to extract JSON from response (AI might wrap in markdown code blocks)
            String json = rawResponse.trim();
            if (json.startsWith("```")) {
                json = json.replaceAll("```json\\s*", "").replaceAll("```\\s*$", "").trim();
            }
            return OBJECT_MAPPER.readValue(json, FreeTextAiResponse.class);
        } catch (Exception e) {
            // If JSON parsing fails, treat the whole response as NPC text
            log.warn("Failed to parse AI JSON response, using raw text: {}", e.getMessage());
            return new FreeTextAiResponse(rawResponse, null, List.of(), "JSON parse failed");
        }
    }

    // --- Intent matching ---

    private DialogNodeResponse handleIntentMatch(DialogContext ctx, DialogNode currentNode,
                                                   FreeTextAiResponse aiResponse) {
        String matchedIntent = aiResponse.matchedIntent();

        // Find the option with matching intent
        for (int i = 0; i < currentNode.options().size(); i++) {
            DialogOption opt = currentNode.options().get(i);
            if (matchedIntent.equals(opt.intent())) {
                // Clear history on node transition
                clearHistory(ctx);

                // Advance dialog via the matched option
                if (opt.next() == null) {
                    dialogService.closeDialog(ctx);
                    return new DialogNodeResponse(
                            ctx.getDialogLease().getLeaseId(),
                            ctx.getNpcTitle(), ctx.getNpcPortrait(),
                            aiResponse.npcText(), List.of(), false, true, null,
                            ctx.getNavigate()
                    );
                }

                // Execute target node effects
                DialogNode targetNode = ctx.getActiveSituation().nodes().get(opt.next());
                if (targetNode != null) {
                    effectExecutor.executeAll(targetNode.effects(), ctx);
                }

                leaseService.setLeaseDataValue(
                        ctx.getDialogLease().getLeaseId(), "currentNode", opt.next());

                // Return the AI-generated text for the transition, then evaluate new node
                DialogNodeResponse nextNode = dialogService.evaluateNode(ctx, opt.next());
                // Use AI-generated text for the response instead of cached/generated node text
                return new DialogNodeResponse(
                        nextNode.progressId(), nextNode.npcTitle(), nextNode.npcPortrait(),
                        aiResponse.npcText(), nextNode.options(), nextNode.freeTextEnabled(), nextNode.finished(),
                        nextNode.voice(), nextNode.navigate()
                );
            }
        }

        // Intent didn't match any option: treat as free response
        log.warn("AI returned intent '{}' that doesn't match any option", matchedIntent);
        return buildFreeTextResponse(ctx, currentNode, ctx.getCurrentNodeId(), aiResponse.npcText());
    }

    private DialogNodeResponse buildFreeTextResponse(DialogContext ctx, DialogNode currentNode,
                                                       String nodeId, String npcText) {
        // Same options as current node (re-evaluate conditions)
        List<OptionView> options = new ArrayList<>();
        for (int i = 0; i < currentNode.options().size(); i++) {
            DialogOption opt = currentNode.options().get(i);
            options.add(new OptionView(i, opt.text()));
        }

        boolean freeTextEnabled = isFreeTextEnabled(ctx, currentNode);

        return new DialogNodeResponse(
                ctx.getDialogLease().getLeaseId(),
                ctx.getNpcTitle(), ctx.getNpcPortrait(),
                npcText, options, freeTextEnabled, false,
                dialogService.buildVoiceInfo(ctx),
                ctx.getNavigate()
        );
    }

    // --- History management ---

    @SuppressWarnings("unchecked")
    private void updateHistory(DialogContext ctx, String playerInput, String npcResponse) {
        Map<String, Object> data = ctx.getDialogLease().getLeaseData();
        List<Map<String, String>> history = data != null && data.get("freeTextHistory") instanceof List<?> list
                ? new ArrayList<>((List<Map<String, String>>) (List<?>) list)
                : new ArrayList<>();

        history.add(Map.of("role", "player", "text", playerInput));
        if (npcResponse != null) {
            history.add(Map.of("role", "npc", "text", npcResponse));
        }

        // Trim to max history
        int maxHistory = DEFAULT_MAX_HISTORY;
        if (ctx.getNpcProfile() != null && ctx.getNpcProfile().freeText() != null
                && ctx.getNpcProfile().freeText().maxTokens() != null) {
            // maxHistory could come from world config; using default for now
        }
        while (history.size() > maxHistory * 2) { // *2 because each exchange = 2 entries
            history.removeFirst();
        }

        leaseService.setLeaseDataValue(
                ctx.getDialogLease().getLeaseId(), "freeTextHistory", history);
    }

    private void clearHistory(DialogContext ctx) {
        leaseService.setLeaseDataValue(
                ctx.getDialogLease().getLeaseId(), "freeTextHistory", List.of());
    }

    // --- Rate limiting ---

    private void checkRateLimit(DialogContext ctx) {
        Map<String, Object> data = ctx.getDialogLease().getLeaseData();
        Object countObj = data != null ? data.get("freeTextRequestCount") : null;
        int count = countObj instanceof Number n ? n.intValue() : 0;

        // Default max per dialog: 50
        int maxPerDialog = 50;
        if (count >= maxPerDialog) {
            throw new DialogService.DialogException("Free text rate limit exceeded");
        }
    }

    private void incrementRequestCount(DialogContext ctx) {
        leaseService.incLeaseDataValue(
                ctx.getDialogLease().getLeaseId(), "freeTextRequestCount", 1);
    }

    // --- Effects filtering ---

    private List<Effect> filterAllowedEffects(List<Effect> effects, DialogContext ctx) {
        if (effects == null || effects.isEmpty()) return List.of();

        Set<String> allowed = Set.of("addMemory", "setMemory"); // defaults
        if (ctx.getNpcProfile() != null && ctx.getNpcProfile().freeText() != null
                && !ctx.getNpcProfile().freeText().allowedEffects().isEmpty()) {
            allowed = new HashSet<>(ctx.getNpcProfile().freeText().allowedEffects());
        }

        Set<String> finalAllowed = allowed;
        return effects.stream()
                .filter(e -> finalAllowed.contains(e.type()))
                .collect(Collectors.toList());
    }

    // --- Config helpers ---

    private String resolveModelName(DialogContext ctx) {
        if (ctx.getNpcProfile() != null && ctx.getNpcProfile().freeText() != null
                && ctx.getNpcProfile().freeText().aiModel() != null) {
            return ctx.getNpcProfile().freeText().aiModel();
        }
        if (ctx.getNpcProfile() != null && ctx.getNpcProfile().aiModel() != null) {
            return ctx.getNpcProfile().aiModel();
        }
        return "default:dialog";
    }

    private int getMaxTokens(DialogContext ctx) {
        if (ctx.getNpcProfile() != null && ctx.getNpcProfile().freeText() != null
                && ctx.getNpcProfile().freeText().maxTokens() != null) {
            return ctx.getNpcProfile().freeText().maxTokens();
        }
        return 300;
    }
}
