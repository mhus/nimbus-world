package de.mhus.nimbus.world.ai.image.gemini;

import de.mhus.nimbus.world.ai.image.AiImage;
import de.mhus.nimbus.world.ai.image.AiImageException;
import de.mhus.nimbus.world.ai.image.AiImageModel;
import de.mhus.nimbus.world.ai.image.AiImageOptions;
import de.mhus.nimbus.world.ai.image.BackgroundRemover;
import de.mhus.nimbus.world.ai.model.SimpleRateLimiter;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;

/**
 * Google Gemini implementation of {@link AiImageModel}.
 * <p>
 * Talks directly to the Gemini REST API ({@code generativelanguage.googleapis.com}) because
 * langchain4j's Gemini integration does not offer image generation. The image is returned inline as
 * base64 in the {@code generateContent} response.
 * <p>
 * Gemini image models do <b>not</b> produce a native alpha channel — when asked for a "transparent"
 * background they render an opaque background (or even paint a checkerboard). Therefore, when
 * {@link AiImageOptions#isTransparentBackground()} is set, this model asks Gemini for a flat solid
 * background and then cuts it out with {@link BackgroundRemover} to obtain real transparency.
 */
@Slf4j
public class GeminiImageModelImpl implements AiImageModel {

    private static final String API_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";

    /**
     * Appended to the prompt when a transparent background is requested: steer Gemini towards a
     * flat, uniform background that {@link BackgroundRemover} can reliably cut out.
     */
    private static final String FLAT_BACKGROUND_HINT =
            " Place the subject centered and fully visible on a perfectly flat, uniform, solid pure"
            + " white (#FFFFFF) background that fills the whole frame edge to edge, with NO border,"
            + " NO frame, NO vignette, NO drop shadow and NO gradient.";

    private final String name;
    private final String modelName;
    private final String apiKey;
    private final AiImageOptions options;
    private final ObjectMapper objectMapper;
    private final SimpleRateLimiter rateLimiter;
    private final int backgroundThreshold;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    public GeminiImageModelImpl(String name, String modelName, String apiKey, AiImageOptions options,
                                ObjectMapper objectMapper, SimpleRateLimiter rateLimiter,
                                int backgroundThreshold) {
        this.name = name;
        this.modelName = modelName;
        this.apiKey = apiKey;
        this.options = options;
        this.objectMapper = objectMapper;
        this.rateLimiter = rateLimiter;
        this.backgroundThreshold = backgroundThreshold;
    }

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
        boolean transparent = options.isTransparentBackground();
        String effectivePrompt = transparent ? prompt + FLAT_BACKGROUND_HINT : prompt;

        try {
            if (rateLimiter != null) {
                rateLimiter.waitIfNeeded();
            }

            byte[] imageBytes = requestImage(effectivePrompt);

            if (rateLimiter != null) {
                rateLimiter.recordRequest();
            }

            if (transparent) {
                imageBytes = BackgroundRemover.removePng(imageBytes, backgroundThreshold);
            }

            return toAiImage(imageBytes);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiImageException("Image generation interrupted", e);
        } catch (AiImageException e) {
            throw e;
        } catch (Exception e) {
            log.error("Gemini image generation failed: model={}, prompt='{}'", modelName, prompt, e);
            throw new AiImageException("Gemini image generation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isAvailable() {
        return apiKey != null && !apiKey.isBlank();
    }

    private byte[] requestImage(String prompt) throws Exception {
        // Typed request body -> JSON (no manual string building).
        GenerateContentRequest body = new GenerateContentRequest(
                List.of(new Content(List.of(new Part(prompt)))),
                new GenerationConfig(List.of("IMAGE")));
        String json = objectMapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + modelName + ":generateContent"))
                .timeout(Duration.ofSeconds(options.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey) // key in header, never in the URL/logs
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new AiImageException("Gemini API HTTP " + response.statusCode() + ": "
                    + abbreviate(response.body()));
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode parts = root.path("candidates").path(0).path("content").path("parts");
        if (parts.isArray()) {
            for (JsonNode part : parts) {
                JsonNode data = part.path("inlineData").path("data");
                if (!data.isMissingNode() && !data.asString("").isBlank()) {
                    return Base64.getDecoder().decode(data.asString(""));
                }
            }
        }
        // No image -> surface the block reason / any text the model returned instead.
        String blockReason = root.path("promptFeedback").path("blockReason").asString("");
        String text = parts.isArray() && parts.size() > 0 ? parts.path(0).path("text").asString("") : "";
        throw new AiImageException("Gemini returned no image"
                + (blockReason.isBlank() ? "" : " (blocked: " + blockReason + ")")
                + (text.isBlank() ? "" : ": " + abbreviate(text)));
    }

    private AiImage toAiImage(byte[] imageBytes) {
        AiImage.AiImageBuilder builder = AiImage.builder()
                .bytes(imageBytes)
                .mimeType("image/png");
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (img != null) {
                builder.width(img.getWidth()).height(img.getHeight());
            }
        } catch (Exception e) {
            log.warn("Could not read generated image dimensions", e);
        }
        return builder.build();
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 300 ? s : s.substring(0, 300) + "…";
    }

    // ---- Typed request DTOs (serialized to the Gemini generateContent body) ----

    private record GenerateContentRequest(List<Content> contents, GenerationConfig generationConfig) {
    }

    private record Content(List<Part> parts) {
    }

    private record Part(String text) {
    }

    private record GenerationConfig(List<String> responseModalities) {
    }
}
