package de.mhus.nimbus.world.ai.model;

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
     * Create a raw ChatModel for use with AiServices (tool support, memory, etc.).
     * Default implementation creates an AiChat and extracts the ChatModel — providers
     * should override this for direct ChatModel creation.
     *
     * @param modelName Name of the specific model
     * @param options Configuration options
     * @return ChatModel if available
     */
    default Optional<dev.langchain4j.model.chat.ChatModel> createChatModel(String modelName, AiChatOptions options) {
        return Optional.empty();
    }

    /**
     * Check if this model provider is available and properly configured.
     *
     * @return true if provider can create chat instances
     */
    boolean isAvailable();
}
