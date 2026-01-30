package de.mhus.nimbus.world.ai.image.openai;

import de.mhus.nimbus.world.ai.image.AiImage;
import de.mhus.nimbus.world.ai.image.AiImageException;
import de.mhus.nimbus.world.ai.image.AiImageModel;
import de.mhus.nimbus.world.ai.image.AiImageOptions;
import de.mhus.nimbus.world.ai.model.SimpleRateLimiter;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.model.image.ImageModel;
import dev.langchain4j.model.output.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

/**
 * OpenAI DALL-E implementation of AiImageModel.
 * Wraps Langchain4j's OpenAiImageModel with rate limiting.
 */
@RequiredArgsConstructor
@Slf4j
public class OpenAiImageModelImpl implements AiImageModel {

    private final String name;
    private final ImageModel imageModel;
    private final AiImageOptions options;
    private final SimpleRateLimiter rateLimiter;

    @Override
    public String getName() {
        return name;
    }

    @Override
    public AiImage generate(String prompt) throws AiImageException {
        return generate(prompt, options.getWidth(), options.getHeight());
    }

    @Override
    public AiImage generate(String prompt, int width, int height) throws AiImageException {
        if (prompt == null || prompt.isBlank()) {
            throw new AiImageException("Prompt cannot be empty");
        }

        try {
            // Apply rate limiting
            if (rateLimiter != null) {
                rateLimiter.waitIfNeeded();
            }

            log.debug("Generating image with prompt: {}", prompt);

            // Generate image via Langchain4j
            Response<Image> response = imageModel.generate(prompt);

            // Record successful request
            if (rateLimiter != null) {
                rateLimiter.recordRequest();
            }

            if (response == null || response.content() == null) {
                throw new AiImageException("Image model returned null response");
            }

            Image image = response.content();

            // Convert to AiImage
            return convertToAiImage(image, width, height);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Image generation interrupted", e);
            throw new AiImageException("Image generation interrupted", e);
        } catch (Exception e) {
            log.error("Failed to generate image: prompt={}", prompt, e);
            throw new AiImageException("Image generation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isAvailable() {
        return imageModel != null;
    }

    /**
     * Convert Langchain4j Image to AiImage.
     *
     * @param image Langchain4j image
     * @param width Expected width (may differ from actual)
     * @param height Expected height (may differ from actual)
     * @return AiImage instance
     * @throws AiImageException if conversion fails
     */
    private AiImage convertToAiImage(Image image, int width, int height) throws AiImageException {
        AiImage.AiImageBuilder builder = AiImage.builder()
                .width(width)
                .height(height)
                .mimeType("image/png");

        byte[] imageBytes = null;

        // Check if image has URL
        if (image.url() != null && !image.url().toString().isBlank()) {
            String url = image.url().toString();
            builder.url(url);

            // If response format is URL, download the image bytes
            if ("url".equals(options.getResponseFormat())) {
                try {
                    imageBytes = downloadImage(url);
                    builder.bytes(imageBytes);
                } catch (Exception e) {
                    log.warn("Failed to download image from URL: {}", url, e);
                    // Keep URL, bytes will be null
                }
            }
        }

        // Check if image has base64 data
        if (image.base64Data() != null && !image.base64Data().isBlank()) {
            try {
                imageBytes = Base64.getDecoder().decode(image.base64Data());
                builder.bytes(imageBytes);
            } catch (IllegalArgumentException e) {
                log.error("Failed to decode base64 image data", e);
            }
        }

        // Read actual dimensions from image bytes if available
        if (imageBytes != null && imageBytes.length > 0) {
            try {
                ByteArrayInputStream inputStream = new ByteArrayInputStream(imageBytes);
                BufferedImage bufferedImage = ImageIO.read(inputStream);
                if (bufferedImage != null) {
                    int actualWidth = bufferedImage.getWidth();
                    int actualHeight = bufferedImage.getHeight();
                    builder.width(actualWidth);
                    builder.height(actualHeight);
                    log.debug("Actual image dimensions: {}x{} (requested: {}x{})",
                            actualWidth, actualHeight, width, height);
                }
            } catch (Exception e) {
                log.warn("Failed to read actual image dimensions, using requested dimensions", e);
                // Keep requested dimensions as fallback
            }
        }

        // Add revised prompt if available
        if (image.revisedPrompt() != null) {
            builder.revisedPrompt(image.revisedPrompt());
        }

        return builder.build();
    }

    /**
     * Download image bytes from URL.
     *
     * @param url Image URL
     * @return Image bytes
     * @throws IOException if download fails
     * @throws InterruptedException if download is interrupted
     */
    private byte[] downloadImage(String url) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200) {
            throw new IOException("Failed to download image: HTTP " + response.statusCode());
        }

        return response.body();
    }
}
