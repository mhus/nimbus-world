package de.mhus.nimbus.world.generator.chat;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.chat.WChatAgent;
import de.mhus.nimbus.world.shared.chat.WChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Generator-specific Eliza-style chatbot agent for testing remote chat functionality.
 * Provides terrain generation themed responses to distinguish from the standard Eliza agent.
 */
@Component
@Slf4j
public class GeneratorElizaChatAgent implements WChatAgent {

    private static final String AGENT_ID = "generator-eliza-agent";
    private final Random random = new Random();

    private static final String[] GREETINGS = {
            "Hello from the Generator! Ready to create some worlds?",
            "Greetings, world builder! How can I help with terrain generation today?",
            "Welcome to the Generator service! Let's shape some landscapes!"
    };

    private static final String[] QUESTION_RESPONSES = {
            "That's an interesting terrain generation question!",
            "Have you considered the topographical implications of that?",
            "What kind of landscape are you envisioning?",
            "Would that create interesting elevation patterns?"
    };

    private static final String[] FEELING_RESPONSES = {
            "Terrain generation can be quite emotional!",
            "Does working with procedural generation make you feel creative?",
            "Building worlds can stir deep feelings indeed.",
            "The landscape reflects your mood, doesn't it?"
    };

    private static final String[] DEFAULT_RESPONSES = {
            "Fascinating! Tell me more about your world-building ideas.",
            "Have you considered using perlin noise for that?",
            "The generator service is processing your thoughts...",
            "That could create some interesting terrain features!",
            "What biome would best represent that concept?"
    };

    @Override
    public String getName() {
        return "generator-eliza";
    }

    @Override
    public String getTitle() {
        return "Generator Eliza (Terrain Edition)";
    }

    @Override
    public List<WChatMessage> chat(WorldId worldId, String chatId, String playerId, String message) {
        log.info("🌍 Generator Eliza processing REMOTE message from player {}: {}", playerId, message);

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

        // Regular message processing
        String response = generateResponse(message);

        WChatMessage responseMessage = WChatMessage.builder()
                .worldId(worldId.withoutInstance().getId())
                .chatId(null) // Will be set by the command or service
                .messageId(UUID.randomUUID().toString())
                .senderId(AGENT_ID)
                .message(response)
                .type("text")
                .createdAt(Instant.now())
                .build();

        log.debug("Generator Eliza response: {}", response);

        return List.of(responseMessage);
    }

    @Override
    public List<WChatMessage> executeCommand(WorldId worldId, String chatId, String playerId, String command, Map<String, Object> params) {
        log.info("🌍 Generator Eliza executing command '{}' from player {}, params: {}", command, playerId, params);

        // Get messageId from params if available
        String messageId = params != null && params.containsKey("messageId") ? params.get("messageId").toString() : null;

        // Generate Eliza-style response based on command
        String response = generateCommandResponse(command, messageId);

        WChatMessage responseMessage = WChatMessage.builder()
                .worldId(worldId.withoutInstance().getId())
                .chatId(null) // Will be set by the command or service
                .messageId(UUID.randomUUID().toString())
                .senderId(AGENT_ID)
                .message(response)
                .type("text")
                .createdAt(Instant.now())
                .build();

        log.debug("Generator Eliza command response: {}", response);

        return List.of(responseMessage);
    }

    private String generateCommandResponse(String command, String messageId) {
        if (command == null || command.isBlank()) {
            return "I noticed you executed something, but I'm not sure what. Tell me more about your terrain generation needs!";
        }

        // Handle "message" command (when clicking command buttons)
        if ("message".equals(command)) {
            return "Ah, you clicked on that command button! Interesting choice. The Generator service has recorded your action in the terrain logs.";
        }

        // Generate Eliza-style responses based on command name
        String lowerCommand = command.toLowerCase().trim();

        if (lowerCommand.contains("help")) {
            return "You seek help from the Generator? I'm here to assist with all your terrain generation questions!";
        }

        if (lowerCommand.contains("status") || lowerCommand.contains("info")) {
            return "The Generator service is running smoothly! All terrain algorithms are operational. How does that make you feel?";
        }

        if (lowerCommand.contains("terrain") || lowerCommand.contains("world") || lowerCommand.contains("generate")) {
            return "Ah, '" + command + "' - a fascinating terrain command! Have you considered the topographical implications?";
        }

        if (lowerCommand.contains("clear") || lowerCommand.contains("reset")) {
            return "You want to clear things? Like a fresh, flat terrain waiting to be sculpted!";
        }

        // Default response
        return "You executed '" + command + "'. That's an interesting command choice! Would you like to generate some terrain to reflect on that?";
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

        // Terrain/generation related
        if (lowerMessage.contains("terrain") || lowerMessage.contains("world") ||
                lowerMessage.contains("generate") || lowerMessage.contains("landscape")) {
            return "Ah yes, " + message + " - that's my specialty! The Generator service excels at creating such features.";
        }

        // I am/I'm statements - reflection with generator theme
        if (lowerMessage.matches("^(i am|i'm) .*")) {
            String remainder = lowerMessage.replaceFirst("^(i am|i'm) ", "");
            return "Being " + remainder + " is like a flat terrain waiting to be sculpted!";
        }

        // I want/need statements - generator themed
        if (lowerMessage.matches("^(i want|i need) .*")) {
            String remainder = lowerMessage.replaceFirst("^(i want|i need) ", "");
            return "To generate " + remainder + " would require careful terrain manipulation.";
        }

        // I can't statements
        if (lowerMessage.matches("^i can't .*")) {
            String remainder = lowerMessage.replaceFirst("^i can't ", "");
            return "Perhaps the Generator service can help you " + remainder + " with procedural generation?";
        }

        // Default response
        return randomFrom(DEFAULT_RESPONSES);
    }

    @Override
    public boolean isEnabled(WorldId worldId, String sessionId) {
        return true;
    }

    private String randomFrom(String[] responses) {
        return responses[random.nextInt(responses.length)];
    }
}
