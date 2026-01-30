package de.mhus.nimbus.world.ai.image;

import java.util.Optional;

/**
 * Interface for LangChain image model providers.
 * Each implementation represents a specific AI provider (e.g., OpenAI DALL-E, Vertex AI Imagen)
 * and can create AI image generation instances with different configurations.
 */
public interface LangchainImageModel {

    /**
     * Get the name of this image model provider.
     * Used as prefix in model names (e.g., "openai", "vertexai").
     *
     * @return Provider name
     */
    String getName();

    /**
     * Create an AI image generation instance with specific model and options.
     *
     * @param modelName Name of the specific model (e.g., "dall-e-3", "imagen-3")
     * @param options Configuration options for image generation
     * @return AI image model instance if model is available
     */
    Optional<AiImageModel> createImageModel(String modelName, AiImageOptions options);

    /**
     * Check if this image model provider is available and properly configured.
     *
     * @return true if provider can create image model instances
     */
    boolean isAvailable();
}
