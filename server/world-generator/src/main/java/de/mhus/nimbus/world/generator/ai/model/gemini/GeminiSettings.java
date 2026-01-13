package de.mhus.nimbus.world.generator.ai.model.gemini;

import de.mhus.nimbus.shared.service.SSettingsService;
import de.mhus.nimbus.shared.settings.SettingInteger;
import de.mhus.nimbus.shared.settings.SettingString;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Configuration for Google Gemini AI models.
 * Loaded from SSettingsService at startup.
 */
@Component
@RequiredArgsConstructor
public class GeminiSettings {

    private final SSettingsService settingsService;

    private SettingString apiKey;
    private SettingInteger flashRateLimit;

    @PostConstruct
    private void init() {
        apiKey = settingsService.getString(
                "langchain4j.gemini.apiKey",
                null
        );
        flashRateLimit = settingsService.getInteger(
                "langchain4j.gemini.flashRateLimit",
                15
        );
    }

    /**
     * Google Gemini API key.
     * Default: null (not configured)
     */
    public String getApiKey() {
        return apiKey.get();
    }

    /**
     * Rate limit for Gemini Flash models in requests per minute (RPM).
     * Gemini Flash free tier: 15 RPM
     * Default: 15
     */
    public int getFlashRateLimit() {
        return flashRateLimit.get();
    }

    /**
     * Check if Gemini is available (API key configured).
     */
    public boolean isAvailable() {
        String key = apiKey.get();
        return key != null && !key.isBlank();
    }
}
