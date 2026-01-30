package de.mhus.nimbus.world.ai.image;

import lombok.Builder;
import lombok.Data;

/**
 * Represents a generated image from an AI model.
 * Contains either the image bytes or a URL to the image.
 */
@Data
@Builder
public class AiImage {

    /**
     * Image data as bytes (if available).
     */
    private byte[] bytes;

    /**
     * URL to the generated image (if available).
     * Can be a remote URL or a local file URL.
     */
    private String url;

    /**
     * MIME type of the image (e.g., "image/png", "image/jpeg").
     */
    private String mimeType;

    /**
     * Width of the image in pixels.
     */
    private int width;

    /**
     * Height of the image in pixels.
     */
    private int height;

    /**
     * Revised prompt used by the model (if provided by the API).
     * Some models refine the input prompt for better results.
     */
    private String revisedPrompt;

    /**
     * Check if image data is available as bytes.
     *
     * @return true if bytes are available
     */
    public boolean hasBytes() {
        return bytes != null && bytes.length > 0;
    }

    /**
     * Check if image is available via URL.
     *
     * @return true if URL is available
     */
    public boolean hasUrl() {
        return url != null && !url.isBlank();
    }
}
