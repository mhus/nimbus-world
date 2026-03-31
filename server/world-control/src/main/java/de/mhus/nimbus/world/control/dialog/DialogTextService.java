package de.mhus.nimbus.world.control.dialog;

import de.mhus.nimbus.world.ai.model.AiChat;
import de.mhus.nimbus.world.ai.model.AiChatOptions;
import de.mhus.nimbus.world.ai.model.AiModelService;
import de.mhus.nimbus.world.control.dialog.DialogDtos.*;
import de.mhus.nimbus.world.shared.world.WAnything;
import de.mhus.nimbus.world.shared.world.WAnythingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * AI text generation and caching for dialog nodes.
 * Generates NPC dialog text using AI models, caches multiple versions world-wide.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DialogTextService {

    private static final int DEFAULT_MAX_VERSIONS = 10;
    private static final int DEFAULT_WARM_UP_COUNT = 3;
    private static final String CACHE_COLLECTION = "dialog-cache";

    private final WAnythingService anythingService;
    private final AiModelService aiModelService;

    /**
     * Resolve text for a dialog node. Uses cache if available, generates via AI if not.
     *
     * @param node     the dialog node
     * @param ctx      the dialog context
     * @param language the target language code (e.g., "de", "en")
     * @return the resolved text, or textPrompt as fallback
     */
    public String resolveText(DialogNode node, DialogContext ctx, String language) {
        if (node.textPrompt() == null || node.textPrompt().isBlank()) {
            return "";
        }

        try {
            String contextKey = buildContextKey(node.cacheKeys(), ctx);
            String cacheName = buildCacheName(ctx, contextKey, language);
            String mainWorldId = ctx.getMainWorldId();
            int maxVersions = getMaxVersions(ctx);

            // Try cache lookup
            Optional<WAnything> cacheOpt = anythingService
                    .findByWorldIdAndCollectionAndName(mainWorldId, CACHE_COLLECTION, cacheName);

            if (cacheOpt.isPresent()) {
                var cacheData = cacheOpt.get().getDataAs(TextCacheEntry.class).orElse(null);
                if (cacheData != null && cacheData.versions() != null && !cacheData.versions().isEmpty()) {
                    // Pick random version
                    String text = pickRandom(cacheData.versions());

                    // Async: generate more if below max
                    if (cacheData.versions().size() < maxVersions) {
                        asyncGenerateAndAppend(node, ctx, language, cacheName, mainWorldId);
                    }

                    return text;
                }
            }

            // Cache miss: generate synchronously
            String generated = generateText(node, ctx, language);
            if (generated != null && !generated.isBlank()) {
                // Save to cache
                saveToCache(mainWorldId, cacheName, ctx, contextKey, language, generated);

                // Async: warm up with additional versions
                int warmUpCount = getWarmUpCount(ctx);
                for (int i = 0; i < warmUpCount; i++) {
                    asyncGenerateAndAppend(node, ctx, language, cacheName, mainWorldId);
                }

                return generated;
            }
        } catch (Exception e) {
            log.warn("Failed to resolve AI text for node, falling back to textPrompt: {}", e.getMessage());
        }

        // Fallback
        return node.textPrompt();
    }

    /**
     * Generate text using AI for a dialog node.
     */
    public String generateText(DialogNode node, DialogContext ctx, String language) {
        String modelName = resolveModelName(ctx);
        Optional<AiChat> chatOpt = aiModelService.createChat(modelName, AiChatOptions.builder()
                .systemMessage(buildSystemPrompt(ctx, language))
                .temperature(0.9)
                .maxTokens(0)
                .timeoutSeconds(30)
                .build());

        if (chatOpt.isEmpty()) {
            log.warn("AI model not available: {}", modelName);
            return null;
        }

        AiChat chat = chatOpt.get();
        try {
            String userPrompt = buildUserPrompt(node, ctx);
            return chat.ask(userPrompt);
        } catch (Exception e) {
            log.warn("AI generation failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Check if AI is available for text generation.
     */
    public boolean isAiAvailable(DialogContext ctx) {
        String modelName = resolveModelName(ctx);
        return aiModelService.createChat(modelName).map(AiChat::isAvailable).orElse(false);
    }

    /**
     * Build a context key from cache keys and current state.
     */
    public String buildContextKey(List<String> cacheKeys, DialogContext ctx) {
        if (cacheKeys == null || cacheKeys.isEmpty()) {
            return "default";
        }

        TreeMap<String, String> parts = new TreeMap<>();
        for (String key : cacheKeys) {
            String value = resolveContextValue(key, ctx);
            String bucket = bucketize(key, value, ctx);
            parts.put(key, bucket);
        }

        return parts.entrySet().stream()
                .map(e -> e.getKey() + ":" + e.getValue())
                .collect(Collectors.joining(","));
    }

    // --- Prompt building ---

    private String buildSystemPrompt(DialogContext ctx, String language) {
        var sb = new StringBuilder();
        NpcProfile profile = ctx.getNpcProfile();

        if (profile != null) {
            sb.append("Du bist ").append(ctx.getNpcTitle()).append(".\n");
            if (profile.personality() != null)
                sb.append("Persoenlichkeit: ").append(profile.personality()).append("\n");
            if (profile.background() != null)
                sb.append("Hintergrund: ").append(profile.background()).append("\n");
            if (profile.speechStyle() != null)
                sb.append("Sprechstil: ").append(profile.speechStyle()).append("\n");
        } else {
            sb.append("Du bist ").append(ctx.getNpcTitle()).append(", ein NPC in einer Fantasy-Welt.\n");
        }

        // Situation context
        if (ctx.getActiveSituation() != null && ctx.getActiveSituation().aiContext() != null) {
            sb.append("\nAktuelle Situation: ").append(ctx.getActiveSituation().aiContext()).append("\n");
        }

        // Background situations
        if (ctx.getBackgroundSituations() != null) {
            for (var bg : ctx.getBackgroundSituations()) {
                if (bg.aiContext() != null) {
                    sb.append("Hintergrund: ").append(bg.aiContext()).append("\n");
                }
            }
        }

        // NPC state
        if (ctx.getNpcState() != null && !ctx.getNpcState().isEmpty()) {
            sb.append("\nDein aktueller Zustand: ").append(ctx.getNpcState()).append("\n");
        }

        // Player memory
        List<String> remembers = ctx.getPlayerRemembers();
        if (!remembers.isEmpty()) {
            sb.append("\nDu erinnerst dich an diesen Spieler: ").append(String.join("; ", remembers)).append("\n");
        }
        int convCount = ctx.getConversationCount();
        if (convCount > 0) {
            sb.append("Ihr habt euch schon ").append(convCount).append(" mal unterhalten.\n");
        }

        sb.append("\nSprache: ").append(language != null ? language : "de").append("\n");
        sb.append("Antworte NUR mit dem gesprochenen Text. Keine Regieanweisungen, keine Anfuehrungszeichen.\n");

        return sb.toString();
    }

    private String buildUserPrompt(DialogNode node, DialogContext ctx) {
        return node.textPrompt();
    }

    // --- Cache management ---

    private String buildCacheName(DialogContext ctx, String contextKey, String language) {
        return ctx.getPlaybookName() + "/" + ctx.getActiveSituationName() + "/"
                + ctx.getCurrentNodeId() + "/" + contextKey + "/" + (language != null ? language : "de");
    }

    private void saveToCache(String mainWorldId, String cacheName, DialogContext ctx,
                             String contextKey, String language, String text) {
        try {
            TextCacheVersion version = new TextCacheVersion(text, Instant.now().toString());
            TextCacheEntry entry = new TextCacheEntry(
                    ctx.getPlaybookName(),
                    ctx.getCurrentNodeId(),
                    contextKey,
                    language != null ? language : "de",
                    getMaxVersions(ctx),
                    List.of(version)
            );
            anythingService.create(mainWorldId, CACHE_COLLECTION, cacheName,
                    null, null, "text-cache", entry);
            log.debug("Created cache entry: {}", cacheName);
        } catch (Exception e) {
            log.warn("Failed to save cache entry {}: {}", cacheName, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void asyncGenerateAndAppend(DialogNode node, DialogContext ctx, String language,
                                         String cacheName, String mainWorldId) {
        // Copy relevant context values for async execution
        String modelName = resolveModelName(ctx);
        String systemPrompt = buildSystemPrompt(ctx, language);
        String userPrompt = buildUserPrompt(node, ctx);
        int maxVersions = getMaxVersions(ctx);

        CompletableFuture.runAsync(() -> {
            try {
                Optional<AiChat> chatOpt = aiModelService.createChat(modelName, AiChatOptions.builder()
                        .systemMessage(systemPrompt)
                        .temperature(0.9)
                        .maxTokens(0)
                        .timeoutSeconds(30)
                        .build());

                if (chatOpt.isEmpty()) return;

                String generated = chatOpt.get().ask(userPrompt);
                if (generated == null || generated.isBlank()) return;

                // Append to cache
                anythingService.findByWorldIdAndCollectionAndName(mainWorldId, CACHE_COLLECTION, cacheName)
                        .ifPresent(anything -> {
                            anythingService.update(anything.getId(), a -> {
                                TextCacheEntry existing = a.getDataAs(TextCacheEntry.class).orElse(null);
                                if (existing != null && existing.versions() != null) {
                                    if (existing.versions().size() >= maxVersions) return;
                                    List<TextCacheVersion> versions = new ArrayList<>(existing.versions());
                                    versions.add(new TextCacheVersion(generated, Instant.now().toString()));
                                    a.setData(new TextCacheEntry(
                                            existing.playbookName(), existing.nodeId(),
                                            existing.contextKey(), existing.language(),
                                            existing.maxVersions(), versions
                                    ));
                                }
                            });
                        });

                log.debug("Async cached text for {}", cacheName);
            } catch (Exception e) {
                log.warn("Async cache generation failed for {}: {}", cacheName, e.getMessage());
            }
        });
    }

    private String pickRandom(List<TextCacheVersion> versions) {
        int index = new Random().nextInt(versions.size());
        return versions.get(index).text();
    }

    // --- Context resolution ---

    private String resolveContextValue(String key, DialogContext ctx) {
        if (key.startsWith("npcState.")) {
            String field = key.substring("npcState.".length());
            Object val = ctx.getNpcStateValue(field);
            return val != null ? val.toString() : "none";
        }
        if (key.startsWith("memory.")) {
            String field = key.substring("memory.".length());
            Object val = ctx.getMemoryValue(field);
            return val != null ? val.toString() : "none";
        }
        return "unknown";
    }

    @SuppressWarnings("unchecked")
    private String bucketize(String key, String rawValue, DialogContext ctx) {
        if (ctx.getNpcProfile() == null || ctx.getNpcProfile().cacheConfig() == null) {
            return rawValue;
        }

        Map<String, Object> buckets = ctx.getNpcProfile().cacheConfig().buckets();
        if (buckets == null) return rawValue;

        // Lookup with original key or underscore variant (MongoDB doesn't allow dots in map keys)
        Object bucketDef = buckets.get(key);
        if (bucketDef == null) {
            bucketDef = buckets.get(key.replace('.', '_'));
        }
        if (bucketDef == null) {
            return rawValue;
        }

        // "direct" means use value as-is
        if ("direct".equals(bucketDef)) {
            return rawValue;
        }

        // Range-based buckets: { "low": [0, 2], "mid": [3, 5], "high": [6, 100] }
        if (bucketDef instanceof Map<?, ?> rangeBuckets) {
            try {
                double numValue = Double.parseDouble(rawValue);
                for (var entry : ((Map<String, Object>) rangeBuckets).entrySet()) {
                    if (entry.getValue() instanceof List<?> range && range.size() == 2) {
                        double min = ((Number) range.get(0)).doubleValue();
                        double max = ((Number) range.get(1)).doubleValue();
                        if (numValue >= min && numValue <= max) {
                            return entry.getKey();
                        }
                    }
                }
            } catch (NumberFormatException e) {
                // Non-numeric value, return as-is
            }
        }

        return rawValue;
    }

    // --- Config helpers ---

    private String resolveModelName(DialogContext ctx) {
        if (ctx.getNpcProfile() != null && ctx.getNpcProfile().aiModel() != null) {
            return ctx.getNpcProfile().aiModel();
        }
        return "default:dialog";
    }

    private int getMaxVersions(DialogContext ctx) {
        if (ctx.getNpcProfile() != null && ctx.getNpcProfile().cacheConfig() != null) {
            return ctx.getNpcProfile().cacheConfig().maxVersions();
        }
        return DEFAULT_MAX_VERSIONS;
    }

    private int getWarmUpCount(DialogContext ctx) {
        if (ctx.getNpcProfile() != null && ctx.getNpcProfile().cacheConfig() != null) {
            return ctx.getNpcProfile().cacheConfig().warmUpCount();
        }
        return DEFAULT_WARM_UP_COUNT;
    }

    // --- Cache data records ---

    public record TextCacheEntry(
            String playbookName,
            String nodeId,
            String contextKey,
            String language,
            int maxVersions,
            List<TextCacheVersion> versions
    ) {}

    public record TextCacheVersion(
            String text,
            String createdAt
    ) {}
}
