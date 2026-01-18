package de.mhus.nimbus.world.ai.model;

/**
 * Interface for AI chat instances.
 * Represents a configured chat session with a specific AI model.
 */
public interface AiChat {

    /**
     * Get the full name of this AI chat instance.
     * Format: provider:model (e.g., "openai:gpt-4", "gemini:gemini-pro")
     *
     * @return Full qualified chat name
     */
    String getName();

    /**
     * Ask a question and get a response from the AI model.
     *
     * @param question Question or prompt to send to the AI
     * @return AI response text
     * @throws AiChatException if the request fails
     */
    String ask(String question) throws AiChatException;

    /**
     * Ask a question with an image and get a response from the AI model.
     * The image is provided as base64-encoded bytes.
     *
     * @param question Question or prompt to send to the AI
     * @param imageBytes Image data as bytes (will be converted to base64)
     * @param mimeType MIME type of the image (e.g., "image/png", "image/jpeg")
     * @return AI response text
     * @throws AiChatException if the request fails or if the model doesn't support images
     */
    String askWithImage(String question, byte[] imageBytes, String mimeType) throws AiChatException;

    /**
     * Check if this chat instance is still valid and can be used.
     *
     * @return true if chat is ready to use
     */
    boolean isAvailable();
}
