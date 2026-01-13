package de.mhus.nimbus.world.generator.flat;

import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.job.JobExecutionException;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import de.mhus.nimbus.world.shared.job.WJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Job executor for importing HexGrid-based WFlat from layer.
 * Imports all columns from the layer and sets them to material 255 (UNKNOWN_NOT_PROTECTED),
 * then sets positions outside the HexGrid to material 0 (UNKNOWN_PROTECTED).
 * Sets unknownProtected = true (only HexGrid positions can be modified).
 *
 * WorldId is taken from job.getWorldId()
 *
 * Required parameters:
 * - layerName: Name of the GROUND layer to import from
 * - sizeX: Width of the flat (1-800)
 * - sizeZ: Height of the flat (1-800)
 * - mountX: Mount X position (start position in layer)
 * - mountZ: Mount Z position (start position in layer)
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
public class FlatHexGridImportJobExecutor implements JobExecutor {

    private static final String EXECUTOR_NAME = "flat-import-hexgrid";

    private final FlatCreateService flatCreateService;
    private final FlatMaterialService flatMaterialService;

    @Override
    public String getExecutorName() {
        return EXECUTOR_NAME;
    }

    @Override
    public JobResult execute(WJob job) throws JobExecutionException {
        try {
            log.info("Starting flat hexgrid import job: jobId={}", job.getId());

            // Get worldId from job
            String worldId = job.getWorldId();

            // Extract and validate required parameters
            String layerName = getRequiredParameter(job, "layerName");
            int sizeX = getRequiredIntParameter(job, "sizeX");
            int sizeZ = getRequiredIntParameter(job, "sizeZ");
            int mountX = getRequiredIntParameter(job, "mountX");
            int mountZ = getRequiredIntParameter(job, "mountZ");
            int hexQ = getRequiredIntParameter(job, "hexQ");
            int hexR = getRequiredIntParameter(job, "hexR");

            // Extract optional parameters
            String flatId = getOptionalParameter(job, "flatId", java.util.UUID.randomUUID().toString());
            String title = getOptionalParameter(job, "title", null);
            String description = getOptionalParameter(job, "description", null);
            String paletteName = getOptionalParameter(job, "paletteName", null);

            // Validate size parameters
            if (sizeX <= 0 || sizeX > 800) {
                throw new JobExecutionException("sizeX must be between 1 and 800, got: " + sizeX);
            }
            if (sizeZ <= 0 || sizeZ > 800) {
                throw new JobExecutionException("sizeZ must be between 1 and 800, got: " + sizeZ);
            }

            log.info("Importing HexGrid flat: worldId={}, layerName={}, flatId={}, size={}x{}, mount=({},{}), hex=({},{}), title={}, description={}, palette={}",
                    worldId, layerName, flatId, sizeX, sizeZ, mountX, mountZ, hexQ, hexR, title, description, paletteName);

            // Execute import
            WFlat flat = flatCreateService.importHexGridFlat(
                    worldId, layerName, flatId,
                    sizeX, sizeZ, mountX, mountZ,
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

            // Build success result
            String resultData = String.format(
                    "Successfully imported HexGrid flat: id=%s, flatId=%s, worldId=%s, layerName=%s, size=%dx%d, mount=(%d,%d), hex=(%d,%d), palette=%s, unknownProtected=true",
                    flat.getId(), flatId, worldId, layerName, sizeX, sizeZ, mountX, mountZ, hexQ, hexR,
                    paletteName != null ? paletteName : "none"
            );

            log.info("Flat hexgrid import completed successfully: flatId={}, id={}", flatId, flat.getId());
            return JobResult.ofSuccess(resultData);

        } catch (IllegalArgumentException e) {
            log.error("Invalid parameters for flat hexgrid import", e);
            throw new JobExecutionException("Invalid parameters: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Flat hexgrid import failed", e);
            throw new JobExecutionException("Import failed: " + e.getMessage(), e);
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

    /**
     * Get optional integer parameter from job with default value.
     */
    private int getOptionalIntParameter(WJob job, String paramName, int defaultValue) {
        String value = job.getParameters().get(paramName);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid integer parameter '{}': {}, using default: {}", paramName, value, defaultValue);
            return defaultValue;
        }
    }
}
