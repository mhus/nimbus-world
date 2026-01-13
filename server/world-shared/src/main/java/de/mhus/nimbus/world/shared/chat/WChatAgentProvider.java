package de.mhus.nimbus.world.shared.chat;

import de.mhus.nimbus.shared.types.WorldId;

import java.util.List;

/**
 * Provider interface for chat agents.
 * Abstracts the source of chat agents (local or remote).
 */
public interface WChatAgentProvider {

    /**
     * Get the name of this provider (e.g., "local", "generator", "life").
     *
     * @return the provider name
     */
    String getProviderName();

    /**
     * Get a specific agent by name.
     *
     * @param agentName the agent name
     * @return the agent, or null if not found
     */
    WChatAgent getAgent(String agentName);

    /**
     * Get all available agents from this provider.
     *
     * @return list of available agents
     */
    List<WChatAgent> getAvailableAgents(WorldId worldId, String sessionId);

    /**
     * Check if this provider is available.
     *
     * @return true if the provider is available and can provide agents
     */
    boolean isAvailable();
}
