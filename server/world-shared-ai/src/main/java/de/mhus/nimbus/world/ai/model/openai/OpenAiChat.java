package de.mhus.nimbus.world.ai.model.openai;

import de.mhus.nimbus.world.ai.model.AiChat;
import de.mhus.nimbus.world.ai.model.AiChatException;
import de.mhus.nimbus.world.ai.model.AiChatOptions;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * OpenAI implementation of AiChat.
 */
@RequiredArgsConstructor
@Slf4j
public class OpenAiChat implements AiChat {

    private final String name;
    private final ChatModel chatModel;
    private final AiChatOptions options;

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
            List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();

            // Add system message if configured
            if (options.getSystemMessage() != null && !options.getSystemMessage().isBlank()) {
                messages.add(SystemMessage.from(options.getSystemMessage()));
            }

            // Add user question
            messages.add(UserMessage.from(question));

            // Generate response
            ChatResponse response = chatModel.chat(messages);

            String answer = response.aiMessage().text();
            log.debug("OpenAI response for '{}': {}", question.substring(0, Math.min(50, question.length())),
                    answer.substring(0, Math.min(100, answer.length())));

            return answer;

        } catch (Exception e) {
            log.error("Failed to get OpenAI response", e);
            throw new AiChatException("Failed to get AI response: " + e.getMessage(), e);
        }
    }

    @Override
    public String askWithImage(String question, byte[] imageBytes, String mimeType) throws AiChatException {
        if (question == null || question.isBlank()) {
            throw new AiChatException("Question cannot be empty");
        }
        if (imageBytes == null || imageBytes.length == 0) {
            throw new AiChatException("Image data cannot be empty");
        }

        try {
            List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();

            // Add system message if configured
            if (options.getSystemMessage() != null && !options.getSystemMessage().isBlank()) {
                messages.add(SystemMessage.from(options.getSystemMessage()));
            }

            // Convert image bytes to base64
            String base64Image = Base64.getEncoder().encodeToString(imageBytes);

            // Create image from base64 data
            Image image = Image.builder()
                    .base64Data(base64Image)
                    .mimeType(mimeType)
                    .build();

            // Create user message with text and image content
            UserMessage userMessage = UserMessage.from(
                    TextContent.from(question),
                    ImageContent.from(image)
            );

            messages.add(userMessage);

            // Generate response
            ChatResponse response = chatModel.chat(messages);

            String answer = response.aiMessage().text();
            log.debug("OpenAI vision response for '{}': {}", question.substring(0, Math.min(50, question.length())),
                    answer.substring(0, Math.min(100, answer.length())));

            return answer;

        } catch (Exception e) {
            log.error("Failed to get OpenAI vision response", e);
            throw new AiChatException("Failed to get AI vision response: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isAvailable() {
        return chatModel != null;
    }
}
