package de.mhus.nimbus.world.ai.image.openai;

import de.mhus.nimbus.world.ai.image.AiImageModel;
import de.mhus.nimbus.world.ai.image.AiImageOptions;
import de.mhus.nimbus.world.ai.image.LangchainImageModel;
import de.mhus.nimbus.world.ai.model.SimpleRateLimiter;
import de.mhus.nimbus.world.ai.model.openai.OpenAiSettings;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.openai.OpenAiImageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * OpenAI DALL-E implementation of LangchainImageModel.
 * Supports DALL-E models (dall-e-2, dall-e-3).
 * Includes rate limiting to respect DALL-E API quotas (10 RPM default).
 * Rate limiter is shared across all DALL-E image models from this provider instance.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OpenAiImageModelProvider implements LangchainImageModel {

    private static final String PROVIDER_NAME = "openai";

    private final OpenAiSettings settings;

    // Global rate limiter shared across all DALL-E image models
    private SimpleRateLimiter imageRateLimiter;

    @Override
    public String getName() {
        return PROVIDER_NAME;
    }

    @Override
    public Optional<AiImageModel> createImageModel(String modelName, AiImageOptions options) {
        if (!isAvailable()) {
            log.warn("OpenAI API key not configured");
            return Optional.empty();
        }

        try {
            // Initialize global rate limiter if needed
            if (imageRateLimiter == null) {
                synchronized (this) {
                    if (imageRateLimiter == null) {
                        imageRateLimiter = new SimpleRateLimiter(settings.getImageRateLimit());
                        log.info("Initialized DALL-E rate limiter: {} RPM", settings.getImageRateLimit());
                    }
                }
            }

            // Build OpenAI ImageModel with options
            OpenAiImageModel.OpenAiImageModelBuilder builder = OpenAiImageModel.builder()
                    .apiKey(settings.getApiKey())
                    .modelName(modelName)
                    .timeout(Duration.ofSeconds(options.getTimeoutSeconds()))
                    .logRequests(options.isLogRequests())
                    .logResponses(options.isLogRequests());

            // Add DALL-E 3 specific options (quality and style are only supported by DALL-E 3)
            if (modelName.contains("dall-e-3")) {
                if (options.getQuality() != null) {
                    builder.quality(options.getQuality());
                }
                if (options.getStyle() != null) {
                    builder.style(options.getStyle());
                }
            }
            if (options.getResponseFormat() != null) {
                builder.responseFormat(options.getResponseFormat());
            }

            // Set size - DALL-E supports specific sizes
            String size = formatSize(options.getWidth(), options.getHeight(), modelName);
            if (size != null) {
                builder.size(size);
            }

            ImageModel imageModel = builder.build();

            String fullName = PROVIDER_NAME + ":" + modelName;
            AiImageModel aiImageModel = new OpenAiImageModelImpl(fullName, imageModel, options, imageRateLimiter);

            log.info("Created OpenAI image model: model={}, size={}", modelName, size);
            return Optional.of(aiImageModel);

        } catch (Exception e) {
            log.error("Failed to create OpenAI image model: model={}", modelName, e);
            return Optional.empty();
        }
    }

    @Override
    public boolean isAvailable() {
        return settings.isAvailable();
    }

    /**
     * Format size string for DALL-E API.
     * DALL-E 2 supports: 256x256, 512x512, 1024x1024
     * DALL-E 3 supports: 1024x1024, 1024x1792, 1792x1024
     *
     * @param width Image width
     * @param height Image height
     * @param modelName Model name to determine supported sizes
     * @return Formatted size string or null if unsupported
     */
    private String formatSize(int width, int height, String modelName) {
        String size = width + "x" + height;

        // DALL-E 3 specific sizes
        if (modelName.contains("dall-e-3")) {
            if (size.equals("1024x1024") || size.equals("1024x1792") || size.equals("1792x1024")) {
                return size;
            }
            // Default to 1024x1024 for DALL-E 3
            log.warn("Unsupported size {} for DALL-E 3, using 1024x1024", size);
            return "1024x1024";
        }

        // DALL-E 2 specific sizes
        if (size.equals("256x256") || size.equals("512x512") || size.equals("1024x1024")) {
            return size;
        }

        // Default to closest supported size
        if (width <= 256 && height <= 256) return "256x256";
        if (width <= 512 && height <= 512) return "512x512";

        log.warn("Unsupported size {}, using 1024x1024", size);
        return "1024x1024";
    }
}
