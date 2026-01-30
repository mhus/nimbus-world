package de.mhus.nimbus.world.ai.image;

import lombok.Builder;
import lombok.Data;

/**
 * Configuration options for AI image generation.
 */
@Data
@Builder
public class AiImageOptions {

    /**
     * Default image width in pixels.
     */
    @Builder.Default
    private int width = 1024;

    /**
     * Default image height in pixels.
     */
    @Builder.Default
    private int height = 1024;

    /**
     * Image quality (e.g., "standard", "hd" for DALL-E).
     */
    @Builder.Default
    private String quality = "standard";

    /**
     * Image style (e.g., "vivid", "natural" for DALL-E).
     */
    @Builder.Default
    private String style = "vivid";

    /**
     * Response format (e.g., "url", "b64_json").
     */
    @Builder.Default
    private String responseFormat = "url";

    /**
     * Timeout in seconds for image generation requests.
     */
    @Builder.Default
    private int timeoutSeconds = 120;

    /**
     * Whether to log requests to the AI model.
     */
    @Builder.Default
    private boolean logRequests = false;

    /**
     * Number of images to generate per request.
     */
    @Builder.Default
    private int numberOfImages = 1;

    /**
     * Create default options.
     *
     * @return Default image generation options
     */
    public static AiImageOptions defaults() {
        return AiImageOptions.builder().build();
    }

    /**
     * Create options for small images (256x256).
     *
     * @return Options for small images
     */
    public static AiImageOptions small() {
        return AiImageOptions.builder()
                .width(256)
                .height(256)
                .build();
    }

    /**
     * Create options for medium images (512x512).
     *
     * @return Options for medium images
     */
    public static AiImageOptions medium() {
        return AiImageOptions.builder()
                .width(512)
                .height(512)
                .build();
    }

    /**
     * Create options for large images (1024x1024).
     *
     * @return Options for large images
     */
    public static AiImageOptions large() {
        return AiImageOptions.builder()
                .width(1024)
                .height(1024)
                .build();
    }
}
