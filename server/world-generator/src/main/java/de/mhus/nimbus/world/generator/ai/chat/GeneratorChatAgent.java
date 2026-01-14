package de.mhus.nimbus.world.generator.ai.chat;

import de.mhus.nimbus.world.generator.ai.orchestration.WorldGeneratorOrchestratorFactory;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.chat.WChatAgent;
import de.mhus.nimbus.world.shared.chat.WChatMessage;
import de.mhus.nimbus.world.shared.chat.WChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * AI-powered world generator chat agent.
 *
 * This agent uses a multi-agent system with langchain4j to intelligently
 * generate blocks and flats based on natural language requests.
 *
 * Features:
 * - Routes requests to appropriate generation type (chat/block/flat)
 * - Plans generation using documentation
 * - Searches for appropriate block types
 * - Prepares layers and models
 * - Generates blocks or flats using manipulators
 * - Validates flat operations for safety
 * - Formats responses in German
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GeneratorChatAgent implements WChatAgent {

    private static final String AGENT_NAME = "ai-generator";
    private static final String AGENT_TITLE = "KI World Generator";

    private final WorldGeneratorOrchestratorFactory orchestratorFactory;
    private final WChatService chatService;

    // Use virtual threads for efficient async execution
    private final Executor executor = Executors.newVirtualThreadPerTaskExecutor();

    @Override
    public String getName() {
        return AGENT_NAME;
    }

    @Override
    public String getTitle() {
        return AGENT_TITLE;
    }

    @Override
    public boolean isEnabled(WorldId worldId, String sessionId) {
        return true; // Always enabled
    }

    @Override
    public List<WChatMessage> chat(WorldId worldId, String chatId, String playerId, String message) {
        return chatWithSession(worldId, chatId, playerId, message, null);
    }

    @Override
    public List<WChatMessage> chatWithSession(
            WorldId worldId,
            String chatId,
            String playerId,
            String message,
            String sessionId) {

        log.info("AI Generator processing message from player {} in world {}: {}",
            playerId, worldId, message.substring(0, Math.min(50, message.length())));

        // Immediate response: processing message
        WChatMessage processingMessage = createProcessingMessage(worldId, chatId);

        // Execute generation asynchronously
        executor.execute(() -> {
            try {
                // Load chat history for context
                List<WChatMessage> history = chatService.getChatMessages(
                    worldId, chatId, 20);

                // Create orchestrator with context
                var orchestrator = orchestratorFactory.create(
                    worldId.withoutInstance().getId(),
                    sessionId != null ? sessionId : "no-session",
                    chatId,
                    playerId,
                    history
                );

                // Process the request
                String response = orchestrator.process(message);

                // Save response to chat
                WChatMessage responseMessage = WChatMessage.builder()
                    .worldId(worldId.withoutInstance().getId())
                    .chatId(chatId)
                    .messageId(UUID.randomUUID().toString())
                    .senderId(AGENT_NAME + "-agent")
                    .message(response)
                    .type("text")
                    .command(false)
                    .createdAt(Instant.now())
                    .build();

                chatService.saveMessages(worldId, chatId, sessionId, true,
                    List.of(responseMessage));

                log.info("AI Generator completed successfully");

            } catch (Exception e) {
                log.error("AI Generator failed to process message", e);

                // Save error message
                WChatMessage errorMessage = WChatMessage.builder()
                    .worldId(worldId.withoutInstance().getId())
                    .chatId(chatId)
                    .messageId(UUID.randomUUID().toString())
                    .senderId(AGENT_NAME + "-agent")
                    .message("❌ Entschuldigung, es ist ein Fehler aufgetreten: " +
                        e.getMessage())
                    .type("error")
                    .command(false)
                    .createdAt(Instant.now())
                    .build();

                chatService.saveMessages(worldId, chatId, sessionId, false,
                    List.of(errorMessage));
            }
        });

        // Return immediate processing message
        return List.of(processingMessage);
    }

    @Override
    public List<WChatMessage> executeCommand(
            WorldId worldId,
            String chatId,
            String playerId,
            String command,
            Map<String, Object> params) {

        log.info("AI Generator command execution not yet implemented: {}", command);

        WChatMessage message = WChatMessage.builder()
            .worldId(worldId.withoutInstance().getId())
            .chatId(chatId)
            .messageId(UUID.randomUUID().toString())
            .senderId(AGENT_NAME + "-agent")
            .message("Command execution ist noch nicht implementiert.")
            .type("text")
            .command(false)
            .createdAt(Instant.now())
            .build();

        return List.of(message);
    }

    /**
     * Create an immediate processing message.
     */
    private WChatMessage createProcessingMessage(WorldId worldId, String chatId) {
        return WChatMessage.builder()
            .worldId(worldId.withoutInstance().getId())
            .chatId(chatId)
            .messageId(UUID.randomUUID().toString())
            .senderId(AGENT_NAME + "-agent")
            .message("⏳ Einen Moment, ich analysiere deine Anfrage...")
            .type("text")
            .command(false)
            .createdAt(Instant.now())
            .build();
    }
}
