package de.mhus.nimbus.world.ai.model.cortecs;

import de.mhus.nimbus.shared.service.SSettingsService;
import de.mhus.nimbus.shared.settings.SettingString;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Configuration for the cortecs.ai OpenAI-compatible aggregator (hosts DeepSeek, gpt-oss, Qwen, ...).
 * One JWT gives access to many models via a single OpenAI-shaped endpoint. Loaded from
 * SSettingsService at startup.
 */
@Component
@RequiredArgsConstructor
public class CortecsSettings {

    private static final String DEFAULT_BASE_URL = "https://api.cortecs.ai/v1";

    private final SSettingsService settingsService;

    private SettingString apiKey;
    private SettingString baseUrl;

    @PostConstruct
    private void init() {
        apiKey = settingsService.getString("langchain4j.cortecs.apiKey", null);
        baseUrl = settingsService.getString("langchain4j.cortecs.baseUrl", DEFAULT_BASE_URL);
    }

    /** Cortecs API key (JWT). Default: null (not configured). */
    public String getApiKey() {
        return apiKey.get();
    }

    /** OpenAI-compatible base URL. Default: {@value #DEFAULT_BASE_URL}. */
    public String getBaseUrl() {
        String url = baseUrl.get();
        return url == null || url.isBlank() ? DEFAULT_BASE_URL : url;
    }

    /** Check if cortecs is available (API key configured). */
    public boolean isAvailable() {
        String key = apiKey.get();
        return key != null && !key.isBlank();
    }
}
