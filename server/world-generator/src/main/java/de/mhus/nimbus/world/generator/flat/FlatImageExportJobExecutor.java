package de.mhus.nimbus.world.generator.flat;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.generator.FlatLevelImageCreator;
import de.mhus.nimbus.world.shared.generator.FlatMaterialImageCreator;
import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.generator.WFlatService;
import de.mhus.nimbus.world.shared.job.JobExecutionException;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import de.mhus.nimbus.world.shared.job.WJob;
import de.mhus.nimbus.world.shared.world.SAssetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.Optional;

/**
 * Job executor for exporting WFlat as images (level map and material map) to SAssets.
 * Creates PNG images from flat terrain data and stores them as assets.
 * <p>
 * WorldId is taken from job.getWorldId()
 * <p>
 * Required parameters:
 * - flatId: Database ID of the WFlat to export
 * - levelPath: Storage path for level image (e.g., "world/flats/images/levels.png")
 * - materialPath: Storage path for material image (e.g., "world/flats/images/materials.png")
 * <p>
 * Optional parameters:
 * - ignoreEmptyMaterial: boolean (default: false) - If true, renders black pixels where material == 0
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FlatImageExportJobExecutor implements JobExecutor {

    private static final String EXECUTOR_NAME = "flat-export-images";

    private final WFlatService flatService;
    private final SAssetService assetService;

    @Override
    public String getExecutorName() {
        return EXECUTOR_NAME;
    }

    @Override
    public JobResult execute(WJob job) throws JobExecutionException {
        try {
            log.info("Starting flat image export job: jobId={}", job.getId());

            // Get worldId from job
            String worldId = job.getWorldId();
            WorldId worldIdObj = WorldId.of(worldId).orElseThrow(
                    () -> new JobExecutionException("Invalid worldId: " + worldId)
            );

            // Extract required parameters
            String flatId = getRequiredParameter(job, "flatId");
            String levelPath = getRequiredParameter(job, "levelPath");
            String materialPath = getRequiredParameter(job, "materialPath");

            // Extract optional parameters
            boolean ignoreEmptyMaterial = getOptionalBooleanParameter(job, "ignoreEmptyMaterial", false);

            log.info("Exporting flat images: flatId={}, worldId={}, levelPath={}, materialPath={}, ignoreEmptyMaterial={}",
                    flatId, worldId, levelPath, materialPath, ignoreEmptyMaterial);

            // Load flat
            Optional<WFlat> flatOpt = flatService.findById(flatId);
            if (flatOpt.isEmpty()) {
                throw new JobExecutionException("Flat not found: " + flatId);
            }
            WFlat flat = flatOpt.get();

            log.info("Flat loaded: id={}, flatId={}, title={}, size={}x{}",
                    flat.getId(), flat.getFlatId(), flat.getTitle(), flat.getSizeX(), flat.getSizeZ());

            // Generate level image
            log.info("Generating level image...");
            byte[] levelImageBytes;
            try {
                FlatLevelImageCreator levelCreator = new FlatLevelImageCreator(flat);
                levelImageBytes = levelCreator.create(ignoreEmptyMaterial);
            } catch (Exception e) {
                throw new JobExecutionException("Failed to generate level image: " + e.getMessage(), e);
            }
            log.info("Level image generated: size={} bytes", levelImageBytes.length);

            // Generate material image
            log.info("Generating material image...");
            byte[] materialImageBytes;
            try {
                FlatMaterialImageCreator materialCreator = new FlatMaterialImageCreator(flat);
                materialImageBytes = materialCreator.create(ignoreEmptyMaterial);
            } catch (Exception e) {
                throw new JobExecutionException("Failed to generate material image: " + e.getMessage(), e);
            }
            log.info("Material image generated: size={} bytes", materialImageBytes.length);

            // Delete old level image assets if they exist
            // findByPath() automatically cleans up old duplicates and returns the newest
            log.info("Checking for existing level image assets: path={}", levelPath);
            assetService.findByPath(worldIdObj, levelPath).ifPresent(existingAsset -> {
                log.info("Found existing level image asset, will be replaced: id={}, path={}",
                        existingAsset.getId(), existingAsset.getPath());
                try {
                    assetService.delete(existingAsset);
                    log.info("Existing level image asset deleted successfully");
                } catch (Exception e) {
                    log.warn("Failed to delete existing level image asset: {}", e.getMessage());
                }
            });

            // Save level image as asset
            log.info("Saving level image to SAsset: path={}", levelPath);
            try {
                ByteArrayInputStream levelStream = new ByteArrayInputStream(levelImageBytes);
                assetService.saveAsset(
                        worldIdObj,
                        levelPath,
                        levelStream,
                        "flat-export-images-job",
                        null // No additional metadata needed
                );
            } catch (Exception e) {
                throw new JobExecutionException("Failed to save level image: " + e.getMessage(), e);
            }
            log.info("Level image saved successfully");

            // Delete old material image assets if they exist
            // findByPath() automatically cleans up old duplicates and returns the newest
            log.info("Checking for existing material image assets: path={}", materialPath);
            assetService.findByPath(worldIdObj, materialPath).ifPresent(existingAsset -> {
                log.info("Found existing material image asset, will be replaced: id={}, path={}",
                        existingAsset.getId(), existingAsset.getPath());
                try {
                    assetService.delete(existingAsset);
                    log.info("Existing material image asset deleted successfully");
                } catch (Exception e) {
                    log.warn("Failed to delete existing material image asset: {}", e.getMessage());
                }
            });

            // Save material image as asset
            log.info("Saving material image to SAsset: path={}", materialPath);
            try {
                ByteArrayInputStream materialStream = new ByteArrayInputStream(materialImageBytes);
                assetService.saveAsset(
                        worldIdObj,
                        materialPath,
                        materialStream,
                        "flat-export-images-job",
                        null // No additional metadata needed
                );
            } catch (Exception e) {
                throw new JobExecutionException("Failed to save material image: " + e.getMessage(), e);
            }
            log.info("Material image saved successfully");

            // Build success result
            String resultData = String.format(
                    "Successfully exported flat images: flatId=%s, worldId=%s, levelPath=%s (size=%d bytes), materialPath=%s (size=%d bytes), ignoreEmptyMaterial=%s",
                    flatId, worldId, levelPath, levelImageBytes.length, materialPath, materialImageBytes.length, ignoreEmptyMaterial
            );

            log.info("Flat image export job completed successfully: jobId={}, flatId={}", job.getId(), flatId);

            return JobResult.ofSuccess(resultData);

        } catch (JobExecutionException e) {
            log.error("Flat image export job failed: jobId={}", job.getId(), e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error in flat image export job: jobId={}", job.getId(), e);
            throw new JobExecutionException("Unexpected error: " + e.getMessage(), e);
        }
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
