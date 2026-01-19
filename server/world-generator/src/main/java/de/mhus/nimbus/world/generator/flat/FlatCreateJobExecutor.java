package de.mhus.nimbus.world.generator.flat;

import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.job.JobExecutionException;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import de.mhus.nimbus.world.shared.job.WJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Job executor for creating empty WFlat with BEDROCK material.
 * Creates a flat terrain at level 0 with border imported from layer.
 *
 * WorldId is taken from job.getWorldId()
 *
 * Two modes:
 * 1. Manual mode (requires all size/mount parameters):
 *    - layerName: Name of the GROUND layer to import border from
 *    - sizeX: Width of the flat (1-800)
 *    - sizeZ: Height of the flat (1-800)
 *    - mountX: Mount X position (start position in layer)
 *    - mountZ: Mount Z position (start position in layer)
 *
 * 2. HexGrid mode (auto-calculates size/mount from grid coordinates):
 *    - layerName: Name of the GROUND layer to import border from
 *    - hexQ: HexGrid Q coordinate (axial)
 *    - hexR: HexGrid R coordinate (axial)
 *    Note: If hexQ and hexR are provided, sizeX, sizeZ, mountX, mountZ are NOT required and will be ignored.
 *
 * Optional parameters (both modes):
 * - flatId: Identifier for the new WFlat (if not provided, UUID will be generated)
 * - title: Display title for the flat
 * - description: Description text for the flat
 * - paletteName: Name of predefined material palette to apply ("nimbus" or "legacy")
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FlatCreateJobExecutor implements JobExecutor {

    private static final String EXECUTOR_NAME = "flat-create";

    private final FlatCreateService flatCreateService;
    private final FlatMaterialService flatMaterialService;

    @Override
    public String getExecutorName() {
        return EXECUTOR_NAME;
    }

    @Override
    public JobResult execute(WJob job) throws JobExecutionException {
        try {
            log.info("Starting flat create job: jobId={}", job.getId());

            // Get worldId from job
            String worldId = job.getWorldId();

            // Extract required parameter
            String layerName = getRequiredParameter(job, "layerName");

            // Extract optional parameters
            String flatId = getOptionalParameter(job, "flatId", java.util.UUID.randomUUID().toString());
            String title = getOptionalParameter(job, "title", null);
            String description = getOptionalParameter(job, "description", null);
            String paletteName = getOptionalParameter(job, "paletteName", null);

            // Check if HexGrid mode (hexQ and hexR provided)
            String hexQStr = getOptionalParameter(job, "hexQ", null);
            String hexRStr = getOptionalParameter(job, "hexR", null);

            WFlat flat;

            if (hexQStr != null && hexRStr != null) {
                // HexGrid mode: auto-calculate size and mount from hex coordinates
                int hexQ;
                int hexR;
                try {
                    hexQ = Integer.parseInt(hexQStr);
                    hexR = Integer.parseInt(hexRStr);
                } catch (NumberFormatException e) {
                    throw new JobExecutionException("Invalid hex coordinates: hexQ=" + hexQStr + ", hexR=" + hexRStr);
                }

                log.info("Creating HexGrid flat (auto-size): worldId={}, layerName={}, flatId={}, hex=({},{}), title={}, description={}, palette={}",
                        worldId, layerName, flatId, hexQ, hexR, title, description, paletteName);

                // Execute create with auto-calculated size/mount
                flat = flatCreateService.createHexGridFlat(
                        worldId, layerName, flatId,
                        hexQ, hexR, title, description
                );
            } else {
                // Manual mode: require all size/mount parameters
                int sizeX = getRequiredIntParameter(job, "sizeX");
                int sizeZ = getRequiredIntParameter(job, "sizeZ");
                int mountX = getRequiredIntParameter(job, "mountX");
                int mountZ = getRequiredIntParameter(job, "mountZ");

                // Validate size parameters
                if (sizeX <= 0 || sizeX > 800) {
                    throw new JobExecutionException("sizeX must be between 1 and 800, got: " + sizeX);
                }
                if (sizeZ <= 0 || sizeZ > 800) {
                    throw new JobExecutionException("sizeZ must be between 1 and 800, got: " + sizeZ);
                }

                log.info("Creating empty flat: worldId={}, layerName={}, flatId={}, size={}x{}, mount=({},{}), title={}, description={}, palette={}",
                        worldId, layerName, flatId, sizeX, sizeZ, mountX, mountZ, title, description, paletteName);

                // Execute create with manual size/mount
                flat = flatCreateService.createEmptyFlat(
                        worldId, layerName, flatId,
                        sizeX, sizeZ, mountX, mountZ,
                        title, description
                );
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
            String resultData;
            if (hexQStr != null && hexRStr != null) {
                resultData = String.format(
                        "Successfully created HexGrid flat: id=%s, flatId=%s, worldId=%s, layerName=%s, hex=(%s,%s), size=%dx%d, mount=(%d,%d), palette=%s",
                        flat.getId(), flatId, worldId, layerName, hexQStr, hexRStr,
                        flat.getSizeX(), flat.getSizeZ(), flat.getMountX(), flat.getMountZ(),
                        paletteName != null ? paletteName : "none"
                );
            } else {
                resultData = String.format(
                        "Successfully created empty flat: id=%s, flatId=%s, worldId=%s, layerName=%s, size=%dx%d, mount=(%d,%d), palette=%s",
                        flat.getId(), flatId, worldId, layerName, flat.getSizeX(), flat.getSizeZ(), flat.getMountX(), flat.getMountZ(),
                        paletteName != null ? paletteName : "none"
                );
            }

            log.info("Flat create completed successfully: flatId={}, id={}", flatId, flat.getId());
            return JobResult.ofSuccess(resultData);

        } catch (IllegalArgumentException e) {
            log.error("Invalid parameters for flat create", e);
            throw new JobExecutionException("Invalid parameters: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Flat create failed", e);
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
