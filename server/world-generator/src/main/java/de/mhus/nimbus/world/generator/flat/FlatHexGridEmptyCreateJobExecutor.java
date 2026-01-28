package de.mhus.nimbus.world.generator.flat;

import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.job.JobExecutionException;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import de.mhus.nimbus.world.shared.job.WJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Job executor for creating empty HexGrid-based WFlat.
 * Creates a flat terrain where:
 * - Positions inside the HexGrid are marked with NOT_SET_MUTABLE (255) at level 0
 * - Positions outside the HexGrid are marked with NOT_SET (0) at level 0
 * - All levels are initialized to 0 (no BEDROCK)
 * - unknownProtected is set to true (only HexGrid positions can be modified)
 * - Ready for terrain generation with no pre-existing terrain data
 *
 * Required parameters:
 * - layerName: Name of the GROUND layer (for layerDataId)
 * - hexQ: HexGrid Q coordinate (axial)
 * - hexR: HexGrid R coordinate (axial)
 *
 * Optional parameters:
 * - flatId: Identifier for the new WFlat (if not provided, UUID will be generated)
 * - title: Display title for the flat
 * - description: Description text for the flat
 * - paletteName: Name of predefined material palette to apply ("nimbus" or "legacy")
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FlatHexGridEmptyCreateJobExecutor implements JobExecutor {

    private static final String EXECUTOR_NAME = "flat-create-hexgrid-empty";

    private final FlatCreateService flatCreateService;
    private final FlatMaterialService flatMaterialService;

    @Override
    public String getExecutorName() {
        return EXECUTOR_NAME;
    }

    @Override
    public JobResult execute(WJob job) throws JobExecutionException {
        try {
            log.info("Starting flat hexgrid empty create job: jobId={}", job.getId());

            // Get worldId from job
            String worldId = job.getWorldId();

            // Extract required parameters
            String layerName = getRequiredParameter(job, "layerName");
            int hexQ = getRequiredIntParameter(job, "hexQ");
            int hexR = getRequiredIntParameter(job, "hexR");

            // Extract optional parameters
            String flatId = getOptionalParameter(job, "flatId", java.util.UUID.randomUUID().toString());
            String title = getOptionalParameter(job, "title", null);
            String description = getOptionalParameter(job, "description", null);
            String paletteName = getOptionalParameter(job, "paletteName", null);

            log.info("Creating empty HexGrid flat: worldId={}, layerName={}, flatId={}, hex=({},{}), title={}, description={}, palette={}",
                    worldId, layerName, flatId, hexQ, hexR, title, description, paletteName);

            // Execute create with auto-calculated size/mount
            WFlat flat = flatCreateService.createEmptyHexGridFlat(
                    worldId, layerName, flatId,
                    hexQ, hexR, title, description
            );

            // Apply material palette if specified
            if (paletteName != null && !paletteName.isBlank()) {
                log.info("Applying material palette: flatId={}, paletteName={}", flat.getId(), paletteName);
                try {
                    flatMaterialService.setPalette(flat.getId(), paletteName);
                    log.info("Material palette applied successfully: flatId={}, paletteName={}", flat.getId(), paletteName);
                } catch (IllegalArgumentException e) {
                    log.warn("Failed to apply material palette: {}", e.getMessage());
                    // Continue - don't fail the job if palette application fails
                }
            }

            // Build successful result
            String hexCoords = flat.getHexGrid() != null
                ? String.format("(%d,%d)", flat.getHexGrid().getQ(), flat.getHexGrid().getR())
                : "(unknown)";

            String resultData = String.format(
                    "Successfully created empty HexGrid flat: id=%s, flatId=%s, worldId=%s, layerName=%s, hex=%s, size=%dx%d, mount=(%d,%d), palette=%s, unknownProtected=true, allLevels=0",
                    flat.getId(), flatId, worldId, layerName, hexCoords,
                    flat.getSizeX(), flat.getSizeZ(), flat.getMountX(), flat.getMountZ(),
                    paletteName != null ? paletteName : "none"
            );

            log.info("Flat hexgrid empty create completed successfully: flatId={}, id={}", flatId, flat.getId());
            return JobResult.success(resultData);

        } catch (IllegalArgumentException e) {
            log.error("Invalid parameters for flat hexgrid empty create", e);
            throw new JobExecutionException("Invalid parameters: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Flat hexgrid empty create failed", e);
            throw new JobExecutionException("Create failed: " + e.getMessage(), e);
        }
    }

    /**
     * Get required string parameter from job.
     */
    private String getRequiredParameter(WJob job, String paramName) throws JobExecutionException {
        String value = job.getParameters().get(paramName);
        if (value == null || value.isBlank()) {
            throw new JobExecutionException("Missing required parameter: " + paramName);
        }
        return value;
    }

    /**
     * Get optional string parameter from job with default value.
     */
    private String getOptionalParameter(WJob job, String paramName, String defaultValue) {
        String value = job.getParameters().get(paramName);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }

    /**
     * Get required integer parameter from job.
     */
    private int getRequiredIntParameter(WJob job, String paramName) throws JobExecutionException {
        String value = getRequiredParameter(job, paramName);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new JobExecutionException("Invalid integer parameter '" + paramName + "': " + value);
        }
    }
}
