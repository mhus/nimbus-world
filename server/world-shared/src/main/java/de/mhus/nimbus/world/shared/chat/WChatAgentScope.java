package de.mhus.nimbus.world.shared.chat;

/**
 * Scope for chat agent visibility.
 * Determines which users can see and interact with a chat agent.
 */
public enum WChatAgentScope {
    /** Visible to all users (players and editors). */
    ALL,
    /** Visible only to players. */
    PLAYER,
    /** Visible only to editors/administrators. */
    EDITOR
}
