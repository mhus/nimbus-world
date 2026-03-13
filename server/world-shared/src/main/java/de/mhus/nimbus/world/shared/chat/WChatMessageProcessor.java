package de.mhus.nimbus.world.shared.chat;

import de.mhus.nimbus.shared.types.WorldId;

/**
 * Interface for processing specific chat message types.
 * Implementations are auto-discovered by Spring and called
 * when agent responses are saved.
 * Each processor lives in the same module as the agent that produces its message type.
 */
public interface WChatMessageProcessor {

    /**
     * Check if this processor can handle the given message.
     */
    boolean canProcess(WChatMessage message);

    /**
     * Process the message (e.g., store data in Redis, send commands to player).
     */
    void process(WorldId worldId, String sessionId, WChatMessage message);
}
