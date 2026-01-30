package de.mhus.nimbus.world.ai.image;

/**
 * Interface for AI image generation instances.
 * Represents a configured image generation session with a specific AI model.
 */
public interface AiImageModel {

    /**
     * Get the full name of this AI image model instance.
     * Format: provider:model (e.g., "openai:dall-e-3", "vertexai:imagen-3")
     *
     * @return Full qualified model name
     */
    String getName();

    /**
     * Generate an image from a text prompt.
     *
     * @param prompt Text description of the image to generate
     * @return Generated image data
     * @throws AiImageException if the request fails
     */
    AiImage generate(String prompt) throws AiImageException;

    /**
     * Generate an image from a text prompt with specific dimensions.
     *
     * @param prompt Text description of the image to generate
     * @param width Desired image width in pixels
     * @param height Desired image height in pixels
     * @return Generated image data
     * @throws AiImageException if the request fails or dimensions are not supported
     */
    AiImage generate(String prompt, int width, int height) throws AiImageException;

    /**
     * Check if this image model instance is still valid and can be used.
     *
     * @return true if model is ready to use
     */
    boolean isAvailable();
}
