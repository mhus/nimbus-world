package de.mhus.nimbus.world.generator.composer;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.generator.composer.build.DatabaseFlatProvider;
import de.mhus.nimbus.world.generator.composer.build.HexGridCompositeImageCreator;
import de.mhus.nimbus.world.shared.archive.WArchiveService;
import de.mhus.nimbus.world.shared.generator.WFlatService;
import de.mhus.nimbus.world.shared.job.JobExecutionException;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import de.mhus.nimbus.world.shared.job.WJob;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Job executor for creating composite images from hex grids.
 * Loads all flats matching a pattern and creates level and material composite images
 * showing the complete world.
 * <p>
 * Required parameters:
 * - compositionId: ID of the composition (used in archive path)
 * <p>
 * Optional parameters:
 * - drawGridLines: Whether to draw hex grid lines (default: false)
 * - flatIdSuffix: Prefix pattern for flatIds (e.g., "genesis_" matches "genesis_0_0", "genesis_1_0", etc.)
 * <p>
 * Images are stored in the archive at:
 * - "composites/{worldId}_{compositionId}_level.png"
 * - "composites/{worldId}_{compositionId}_material.png"
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class HexGridCompositeImageJobExecutor implements JobExecutor {

    private static final String EXECUTOR_NAME = "hex-grid-composite-image";

    private final WFlatService flatService;
    private final WArchiveService archiveService;
    private final WWorldService worldService;

    @Override
    public String getExecutorName() {
        return EXECUTOR_NAME;
    }

    @Override
    public JobResult execute(WJob job) throws JobExecutionException {
        try {
            log.info("Starting hex grid composite image creation: jobId={}", job.getId());

            // Get worldId from job
            String worldId = job.getWorldId();
            WorldId.of(worldId).orElseThrow(
                    () -> new JobExecutionException("Invalid worldId: " + worldId)
            );
            WWorld world = worldService.getByWorldId(worldId).orElseThrow();

            // Extract required parameters
            String compositionId = getRequiredParameter(job, "compositionId");

            // Extract optional parameters
            boolean drawGridLines = getOptionalBooleanParameter(job, "drawGridLines", false);
            String flatIdSuffix = getOptionalParameter(job, "flatIdSuffix", "genesis_");

            int flatSize = world.getPublicData().getHexGridSize();

            log.info("Creating composite images: worldId={}, compositionId={}, flatIdSuffix={}, flatSize={}, drawGridLines={}",
                    worldId, compositionId, flatIdSuffix, flatSize, drawGridLines);

            // Create database flat provider for lazy loading
            DatabaseFlatProvider flatProvider = new DatabaseFlatProvider(flatService, worldId, flatIdSuffix);

            // Check that we have grids to render
            int gridCount = flatProvider.getCoordinates().size();
            if (gridCount == 0) {
                throw new JobExecutionException("No flats found for worldId=" + worldId + ", flatIdSuffix=" + flatIdSuffix);
            }

            log.info("Found {} hex grids to render", gridCount);

            // Create composite images using the provider
            HexGridCompositeImageCreator creator = HexGridCompositeImageCreator.builder()
                    .flatProvider(flatProvider)
                    .flatSize(flatSize)
                    .drawGridLines(drawGridLines)
                    .build();

            HexGridCompositeImageCreator.CompositeImageResult result = creator.createCompositeImages();

            if (!result.isSuccess()) {
                throw new JobExecutionException("Failed to create composite images: " + result.getErrorMessage());
            }

            log.info("Composite images created: {}x{} pixels, rendered {}/{} grids",
                    result.getImageWidth(), result.getImageHeight(),
                    result.getRenderedGridCount(), result.getTotalGridCount());

            // Convert images to PNG byte arrays
            byte[] levelImageBytes = convertImageToBytes(result.getLevelImage());
            byte[] materialImageBytes = convertImageToBytes(result.getMaterialImage());

            // Store in archive
            String levelPath = String.format("composites/%s_%s_level.png", worldId, compositionId);
            String materialPath = String.format("composites/%s_%s_material.png", worldId, compositionId);

            log.info("Storing level composite in archive: {}", levelPath);
            archiveService.archive(levelPath, new ByteArrayInputStream(levelImageBytes));

            log.info("Storing material composite in archive: {}", materialPath);
            archiveService.archive(materialPath, new ByteArrayInputStream(materialImageBytes));

            // Build success result
            String resultData = String.format(
                    "Successfully created composite images: worldId=%s, compositionId=%s, grids=%d/%d, size=%dx%d, levelSize=%d bytes, materialSize=%d bytes",
                    worldId, compositionId,
                    result.getRenderedGridCount(), result.getTotalGridCount(),
                    result.getImageWidth(), result.getImageHeight(),
                    levelImageBytes.length, materialImageBytes.length
            );

            log.info("Hex grid composite image creation completed successfully: jobId={}", job.getId());

            return JobResult.success(resultData);

        } catch (JobExecutionException e) {
            log.error("Hex grid composite image creation failed: jobId={}", job.getId(), e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error in hex grid composite image creation: jobId={}", job.getId(), e);
            throw new JobExecutionException("Unexpected error: " + e.getMessage(), e);
        }
    }

    /**
     * Converts a BufferedImage to PNG byte array.
     */
    private byte[] convertImageToBytes(java.awt.image.BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }

    /**
     * Get required parameter from job.
     */
    private String getRequiredParameter(WJob job, String key) throws JobExecutionException {
        String value = job.getParameters().get(key);
        if (value == null || value.isBlank()) {
            throw new JobExecutionException("Missing required parameter: " + key);
        }
        return value;
    }

    /**
     * Get optional  parameter from job.
     */
    private String getOptionalParameter(WJob job, String key, String defaultValue) {
        String value = job.getParameters().get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }

    /**
     * Get optional boolean parameter from job.
     */
    private boolean getOptionalBooleanParameter(WJob job, String key, boolean defaultValue) {
        String value = job.getParameters().get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }
}
