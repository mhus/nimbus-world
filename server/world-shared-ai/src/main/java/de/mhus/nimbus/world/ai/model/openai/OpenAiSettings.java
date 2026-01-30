package de.mhus.nimbus.world.ai.model.openai;

import de.mhus.nimbus.shared.service.SSettingsService;
import de.mhus.nimbus.shared.settings.SettingInteger;
import de.mhus.nimbus.shared.settings.SettingString;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Configuration for OpenAI models.
 * Loaded from SSettingsService at startup.
 */
@Component
@RequiredArgsConstructor
public class OpenAiSettings {

    private final SSettingsService settingsService;

    private SettingString apiKey;
    private SettingInteger imageRateLimit;

    @PostConstruct
    private void init() {
        apiKey = settingsService.getString(
                "langchain4j.openai.apiKey",
                null
        );
        imageRateLimit = settingsService.getInteger(
                "langchain4j.openai.image.rateLimit",
                10
        );
    }

    /**
     * OpenAI API key.
     * Default: null (not configured)
     */
    public String getApiKey() {
        return apiKey.get();
    }

    /**
     * Rate limit for DALL-E image generation in requests per minute (RPM).
     * DALL-E rate limit: 10 RPM (tier 1), 50 RPM (tier 5)
     * Default: 10
     */
    public int getImageRateLimit() {
        return imageRateLimit.get();
    }

    /**
     * Check if OpenAI is available (API key configured).
     */
    public boolean isAvailable() {
        String key = apiKey.get();
        return key != null && !key.isBlank();
    }
}
