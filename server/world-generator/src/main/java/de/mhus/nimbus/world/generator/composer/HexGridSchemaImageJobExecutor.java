package de.mhus.nimbus.world.generator.composer;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.shared.utils.TypeUtil;
import de.mhus.nimbus.world.generator.composer.build.HexComposition;
import de.mhus.nimbus.world.generator.composer.build.HexGridSchemaImageCreator;
import de.mhus.nimbus.world.shared.archive.WArchiveService;
import de.mhus.nimbus.world.shared.job.JobExecutionException;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import de.mhus.nimbus.world.shared.job.WJob;
import de.mhus.nimbus.world.shared.world.WDocument;
import de.mhus.nimbus.world.shared.world.WDocumentService;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;

import static de.mhus.nimbus.world.generator.translator.TranslateInstructionJobExecutor.COMPOSED_COLLECTION;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.core.json.JsonReadFeature;

/**
 * Job executor for creating schematic overview images from hex grid compositions.
 * Shows which hex grids are filled with which biomes using colored hexagons.
 * <p>
 * Required parameters:
 * - compositionId: ID of the composition
 * <p>
 * Image is stored in the archive at:
 * - "composites/{worldId}_{compositionId}_schema.png"
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class HexGridSchemaImageJobExecutor implements JobExecutor {

    private static final String EXECUTOR_NAME = "hex-grid-schema-image";

    private final WArchiveService archiveService;
    private final WWorldService worldService;
    private final WDocumentService documentService;

    @Override
    public String getExecutorName() {
        return EXECUTOR_NAME;
    }

    @Override
    public JobResult execute(WJob job) throws JobExecutionException {
        try {
            log.info("Starting hex grid schema image creation: jobId={}", job.getId());

            String worldId = job.getWorldId();
            WorldId.of(worldId).orElseThrow(
                    () -> new JobExecutionException("Invalid worldId: " + worldId)
            );
            WWorld world = worldService.getByWorldId(worldId).orElseThrow();

            String compositionId = getRequiredParameter(job, "compositionId");
            int hexGridSize = world.getPublicData().getHexGridSize();

            log.info("Creating schema image: worldId={}, compositionId={}, hexGridSize={}",
                    worldId, compositionId, hexGridSize);

            // Load HexComposition from document
            HexComposition composition = loadComposition(worldId, compositionId);
            if (composition.getFeatureHexGrids() == null || composition.getFeatureHexGrids().isEmpty()) {
                throw new JobExecutionException("No featureHexGrids found in composition: " + compositionId);
            }

            log.info("Loaded composition with {} featureHexGrids", composition.getFeatureHexGrids().size());

            // Create schema image
            HexGridSchemaImageCreator creator = HexGridSchemaImageCreator.builder()
                    .composition(composition)
                    .hexGridSize(hexGridSize)
                    .build();

            HexGridSchemaImageCreator.SchemaImageResult result = creator.createSchemaImage();

            if (!result.isSuccess()) {
                throw new JobExecutionException("Failed to create schema image: " + result.getErrorMessage());
            }

            log.info("Schema image created: {}x{} pixels, {} grids",
                    result.getImageWidth(), result.getImageHeight(), result.getRenderedGridCount());

            // Store in archive
            byte[] imageBytes = convertImageToBytes(result.getImage());
            String archivePath = String.format("composites/%s_%s_schema.png", worldId, compositionId);

            log.info("Storing schema image in archive: {}", archivePath);
            archiveService.archive(archivePath, new ByteArrayInputStream(imageBytes));

            String resultData = String.format(
                    "Successfully created schema image: worldId=%s, compositionId=%s, grids=%d, size=%dx%d, bytes=%d",
                    worldId, compositionId,
                    result.getRenderedGridCount(),
                    result.getImageWidth(), result.getImageHeight(),
                    imageBytes.length
            );

            log.info("Hex grid schema image creation completed: jobId={}", job.getId());
            return JobResult.success(resultData);

        } catch (JobExecutionException e) {
            log.error("Hex grid schema image creation failed: jobId={}", job.getId(), e);
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error in hex grid schema image creation: jobId={}", job.getId(), e);
            throw new JobExecutionException("Unexpected error: " + e.getMessage(), e);
        }
    }

    private byte[] convertImageToBytes(java.awt.image.BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }

    private String getRequiredParameter(WJob job, String key) throws JobExecutionException {
        String value = job.getParameters().get(key);
        if (value == null || value.isBlank()) {
            throw new JobExecutionException("Missing required parameter: " + key);
        }
        return value;
    }

    private HexComposition loadComposition(String worldId, String compositionId) throws JobExecutionException {
        try {
            WorldId wid = WorldId.of(worldId)
                    .orElseThrow(() -> new JobExecutionException("Invalid worldId: " + worldId));

            Optional<WDocument> documentOpt = documentService.findByDocumentId(wid, COMPOSED_COLLECTION, compositionId);
            if (documentOpt.isEmpty()) {
                throw new JobExecutionException("Composition document not found: " + compositionId);
            }

            WDocument document = documentOpt.get();

            ObjectMapper mapper = JsonMapper.builder()
                    .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                    .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .build();

            HexComposition composition = mapper.readValue(document.getContent(), HexComposition.class);

            // Convert featureHexGrids List back to featureHexGridRegistry Map
            if (composition.getFeatureHexGrids() != null && !composition.getFeatureHexGrids().isEmpty()) {
                var registry = composition.getFeatureHexGridRegistry();
                for (var grid : composition.getFeatureHexGrids()) {
                    String key = TypeUtil.toStringHexCoord(grid.getCoordinate());
                    registry.put(key, grid);
                }
            }

            log.info("Successfully loaded HexComposition from document: {}", compositionId);
            return composition;

        } catch (JobExecutionException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to load composition: {}", compositionId, e);
            throw new JobExecutionException("Failed to load composition: " + e.getMessage(), e);
        }
    }
}
