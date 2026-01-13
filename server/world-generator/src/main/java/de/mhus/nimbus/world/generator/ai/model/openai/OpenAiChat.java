package de.mhus.nimbus.world.generator.ai.model.openai;

import de.mhus.nimbus.world.generator.ai.model.AiChat;
import de.mhus.nimbus.world.generator.ai.model.AiChatException;
import de.mhus.nimbus.world.generator.ai.model.AiChatOptions;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenAI implementation of AiChat.
 */
@RequiredArgsConstructor
@Slf4j
public class OpenAiChat implements AiChat {

    private final String name;
    private final ChatLanguageModel chatModel;
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
            Response<AiMessage> response = chatModel.generate(messages);

            String answer = response.content().text();
            log.debug("OpenAI response for '{}': {}", question.substring(0, Math.min(50, question.length())),
                    answer.substring(0, Math.min(100, answer.length())));

            return answer;

        } catch (Exception e) {
            log.error("Failed to get OpenAI response", e);
            throw new AiChatException("Failed to get AI response: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isAvailable() {
        return chatModel != null;
    }
}
