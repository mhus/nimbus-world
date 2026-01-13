package de.mhus.nimbus.world.generator.ai.model.openai;

import de.mhus.nimbus.shared.service.SSettingsService;
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

    @PostConstruct
    private void init() {
        apiKey = settingsService.getString(
                "langchain4j.openai.apiKey",
                null
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
     * Check if OpenAI is available (API key configured).
     */
    public boolean isAvailable() {
        String key = apiKey.get();
        return key != null && !key.isBlank();
    }
}
