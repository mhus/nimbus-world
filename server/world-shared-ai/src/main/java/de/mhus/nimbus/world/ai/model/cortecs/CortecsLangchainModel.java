package de.mhus.nimbus.world.ai.model.cortecs;

import de.mhus.nimbus.world.ai.model.AiChat;
import de.mhus.nimbus.world.ai.model.AiChatOptions;
import de.mhus.nimbus.world.ai.model.LangchainModel;
import de.mhus.nimbus.world.ai.model.openai.OpenAiChat;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * cortecs.ai implementation of {@link LangchainModel}. Uses the OpenAI protocol against the cortecs
 * base URL, so any hosted model is addressed as {@code cortecs:<model>}, e.g.
 * {@code cortecs:deepseek-v4-pro}. Reuses langchain4j's {@link OpenAiChatModel} (just a different
 * {@code baseUrl} + key) and the shared {@link OpenAiChat} wrapper.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CortecsLangchainModel implements LangchainModel {

    private static final String PROVIDER_NAME = "cortecs";

    private final CortecsSettings settings;

    @Override
    public String getName() {
        return PROVIDER_NAME;
    }

    @Override
    public Optional<AiChat> createAiChat(String modelName, AiChatOptions options) {
        return buildChatModel(modelName, options)
                .map(cm -> new OpenAiChat(PROVIDER_NAME + ":" + modelName, cm, options));
    }

    @Override
    public Optional<ChatModel> createChatModel(String modelName, AiChatOptions options) {
        return buildChatModel(modelName, options).map(cm -> cm);
    }

    private Optional<OpenAiChatModel> buildChatModel(String modelName, AiChatOptions options) {
        if (!isAvailable()) {
            log.warn("Cortecs API key not configured");
            return Optional.empty();
        }
        try {
            OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                    .baseUrl(settings.getBaseUrl())
                    .apiKey(settings.getApiKey())
                    .modelName(modelName)
                    .temperature(options.getTemperature())
                    .timeout(Duration.ofSeconds(options.getTimeoutSeconds()))
                    .logRequests(options.getLogRequests())
                    .logResponses(options.getLogRequests());
            // maxTokens=0 means "model maximum" in our options; a real OpenAI endpoint rejects 0,
            // so only pass it when a positive cap is set.
            if (options.getMaxTokens() > 0) {
                builder.maxTokens(options.getMaxTokens());
            }
            OpenAiChatModel chatModel = builder.build();
            log.info("Created cortecs chat: baseUrl={}, model={}", settings.getBaseUrl(), modelName);
            return Optional.of(chatModel);
        } catch (Exception e) {
            log.error("Failed to create cortecs chat: model={}", modelName, e);
            return Optional.empty();
        }
    }

    @Override
    public boolean isAvailable() {
        return settings.isAvailable();
    }
}
