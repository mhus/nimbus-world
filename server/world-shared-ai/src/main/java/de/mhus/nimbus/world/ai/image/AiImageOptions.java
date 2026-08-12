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
     *
     * @deprecated langchain4j dropped this builder option when the image API moved to the
     * gpt-image-1 generation; the value is no longer sent to the provider. Kept so existing
     * configurations keep deserializing.
     */
    @Deprecated
    @Builder.Default
    private String style = "vivid";

    /**
     * Response format (e.g., "url", "b64_json").
     *
     * @deprecated langchain4j dropped this builder option and now decides the transport
     * format itself. Kept so existing configurations keep deserializing.
     */
    @Deprecated
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
     * Whether the generated image should have a transparent background (a real alpha channel),
     * i.e. the subject is cut out. Required for item/decoration icons that are composited into
     * the 3D world.
     * <p>
     * This is a request for <b>native</b> transparency from the model (e.g. OpenAI
     * {@code gpt-image-1} with {@code background=transparent}). Providers that support it must
     * emit a PNG with a genuine alpha channel. Providers that do not support native transparency
     * must ignore this flag rather than fake it by making a fixed color transparent (color-keying),
     * which produces fringing and holes in the subject.
     */
    @Builder.Default
    private boolean transparentBackground = false;

    /**
     * Create default options.
     *
     * @return Default image generation options
     */
    public static AiImageOptions defaults() {
        return AiImageOptions.builder().build();
    }

    /**
     * Create options for a freestanding, square item/decoration icon with a transparent
     * background (native alpha). Intended for reality/item image generation.
     *
     * @param size Icon edge length in pixels (width == height)
     * @return Options requesting a transparent-background image of the given size
     */
    public static AiImageOptions iconTransparent(int size) {
        return AiImageOptions.builder()
                .width(size)
                .height(size)
                .transparentBackground(true)
                .build();
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
