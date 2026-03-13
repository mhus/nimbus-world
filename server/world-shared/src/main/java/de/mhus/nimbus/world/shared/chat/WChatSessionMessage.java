package de.mhus.nimbus.world.shared.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Queue item DTO for async chat message processing.
 * Used by WChatSession to process messages in a background thread.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WChatSessionMessage {

    public enum Type {
        CHAT,
        COMMAND
    }

    private Type type;
    private String worldId;
    private String chatId;
    private String agentName;
    private String playerId;
    private String playerMessageId;
    private String message;
    private String sessionId;

    /**
     * Command name (only for COMMAND type).
     */
    private String command;

    /**
     * Command parameters (only for COMMAND type).
     */
    private Map<String, Object> commandParams;
}
