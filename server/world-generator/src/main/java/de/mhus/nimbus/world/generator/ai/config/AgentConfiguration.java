package de.mhus.nimbus.world.generator.ai.config;

import de.mhus.nimbus.world.ai.model.AiChat;
import de.mhus.nimbus.world.ai.model.AiChatOptions;
import de.mhus.nimbus.world.ai.model.AiModelService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Spring configuration for the AI agent system.
 *
 * This configuration provides:
 * - ChatModel for LLM interactions via AiModelService
 * - Tool services for agent tool usage
 * - Configuration from application properties
 */
@Configuration
@Slf4j
public class AgentConfiguration {

    @Value("${ai.agent.model:default:chat}")
    private String agentModelName;

    @Value("${ai.agent.temperature:0.7}")
    private Double temperature;

    @Value("${ai.agent.max-tokens:4096}")
    private Integer maxTokens;

    /**
     * Provides the ChatModel for agents using AiModelService.
     *
     * Creates an adapter that wraps AiChat in the ChatModel interface.
     */
    @Bean
    public ChatModel chatModel(AiModelService aiModelService) {
        log.info("Configuring ChatModel from AiModelService: model={}, temperature={}, maxTokens={}",
            agentModelName, temperature, maxTokens);

        // Create AI chat options
        AiChatOptions options = AiChatOptions.builder()
            .temperature(temperature)
            .maxTokens(maxTokens)
            .build();

        // Get AiChat from service
        AiChat aiChat = aiModelService.createChat(agentModelName, options)
            .orElseThrow(() -> new IllegalStateException(
                "Failed to create AI chat model: " + agentModelName +
                ". Check if the model is configured and available."));

        log.info("AI chat created successfully: {}", aiChat.getName());

        // Return adapter that wraps AiChat as ChatModel
        return new AiChatModelAdapter(aiChat);
    }

    /**
     * Adapter that wraps AiChat to implement ChatModel interface.
     */
    private static class AiChatModelAdapter implements ChatModel {
        private final AiChat aiChat;

        AiChatModelAdapter(AiChat aiChat) {
            this.aiChat = aiChat;
        }

        @Override
        public ChatResponse chat(List<ChatMessage> messages) {
            // Convert messages to prompt
            String prompt = messages.stream()
                .map(msg -> {
                    if (msg instanceof UserMessage userMsg) {
                        return userMsg.singleText();
                    } else if (msg instanceof AiMessage aiMsg) {
                        return aiMsg.text();
                    } else if (msg instanceof dev.langchain4j.data.message.SystemMessage sysMsg) {
                        return sysMsg.text();
                    }
                    // Fallback: try to get text from message
                    return msg.toString();
                })
                .collect(Collectors.joining("\n"));

            // Ask AI chat
            try {
                String response = aiChat.ask(prompt);
                return ChatResponse.builder()
                    .aiMessage(AiMessage.from(response))
                    .build();
            } catch (Exception e) {
                throw new RuntimeException("AI chat failed: " + e.getMessage(), e);
            }
        }

        @Override
        public ChatResponse chat(ChatRequest request) {
            return chat(request.messages());
        }
    }

    /**
     * Note: Tool services are auto-detected by Spring via @Service or @Component annotations.
     * No manual bean configuration needed:
     * - BlockTypeToolService
     * - LayerToolService
     * - DocumentToolService
     * - BlockToolService
     * - FlatToolService
     */
}
