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
 * Imports all columns from the layer and sets them to material 255 (NOT_SET_MUTABLE) if inside hex,
 * then sets positions outside the HexGrid to material 0 (NOT_SET).
 * Sets unknownProtected = true (only HexGrid positions can be modified).
 *
 * WorldId is taken from job.getWorldId()
 * Job type (job.getType()) determines the coordinate system:
 *
 * Type "grid" (default if type not set):
 *   Uses HexGrid coordinates. Size and mount position are auto-calculated from hexGridSize.
 *   Required parameters:
 *   - layerName: Name of the GROUND layer to import from
 *   - hexQ: HexGrid Q coordinate (axial)
 *   - hexR: HexGrid R coordinate (axial)
 *   Note: sizeX, sizeZ, mountX, mountZ are auto-calculated from hexGridSize
 *
 * Type "rectangular":
 *   Uses rectangular coordinates. All dimensions must be specified explicitly.
 *   Required parameters:
 *   - layerName: Name of the GROUND layer to import from
 *   - sizeX: Width of the flat (1-800)
 *   - sizeZ: Height of the flat (1-800)
 *   - mountX: Mount X position (start position in layer)
 *   - mountZ: Mount Z position (start position in layer)
 *   - hexQ: HexGrid Q coordinate (axial)
 *   - hexR: HexGrid R coordinate (axial)
 *
 * Optional parameters (all types):
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
            log.info("Starting flat hexgrid import job: jobId={}, type={}", job.getId(), job.getType());

            // Get worldId from job
            String worldId = job.getWorldId();

            // Extract required parameter
            String layerName = getRequiredParameter(job, "layerName");

            // Extract optional parameters
            String flatId = getOptionalParameter(job, "flatId", java.util.UUID.randomUUID().toString());
            String title = getOptionalParameter(job, "title", null);
            String description = getOptionalParameter(job, "description", null);
            String paletteName = getOptionalParameter(job, "paletteName", null);

            // Get job type to determine coordinate system
            String jobType = job.getType();
            if (jobType == null || jobType.isBlank()) {
                jobType = "grid";  // Default to grid mode
            }

            WFlat flat;

            if ("grid".equals(jobType)) {
                // Grid mode: use HexGrid coordinates, calculate size and mount automatically
                int hexQ = getRequiredIntParameter(job, "hexQ");
                int hexR = getRequiredIntParameter(job, "hexR");

                log.info("Importing HexGrid flat (grid mode): worldId={}, layerName={}, flatId={}, hex=({},{}), title={}, description={}, palette={}",
                        worldId, layerName, flatId, hexQ, hexR, title, description, paletteName);

                // Execute import with auto-calculated size/mount
                flat = flatCreateService.importHexGridFlat(
                        worldId, layerName, flatId,
                        hexQ, hexR, title, description
                );
            } else if ("rectangular".equals(jobType)) {
                // Rectangular mode: use explicit rectangular coordinates
                int sizeX = getRequiredIntParameter(job, "sizeX");
                int sizeZ = getRequiredIntParameter(job, "sizeZ");
                int mountX = getRequiredIntParameter(job, "mountX");
                int mountZ = getRequiredIntParameter(job, "mountZ");
                int hexQ = getRequiredIntParameter(job, "hexQ");
                int hexR = getRequiredIntParameter(job, "hexR");

                // Validate size parameters
                if (sizeX <= 0 || sizeX > 800) {
                    throw new JobExecutionException("sizeX must be between 1 and 800, got: " + sizeX);
                }
                if (sizeZ <= 0 || sizeZ > 800) {
                    throw new JobExecutionException("sizeZ must be between 1 and 800, got: " + sizeZ);
                }

                log.info("Importing HexGrid flat (rectangular mode): worldId={}, layerName={}, flatId={}, size={}x{}, mount=({},{}), hex=({},{}), title={}, description={}, palette={}",
                        worldId, layerName, flatId, sizeX, sizeZ, mountX, mountZ, hexQ, hexR, title, description, paletteName);

                // Execute import with explicit rectangular coordinates
                flat = flatCreateService.importHexGridFlat(
                        worldId, layerName, flatId,
                        sizeX, sizeZ, mountX, mountZ,
                        hexQ, hexR, title, description
                );
            } else {
                throw new JobExecutionException("Unknown job type: " + jobType + ". Valid types: grid, rectangular");
            }

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
            String hexCoords = flat.getHexGrid() != null
                ? String.format("(%d,%d)", flat.getHexGrid().getQ(), flat.getHexGrid().getR())
                : "(unknown)";

            String resultData = String.format(
                    "Successfully imported HexGrid flat (type=%s): id=%s, flatId=%s, worldId=%s, layerName=%s, hex=%s, size=%dx%d, mount=(%d,%d), palette=%s, unknownProtected=true",
                    jobType, flat.getId(), flatId, worldId, layerName, hexCoords,
                    flat.getSizeX(), flat.getSizeZ(), flat.getMountX(), flat.getMountZ(),
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
