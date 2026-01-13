package de.mhus.nimbus.world.control.chat;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.chat.WChatAgent;
import de.mhus.nimbus.world.shared.chat.WChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Simple Eliza-style chatbot agent for testing.
 * Provides pattern-based responses inspired by the classic ELIZA program.
 */
@Component
@Slf4j
public class ElizaChatAgent implements WChatAgent {

    private static final String AGENT_ID = "eliza-agent";
    private final Random random = new Random();

    private static final String[] GREETINGS = {
            "Hello! How are you feeling today?",
            "Hi there! What brings you here?",
            "Welcome! What's on your mind?"
    };

    private static final String[] QUESTION_RESPONSES = {
            "Why do you ask that?",
            "What would it mean to you if I told you?",
            "What comes to mind when you think about that?",
            "Why is that question important to you?"
    };

    private static final String[] FEELING_RESPONSES = {
            "Tell me more about how you feel.",
            "Why do you feel that way?",
            "How long have you felt like this?",
            "What makes you feel that way?"
    };

    private static final String[] DEFAULT_RESPONSES = {
            "I see. Please go on.",
            "Can you elaborate on that?",
            "Very interesting. Tell me more.",
            "How does that make you feel?",
            "What do you think about that?"
    };

    @Override
    public String getName() {
        return "eliza";
    }

    @Override
    public String getTitle() {
        return "Eliza Chatbot";
    }

    @Override
    public boolean isEnabled(WorldId worldId, String sessionId) {
        return true;
    }

    @Override
    public List<WChatMessage> chat(WorldId worldId, String chatId, String playerId, String message) {
        log.debug("Eliza processing message from player {}: {}", playerId, message);

        // Check if message starts with '/' - create command message
        if (message != null && message.trim().startsWith("/")) {
            String commandName = message.trim().substring(1); // Remove '/'

            log.info("Creating command message for: {}", commandName);

            WChatMessage commandMessage = WChatMessage.builder()
                    .worldId(worldId.withoutInstance().getId())
                    .chatId(null) // Will be set by the command or service
                    .messageId(UUID.randomUUID().toString())
                    .senderId(AGENT_ID)
                    .message("Click to execute: " + commandName)
                    .type(commandName)
                    .command(true)
                    .createdAt(Instant.now())
                    .build();

            return List.of(commandMessage);
        }

        String response = generateResponse(message);

        WChatMessage responseMessage = WChatMessage.builder()
                .worldId(worldId.withoutInstance().getId())
                .chatId(null) // Will be set by the service
                .messageId(UUID.randomUUID().toString())
                .senderId(AGENT_ID)
                .message(response)
                .type("text")
                .createdAt(Instant.now())
                .build();

        return List.of(responseMessage);
    }

    private String generateResponse(String message) {
        if (message == null || message.isBlank()) {
            return randomFrom(DEFAULT_RESPONSES);
        }

        String lowerMessage = message.toLowerCase().trim();

        // Greetings
        if (lowerMessage.matches("^(hi|hello|hey|greetings).*")) {
            return randomFrom(GREETINGS);
        }

        // Questions
        if (lowerMessage.endsWith("?")) {
            return randomFrom(QUESTION_RESPONSES);
        }

        // Feelings
        if (lowerMessage.contains("feel") || lowerMessage.contains("felt") ||
                lowerMessage.contains("sad") || lowerMessage.contains("happy") ||
                lowerMessage.contains("angry") || lowerMessage.contains("worried")) {
            return randomFrom(FEELING_RESPONSES);
        }

        // I am/I'm statements - reflection
        if (lowerMessage.matches("^(i am|i'm) .*")) {
            String remainder = lowerMessage.replaceFirst("^(i am|i'm) ", "");
            return "Why are you " + remainder + "?";
        }

        // I want/need statements
        if (lowerMessage.matches("^(i want|i need) .*")) {
            String remainder = lowerMessage.replaceFirst("^(i want|i need) ", "");
            return "What would it mean to you to get " + remainder + "?";
        }

        // I can't statements
        if (lowerMessage.matches("^i can't .*")) {
            String remainder = lowerMessage.replaceFirst("^i can't ", "");
            return "Why do you think you can't " + remainder + "?";
        }

        // Default response
        return randomFrom(DEFAULT_RESPONSES);
    }

    private String randomFrom(String[] responses) {
        return responses[random.nextInt(responses.length)];
    }
}
