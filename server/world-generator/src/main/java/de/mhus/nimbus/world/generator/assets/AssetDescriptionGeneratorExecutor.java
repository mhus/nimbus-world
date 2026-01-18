package de.mhus.nimbus.world.generator.assets;

import de.mhus.nimbus.shared.service.SSettingsService;
import de.mhus.nimbus.shared.settings.SettingDouble;
import de.mhus.nimbus.shared.settings.SettingInteger;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.ai.model.AiChat;
import de.mhus.nimbus.world.ai.model.AiChatException;
import de.mhus.nimbus.world.ai.model.AiChatOptions;
import de.mhus.nimbus.world.ai.model.AiModelService;
import de.mhus.nimbus.world.shared.job.JobExecutionException;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import de.mhus.nimbus.world.shared.job.WJob;
import de.mhus.nimbus.world.shared.world.AssetMetadata;
import de.mhus.nimbus.world.shared.world.SAsset;
import de.mhus.nimbus.world.shared.world.SAssetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

/**
 * Job executor that generates AI descriptions for assets without descriptions.
 * Processes assets sequentially to manage memory and respect API rate limits.
 * Only processes image files below a configurable size limit.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AssetDescriptionGeneratorExecutor implements JobExecutor {

    private static final String EXECUTOR_NAME = "asset-description-generator";
    private static final long DEFAULT_MAX_SIZE_BYTES = 5 * 1024 * 1024; // 5MB
    private static final List<String> SUPPORTED_EXTENSIONS = List.of(".png", ".jpg", ".jpeg", ".gif", ".bmp");

    private final SAssetService assetService;
    private final AiModelService aiModelService;
    private final SSettingsService settingsService;

    @Value("${asset.description.max-size-bytes:5242880}")
    private long maxSizeBytes;

    @Value("${asset.description.ai-model:default:generate}")
    private String aiModelName;

    // AI description generation settings (loaded dynamically from SSettingsService)
    private SettingInteger maxTokens;
    private SettingDouble temperature;
    private SettingInteger timeoutSeconds;
    private SettingInteger maxChars;

    @PostConstruct
    private void initSettings() {
        maxTokens = settingsService.getInteger("asset.description.max-tokens", 1000);
        temperature = settingsService.getDouble("asset.description.temperature", 0.7);
        timeoutSeconds = settingsService.getInteger("asset.description.timeout-seconds", 120);
        maxChars = settingsService.getInteger("asset.description.max-chars", 1000);

        log.info("Asset description generation settings initialized: maxTokens={}, temperature={}, timeoutSeconds={}, maxChars={}",
                maxTokens.get(), temperature.get(), timeoutSeconds.get(), maxChars.get());
    }

    @Override
    public String getExecutorName() {
        return EXECUTOR_NAME;
    }

    @Override
    public JobResult execute(WJob job) throws JobExecutionException {
        try {
            // Get worldId from job (not from parameters)
            String worldIdStr = job.getWorldId();
            if (worldIdStr == null || worldIdStr.isBlank()) {
                throw new JobExecutionException("Job has no worldId");
            }

            WorldId worldId = WorldId.of(worldIdStr)
                    .orElseThrow(() -> new JobExecutionException("Invalid worldId: " + worldIdStr));

            // Check if single asset path is specified
            String assetPath = job.getParameters().get("assetPath");
            if (assetPath != null && !assetPath.isBlank()) {
                return processSingleAsset(worldId, assetPath);
            }

            return processBulkAssets(worldId);

        } catch (Exception e) {
            log.error("Asset description generation failed", e);
            throw new JobExecutionException("Generation failed: " + e.getMessage(), e);
        }
    }

    /**
     * Process a single asset by path.
     * Always generates description even if one already exists.
     */
    private JobResult processSingleAsset(WorldId worldId, String assetPath) throws JobExecutionException {
        log.info("Processing single asset: world={}, path={}", worldId.getId(), assetPath);

        // Load asset by path
        Optional<SAsset> assetOpt = assetService.findByPath(worldId, assetPath);
        if (assetOpt.isEmpty()) {
            throw new JobExecutionException("Asset not found: " + assetPath);
        }

        SAsset asset = assetOpt.get();

        // Create AI chat instance
        Optional<AiChat> aiChatOpt = aiModelService.createChat(aiModelName, createChatOptions());
        if (aiChatOpt.isEmpty()) {
            throw new JobExecutionException("Failed to create AI chat: " + aiModelName);
        }
        AiChat aiChat = aiChatOpt.get();
        log.info("Using AI model: {}", aiChat.getName());

        try {
            // Process asset with force regeneration
            boolean success = processAsset(asset, aiChat, true);

            if (success) {
                String message = String.format("Generated description for asset: %s", assetPath);
                log.info(message);
                return JobResult.ofSuccess(message);
            } else {
                String message = String.format("Failed to generate description for asset: %s", assetPath);
                log.warn(message);
                return JobResult.ofSuccess(message);
            }

        } catch (Exception e) {
            log.error("Failed to process single asset: path={}", assetPath, e);
            throw new JobExecutionException("Failed to process asset: " + e.getMessage(), e);
        }
    }

    /**
     * Process all assets in a world.
     * Only generates descriptions for assets without existing ones.
     */
    private JobResult processBulkAssets(WorldId worldId) throws JobExecutionException {
        log.info("Starting bulk asset description generation for world: {}", worldId.getId());

        // Load all assets for the world
        List<SAsset> assets = assetService.findByWorldId(worldId);
        log.info("Found {} assets in world {}", assets.size(), worldId.getId());

        // Create AI chat instance
        Optional<AiChat> aiChatOpt = aiModelService.createChat(aiModelName, createChatOptions());
        if (aiChatOpt.isEmpty()) {
            throw new JobExecutionException("Failed to create AI chat: " + aiModelName);
        }
        AiChat aiChat = aiChatOpt.get();
        log.info("Using AI model: {}", aiChat.getName());

        // Process assets sequentially
        int processed = 0;
        int generated = 0;
        int skipped = 0;
        int errors = 0;

        for (SAsset asset : assets) {
            try {
                boolean descriptionGenerated = processAsset(asset, aiChat, false);
                if (descriptionGenerated) {
                    generated++;
                } else {
                    skipped++;
                }
                processed++;

                if (processed % 10 == 0) {
                    log.info("Progress: {}/{} assets processed", processed, assets.size());
                }

            } catch (Exception e) {
                log.error("Failed to process asset: id={}, path={}", asset.getId(), asset.getPath(), e);
                errors++;
            }
        }

        String resultMessage = String.format(
                "Processed %d/%d assets: %d generated, %d skipped, %d errors",
                processed, assets.size(), generated, skipped, errors
        );

        log.info("Asset description generation completed: {}", resultMessage);
        return JobResult.ofSuccess(resultMessage);
    }

    /**
     * Process a single asset.
     * Returns true if a description was generated, false if skipped.
     *
     * @param asset The asset to process
     * @param aiChat The AI chat instance
     * @param forceRegeneration If true, regenerates description even if one exists
     */
    private boolean processAsset(SAsset asset, AiChat aiChat, boolean forceRegeneration) throws Exception {
        // Check if description already exists (unless force regeneration)
        if (!forceRegeneration &&
            asset.getPublicData() != null &&
            asset.getPublicData().getDescription() != null &&
            !asset.getPublicData().getDescription().isBlank()) {
            log.debug("Asset already has description: {}", asset.getPath());
            return false;
        }

        // Check file extension
        if (!isSupportedImageType(asset.getPath())) {
            log.debug("Unsupported file type: {}", asset.getPath());
            return false;
        }

        // Check file size
        if (asset.getSize() > maxSizeBytes) {
            log.debug("Asset too large ({} bytes): {}", asset.getSize(), asset.getPath());
            return false;
        }

        // Load asset content
        InputStream contentStream = assetService.loadContent(asset);
        if (contentStream == null) {
            log.warn("Failed to load content for asset: {}", asset.getPath());
            return false;
        }

        // Read all bytes
        byte[] contentBytes = contentStream.readAllBytes();
        contentStream.close();

        // Verify it's a valid image
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(contentBytes));
            if (image == null) {
                log.warn("Not a valid image: {}", asset.getPath());
                return false;
            }
        } catch (Exception e) {
            log.warn("Failed to read image: {}", asset.getPath(), e);
            return false;
        }

        // Generate description using AI
        String description = generateDescription(asset.getPath(), contentBytes, aiChat);
        if (description == null || description.isBlank()) {
            log.warn("AI generated empty description for: {}", asset.getPath());
            return false;
        }

        // Update asset metadata
        AssetMetadata metadata = asset.getPublicData();
        if (metadata == null) {
            metadata = new AssetMetadata();
        }

        String action = (metadata.getDescription() != null && !metadata.getDescription().isBlank())
                ? "Regenerated" : "Generated";
        metadata.setDescription(description);

        assetService.updateMetadata(asset, metadata);
        log.info("{} description for asset: {} - \"{}\"",
                action, asset.getPath(), description.substring(0, Math.min(50, description.length())));

        return true;
    }

    /**
     * Generate description for an image using AI.
     * Uses AI vision capabilities to analyze the actual image content.
     */
    private String generateDescription(String path, byte[] imageBytes, AiChat aiChat) throws AiChatException {
        // Extract filename for context
        String filename = path.substring(path.lastIndexOf('/') + 1);

        // Build prompt
        String prompt = buildDescriptionPrompt(filename);

        // Determine MIME type from file extension
        String mimeType = getMimeType(path);

        // Use AI vision to analyze the actual image
        return aiChat.askWithImage(prompt, imageBytes, mimeType);
    }

    /**
     * Build prompt for description generation based on filename and image content.
     */
    private String buildDescriptionPrompt(String filename) {
        return String.format(
                "Analyze this game asset image (filename: '%s') and generate a concise, single-sentence description (maximum %d characters). " +
                "Describe what you see in the image - the visual appearance, colors, shapes, and what game element it represents. " +
                "Return ONLY the complete description text, no quotes, no additional explanation.",
                filename,
                maxChars.get()
        );
    }

    /**
     * Check if file extension is supported.
     */
    private boolean isSupportedImageType(String path) {
        if (path == null) return false;
        String lowerPath = path.toLowerCase();
        return SUPPORTED_EXTENSIONS.stream().anyMatch(lowerPath::endsWith);
    }

    /**
     * Get MIME type from file path extension.
     */
    private String getMimeType(String path) {
        if (path == null) return "application/octet-stream";
        String lowerPath = path.toLowerCase();

        if (lowerPath.endsWith(".png")) return "image/png";
        if (lowerPath.endsWith(".jpg") || lowerPath.endsWith(".jpeg")) return "image/jpeg";
        if (lowerPath.endsWith(".gif")) return "image/gif";
        if (lowerPath.endsWith(".bmp")) return "image/bmp";
        if (lowerPath.endsWith(".webp")) return "image/webp";

        return "application/octet-stream";
    }

    /**
     * Create AI chat options for description generation.
     * Uses settings from SSettingsService for runtime configurability.
     */
    private AiChatOptions createChatOptions() {
        return AiChatOptions.builder()
                .temperature(temperature.get())
                .maxTokens(maxTokens.get())
                .timeoutSeconds(timeoutSeconds.get())
                .systemMessage(String.format(
                        "You are a helpful assistant that generates concise descriptions for game assets. " +
                        "Keep descriptions under %d characters and focus on the asset's visual appearance and purpose. " +
                        "Always complete your sentences. Return ONLY the description text.",
                        maxChars.get()))
                .build();
    }
}
