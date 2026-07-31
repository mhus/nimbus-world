package de.mhus.nimbus.world.generator.reality;

import de.mhus.nimbus.world.ai.model.AiChat;
import de.mhus.nimbus.world.ai.model.AiChatOptions;
import de.mhus.nimbus.world.ai.model.AiModelService;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared AI plumbing for the reality generator stages. Extracted so the identical chat-model
 * fallback, prompt-template loading and JSON fence stripping live in one place instead of being
 * copy-pasted into every stage service.
 */
@Slf4j
public final class RealityAiSupport {

    /** Immutable classpath templates -> a process-wide cache is safe and avoids re-reads. */
    private static final Map<String, String> TEMPLATE_CACHE = new ConcurrentHashMap<>();

    private RealityAiSupport() {
    }

    /**
     * Resolve a chat model with the standard reality fallback chain: an explicit
     * {@code provider:model} wins, otherwise the {@code default:reality} mapping, and finally
     * {@code default:chat}.
     */
    public static Optional<AiChat> createChat(AiModelService aiModelService, String modelName, AiChatOptions options) {
        if (!Strings.isBlank(modelName)) {
            return aiModelService.createChat(modelName, options);
        }
        Optional<AiChat> chat = aiModelService.createChat("default:reality", options);
        if (chat.isPresent()) {
            return chat;
        }
        log.debug("No 'default:reality' mapping, falling back to 'default:chat'");
        return aiModelService.createChat("default:chat", options);
    }

    /** Load a classpath prompt template (cached). Empty if it does not exist or cannot be read. */
    public static Optional<String> loadTemplate(String path) {
        String cached = TEMPLATE_CACHE.get(path);
        if (cached != null) {
            return Optional.of(cached);
        }
        try {
            ClassPathResource resource = new ClassPathResource(path);
            if (!resource.exists()) {
                log.error("Prompt template not found: {}", path);
                return Optional.empty();
            }
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            TEMPLATE_CACHE.put(path, content);
            return Optional.of(content);
        } catch (IOException e) {
            log.error("Failed to load prompt template {}", path, e);
            return Optional.empty();
        }
    }

    /**
     * Extract the JSON payload from a chat response. If the response contains a fenced code block
     * (<code>```json ... ```</code> or <code>``` ... ```</code>), its content is returned; otherwise
     * the trimmed response is returned unchanged. Unlike a plain prefix/suffix trim, this tolerates
     * leading or trailing prose around the block (e.g. "Here is the plan:\n```json\n...\n```").
     */
    public static String extractJson(String response) {
        if (response == null) {
            return "";
        }
        String s = response.trim();
        int open = s.indexOf("```");
        if (open < 0) {
            return s;
        }
        int cursor = open + 3;
        // Skip an optional language tag directly after the opening fence (e.g. "json").
        while (cursor < s.length() && Character.isLetterOrDigit(s.charAt(cursor))) {
            cursor++;
        }
        // Skip the line break that separates the fence line from the body, if present.
        if (cursor < s.length() && s.charAt(cursor) == '\r') {
            cursor++;
        }
        if (cursor < s.length() && s.charAt(cursor) == '\n') {
            cursor++;
        }
        int close = s.indexOf("```", cursor);
        return (close < 0 ? s.substring(cursor) : s.substring(cursor, close)).trim();
    }
}
