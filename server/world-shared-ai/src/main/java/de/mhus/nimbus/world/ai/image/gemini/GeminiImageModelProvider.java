package de.mhus.nimbus.world.ai.image.gemini;

import de.mhus.nimbus.world.ai.image.AiImageModel;
import de.mhus.nimbus.world.ai.image.AiImageOptions;
import de.mhus.nimbus.world.ai.image.BackgroundRemover;
import de.mhus.nimbus.world.ai.image.LangchainImageModel;
import de.mhus.nimbus.world.ai.model.SimpleRateLimiter;
import de.mhus.nimbus.world.ai.model.gemini.GeminiSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

/**
 * Google Gemini implementation of {@link LangchainImageModel}.
 * Registered under the provider name {@code gemini}, so image models are addressed as
 * {@code gemini:<model>} (e.g. {@code gemini:gemini-2.5-flash-image}) and are selectable purely via
 * configuration through {@code AiModelService} (setting {@code asset.image.ai-model} /
 * image-model mappings).
 * <p>
 * Gemini has no native transparency; {@link AiImageOptions#isTransparentBackground()} is realized by
 * generating on a flat background and cutting it out with {@link BackgroundRemover}
 * (see {@link GeminiImageModelImpl}).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GeminiImageModelProvider implements LangchainImageModel {

    private static final String PROVIDER_NAME = "gemini";

    private final GeminiSettings settings;
    private final ObjectMapper objectMapper;

    // Shared across all Gemini image models created by this provider instance.
    private volatile SimpleRateLimiter rateLimiter;

    @Override
    public String getName() {
        return PROVIDER_NAME;
    }

    @Override
    public Optional<AiImageModel> createImageModel(String modelName, AiImageOptions options) {
        if (!isAvailable()) {
            log.warn("Gemini API key not configured");
            return Optional.empty();
        }
        if (rateLimiter == null) {
            synchronized (this) {
                if (rateLimiter == null) {
                    rateLimiter = new SimpleRateLimiter(settings.getFlashRateLimit());
                    log.info("Initialized Gemini image rate limiter: {} RPM", settings.getFlashRateLimit());
                }
            }
        }

        String fullName = PROVIDER_NAME + ":" + modelName;
        AiImageModel model = new GeminiImageModelImpl(
                fullName, modelName, settings.getApiKey(), options,
                objectMapper, rateLimiter, BackgroundRemover.DEFAULT_THRESHOLD);
        log.info("Created Gemini image model: model={}, transparentBackground={}",
                modelName, options.isTransparentBackground());
        return Optional.of(model);
    }

    @Override
    public boolean isAvailable() {
        return settings.isAvailable();
    }
}
