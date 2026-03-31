package de.mhus.nimbus.world.ai.model.gemini;

import de.mhus.nimbus.world.ai.model.AiChat;
import de.mhus.nimbus.world.ai.model.AiChatOptions;
import de.mhus.nimbus.world.ai.model.LangchainModel;
import de.mhus.nimbus.world.ai.model.SimpleRateLimiter;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Google Gemini implementation of LangchainModel.
 * Supports Gemini models (gemini-pro, gemini-pro-vision, etc.)
 * Includes rate limiting for Flash models to respect API quotas.
 * Rate limiter is shared across all chats from this provider instance.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GeminiLangchainModel implements LangchainModel {

    private static final String PROVIDER_NAME = "gemini";

    private final GeminiSettings settings;

    // Global rate limiter shared across all Flash model chats
    private SimpleRateLimiter flashRateLimiter;

    @Override
    public String getName() {
        return PROVIDER_NAME;
    }

    @Override
    public Optional<ChatModel> createChatModel(String modelName, AiChatOptions options) {
        if (!isAvailable()) {
            return Optional.empty();
        }
        try {
            AiChatOptions adjustedOptions = validateAndAdjustOptions(modelName, options);
            ChatModel chatModel = GoogleAiGeminiChatModel.builder()
                    .apiKey(settings.getApiKey())
                    .modelName(modelName)
                    .temperature(adjustedOptions.getTemperature())
                    .maxOutputTokens(adjustedOptions.getMaxTokens())
                    .timeout(Duration.ofSeconds(adjustedOptions.getTimeoutSeconds()))
                    .logRequestsAndResponses(adjustedOptions.getLogRequests())
                    .build();
            return Optional.of(chatModel);
        } catch (Exception e) {
            log.error("Failed to create ChatModel: model={}", modelName, e);
            return Optional.empty();
        }
    }

    @Override
    public Optional<AiChat> createAiChat(String modelName, AiChatOptions options) {
        if (!isAvailable()) {
            log.warn("Gemini API key not configured");
            return Optional.empty();
        }

        try {
            // Validate and adjust options for this model
            AiChatOptions adjustedOptions = validateAndAdjustOptions(modelName, options);

            log.debug("Creating Gemini ChatModel: model={}, maxOutputTokens={}, temperature={}, timeout={}s",
                    modelName, adjustedOptions.getMaxTokens(), adjustedOptions.getTemperature(),
                    adjustedOptions.getTimeoutSeconds());

            ChatModel chatModel = GoogleAiGeminiChatModel.builder()
                    .apiKey(settings.getApiKey())
                    .modelName(modelName)
                    .temperature(adjustedOptions.getTemperature())
                    .maxOutputTokens(adjustedOptions.getMaxTokens())
                    .timeout(Duration.ofSeconds(adjustedOptions.getTimeoutSeconds()))
                    .logRequestsAndResponses(adjustedOptions.getLogRequests())
                    .build();

            String fullName = PROVIDER_NAME + ":" + modelName;

            // Only use rate limiter for Flash models
            SimpleRateLimiter rateLimiter = null;
            if (isFlashModel(modelName)) {
                // Initialize global rate limiter if needed
                if (flashRateLimiter == null) {
                    synchronized (this) {
                        if (flashRateLimiter == null) {
                            flashRateLimiter = new SimpleRateLimiter(settings.getFlashRateLimit());
                            log.info("Initialized global Flash rate limiter: {} RPM", settings.getFlashRateLimit());
                        }
                    }
                }
                rateLimiter = flashRateLimiter;
                log.info("Created Gemini chat: model={}, maxTokens={}, rateLimit={} RPM (shared)",
                        modelName, adjustedOptions.getMaxTokens(), settings.getFlashRateLimit());
            } else {
                log.info("Created Gemini chat: model={}, maxTokens={}, no rate limit",
                        modelName, adjustedOptions.getMaxTokens());
            }

            AiChat chat = new GeminiChat(fullName, chatModel, adjustedOptions, rateLimiter);
            return Optional.of(chat);

        } catch (Exception e) {
            log.error("Failed to create Gemini chat: model={}", modelName, e);
            return Optional.empty();
        }
    }

    /**
     * Validate and adjust options for the specific Gemini model.
     * Ensures maxTokens are within model limits and reasonable ranges.
     * If maxTokens is 0, uses the model's maximum limit.
     */
    private AiChatOptions validateAndAdjustOptions(String modelName, AiChatOptions options) {
        int maxTokens = options.getMaxTokens() != null ? options.getMaxTokens() : 0;
        int adjustedMaxTokens = maxTokens;

        // Get model-specific limits
        int modelMaxTokens = getModelMaxTokens(modelName);

        // If maxTokens is 0 or negative, use model maximum
        if (maxTokens <= 0) {
            log.info("maxTokens set to {}, using model maximum: {}", maxTokens, modelMaxTokens);
            adjustedMaxTokens = modelMaxTokens;
        }
        // Warn and adjust if exceeds model limit
        else if (maxTokens > modelMaxTokens) {
            log.warn("Requested maxTokens ({}) exceeds {} limit ({}), adjusting to {}",
                    maxTokens, modelName, modelMaxTokens, modelMaxTokens);
            adjustedMaxTokens = modelMaxTokens;
        }
        // Warn if unusually low (might indicate configuration issue)
        else if (maxTokens < 50) {
            log.warn("maxTokens ({}) is very low for model {}, consider increasing", maxTokens, modelName);
        }

        // Return adjusted options if needed
        if (adjustedMaxTokens != maxTokens) {
            return AiChatOptions.builder()
                    .temperature(options.getTemperature())
                    .maxTokens(adjustedMaxTokens)
                    .timeoutSeconds(options.getTimeoutSeconds())
                    .tools(options.getTools())
                    .jsonSchema(options.getJsonSchema())
                    .customParameters(options.getCustomParameters())
                    .logRequests(options.getLogRequests())
                    .systemMessage(options.getSystemMessage())
                    .build();
        }

        return options;
    }

    /**
     * Get maximum output tokens supported by the model.
     * Based on 2026 Gemini API limits.
     */
    private int getModelMaxTokens(String modelName) {
        if (modelName == null) return 32000; // Default for modern models

        String lowerName = modelName.toLowerCase();

        // Gemini 2.5 Pro and Gemini 3 Pro: up to 64,000 tokens
        if (lowerName.contains("2.5") && lowerName.contains("pro")) {
            return 64000;
        }
        if (lowerName.contains("3.") && lowerName.contains("pro")) {
            return 64000;
        }

        // Gemini 2.5 Flash/Flash-Lite: 8,000-32,000 tokens (use 32,000 as safe upper limit)
        if (lowerName.contains("2.5") && (lowerName.contains("flash") || lowerName.contains("lite"))) {
            return 32000;
        }

        // Gemini 2.0 (deprecated, will be shut down March 31, 2026): 8,192 tokens
        if (lowerName.contains("2.0")) {
            log.warn("Gemini 2.0 models are deprecated and will be shut down on March 31, 2026. Consider migrating to Gemini 2.5+");
            return 8192;
        }

        // Default to 32,000 for unknown/future models
        return 32000;
    }

    /**
     * Check if model name is a Flash model that requires rate limiting.
     */
    private boolean isFlashModel(String modelName) {
        if (modelName == null) return false;
        String lowerName = modelName.toLowerCase();
        return lowerName.contains("flash");
    }

    @Override
    public boolean isAvailable() {
        return settings.isAvailable();
    }
}
