package de.mhus.nimbus.world.shared.chat;

import de.mhus.nimbus.shared.types.WorldId;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Interface for chat agent implementations.
 * Chat agents can process player messages and generate responses.
 */
public interface WChatAgent {

    /**
     * Get the technical name of the agent.
     * This is used as an identifier in the system.
     *
     * @return Technical name (e.g., "eliza", "gpt-assistant")
     */
    String getName();

    /**
     * Get the display title of the agent.
     * This is shown to users in the UI.
     *
     * @return Display title (e.g., "Eliza Chatbot", "GPT Assistant")
     */
    String getTitle();

    /**
     * Return true if the agent is enabled for the given world and session.
     *
     * @return true if enabled, false otherwise
     */
    boolean isEnabled(WorldId worldId, String sessionId);

    /**
     * Process a chat message and generate responses.
     *
     * @param worldId The world identifier
     * @param chatId The chat ID where the message is sent
     * @param playerId The player ID sending the message
     * @param message The message content
     * @return List of response messages from the agent
     */
    List<WChatMessage> chat(WorldId worldId, String chatId, String playerId, String message);

    /**
     * Process a chat message with session context and generate responses.
     * Default implementation delegates to chat() without sessionId for backwards compatibility.
     *
     * @param worldId The world identifier
     * @param chatId The chat ID where the message is sent
     * @param playerId The player ID sending the message
     * @param message The message content
     * @param sessionId The session ID for accessing session-specific context (optional)
     * @return List of response messages from the agent
     */
    default List<WChatMessage> chatWithSession(WorldId worldId, String chatId, String playerId, String message, String sessionId) {
        return chat(worldId, chatId, playerId, message);
    }

    /**
     * Execute a command on the agent and generate responses.
     * This allows structured interaction with the agent beyond simple chat messages.
     *
     * @param worldId The world identifier
     * @param chatId The chat ID where the command is executed
     * @param playerId The player ID executing the command
     * @param command The command to execute
     * @param params Command parameters
     * @return List of response messages from the agent
     */
    default List<WChatMessage> executeCommand(WorldId worldId, String chatId, String playerId,
                                             String command, Map<String, Object> params) {
        // Default implementation: return error message
        WChatMessage errorMessage = WChatMessage.builder()
                .worldId(worldId.withoutInstance().getId())
                .messageId(UUID.randomUUID().toString())
                .senderId(getName() + "-agent")
                .message("Command execution not supported by this agent: " + command)
                .type("error")
                .createdAt(Instant.now())
                .build();
        return List.of(errorMessage);
    }
}
