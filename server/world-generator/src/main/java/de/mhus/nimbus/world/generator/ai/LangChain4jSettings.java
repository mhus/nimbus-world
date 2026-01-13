package de.mhus.nimbus.world.generator.ai;

import de.mhus.nimbus.shared.service.SSettingsService;
import de.mhus.nimbus.shared.settings.SettingDouble;
import de.mhus.nimbus.shared.settings.SettingInteger;
import de.mhus.nimbus.shared.settings.SettingString;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuration for LangChain4j integration.
 * Provides AI model beans for world generation.
 * Configuration loaded from SSettingsService at startup.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class LangChain4jSettings {

    private final SSettingsService settingsService;

    private SettingString apiKey;
    private SettingString modelName;
    private SettingInteger timeoutSeconds;
    private SettingDouble temperature;
    private SettingInteger maxTokens;

    @PostConstruct
    private void init() {
        apiKey = settingsService.getString(
                "langchain4j.openai.apiKey",
                null
        );
        modelName = settingsService.getString(
                "langchain4j.openai.modelName",
                "gpt-3.5-turbo"
        );
        timeoutSeconds = settingsService.getInteger(
                "langchain4j.openai.timeoutSeconds",
                60
        );
        temperature = settingsService.getDouble(
                "langchain4j.openai.temperature",
                0.7
        );
        maxTokens = settingsService.getInteger(
                "langchain4j.openai.maxTokens",
                1000
        );
    }

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        String key = apiKey.get();
        if (key == null || key.isBlank()) {
            log.warn("OpenAI API key not configured. LangChain4j features will be limited.");
            return null;
        }

        String model = modelName.get();
        int timeout = timeoutSeconds.get();
        double temp = temperature.get();
        int tokens = maxTokens.get();

        log.info("Initializing OpenAI ChatLanguageModel: model={}, timeout={}s, temperature={}",
                model, timeout, temp);

        return OpenAiChatModel.builder()
                .apiKey(key)
                .modelName(model)
                .timeout(Duration.ofSeconds(timeout))
                .temperature(temp)
                .maxTokens(tokens)
                .logRequests(true)
                .logResponses(true)
                .build();
    }
}
