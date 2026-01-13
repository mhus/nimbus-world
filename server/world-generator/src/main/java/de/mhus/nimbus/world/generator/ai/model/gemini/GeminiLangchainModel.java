package de.mhus.nimbus.world.generator.ai.model.gemini;

import de.mhus.nimbus.world.generator.ai.model.AiChat;
import de.mhus.nimbus.world.generator.ai.model.AiChatOptions;
import de.mhus.nimbus.world.generator.ai.model.LangchainModel;
import de.mhus.nimbus.world.generator.ai.model.SimpleRateLimiter;
import dev.langchain4j.model.chat.ChatLanguageModel;
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
    public Optional<AiChat> createAiChat(String modelName, AiChatOptions options) {
        if (!isAvailable()) {
            log.warn("Gemini API key not configured");
            return Optional.empty();
        }

        try {
            ChatLanguageModel chatModel = GoogleAiGeminiChatModel.builder()
                    .apiKey(settings.getApiKey())
                    .modelName(modelName)
                    .temperature(options.getTemperature())
                    .maxOutputTokens(options.getMaxTokens())
                    .timeout(Duration.ofSeconds(options.getTimeoutSeconds()))
                    .logRequestsAndResponses(options.getLogRequests())
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
                log.info("Created Gemini chat: model={}, rateLimit={} RPM (shared)", modelName, settings.getFlashRateLimit());
            } else {
                log.info("Created Gemini chat: model={}, no rate limit", modelName);
            }

            AiChat chat = new GeminiChat(fullName, chatModel, options, rateLimiter);
            return Optional.of(chat);

        } catch (Exception e) {
            log.error("Failed to create Gemini chat: model={}", modelName, e);
            return Optional.empty();
        }
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
