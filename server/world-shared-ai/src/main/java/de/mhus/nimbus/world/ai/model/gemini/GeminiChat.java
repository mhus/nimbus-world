package de.mhus.nimbus.world.ai.model.gemini;

import de.mhus.nimbus.world.ai.model.AiChat;
import de.mhus.nimbus.world.ai.model.AiChatException;
import de.mhus.nimbus.world.ai.model.AiChatOptions;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import de.mhus.nimbus.world.ai.model.RateLimiter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Google Gemini implementation of AiChat.
 */
@Slf4j
public class GeminiChat implements AiChat {

    private final String name;
    private final ChatModel chatModel;
    private final AiChatOptions options;
    private final RateLimiter rateLimiter;

    public GeminiChat(String name, ChatModel chatModel, AiChatOptions options,
                      RateLimiter rateLimiter) {
        this.name = name;
        this.chatModel = chatModel;
        this.options = options;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String ask(String question) throws AiChatException {
        if (question == null || question.isBlank()) {
            throw new AiChatException("Question cannot be empty");
        }

        try {
            // Apply rate limiting if available
            if (rateLimiter != null) {
                rateLimiter.waitIfNeeded();
            }

            List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();

            // Add system message if configured
            // Note: Some Gemini models may not support system messages directly
            if (options.getSystemMessage() != null && !options.getSystemMessage().isBlank()) {
                messages.add(SystemMessage.from(options.getSystemMessage()));
            }

            // Add user question
            messages.add(UserMessage.from(question));

            // Generate response
            ChatResponse response = chatModel.chat(messages);

            // Record request after successful completion
            if (rateLimiter != null) {
                rateLimiter.recordRequest();
            }

            String answer = response.aiMessage().text();
            log.debug("Gemini response for '{}': {}", question.substring(0, Math.min(50, question.length())),
                    answer.substring(0, Math.min(100, answer.length())));

            return answer;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while waiting for rate limit", e);
            throw new AiChatException("Request interrupted: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to get Gemini response", e);
            throw new AiChatException("Failed to get AI response: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isAvailable() {
        return chatModel != null;
    }
}
