package de.mhus.nimbus.world.generator.ai.model;

import java.util.Optional;

/**
 * Interface for LangChain model providers.
 * Each implementation represents a specific AI provider (e.g., OpenAI, Gemini, etc.)
 * and can create AI chat instances with different configurations.
 */
public interface LangchainModel {

    /**
     * Get the name of this model provider.
     * Used as prefix in model names (e.g., "openai", "gemini").
     *
     * @return Provider name
     */
    String getName();

    /**
     * Create an AI chat instance with specific model and options.
     *
     * @param modelName Name of the specific model (e.g., "gpt-4", "gemini-pro")
     * @param options Configuration options for the chat
     * @return AI chat instance if model is available
     */
    Optional<AiChat> createAiChat(String modelName, AiChatOptions options);

    /**
     * Check if this model provider is available and properly configured.
     *
     * @return true if provider can create chat instances
     */
    boolean isAvailable();
}
