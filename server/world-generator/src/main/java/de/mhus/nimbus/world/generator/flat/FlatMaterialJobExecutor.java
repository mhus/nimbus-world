package de.mhus.nimbus.world.generator.flat;

import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.generator.WFlatService;
import de.mhus.nimbus.world.shared.job.JobExecutionException;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import de.mhus.nimbus.world.shared.job.WJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Job executor for managing WFlat material definitions.
 * Supports multiple job types for different material operations.
 *
 * Job Types:
 *
 * 1. "set-material" - Set a single material definition
 *    Required parameters:
 *    - flatId: Flat database ID
 *    - materialId: Material ID (0-255)
 *    - properties: JSON string with format "blockDef|nextBlockDef|hasOcean"
 *      Example: "n:stone@s:default|n:dirt@s:default|false"
 *
 * 2. "set-materials" - Set multiple material definitions
 *    Required parameters:
 *    - flatId: Flat database ID
 *    - properties: JSON string with format {"materialId": "blockDef|nextBlockDef|hasOcean", ...}
 *      Example: {"1":"n:grass@s:default||false","2":"n:dirt@s:default||false"}
 *
 * 3. "set-palette" - Set a predefined material palette
 *    Required parameters:
 *    - flatId: Flat database ID
 *    - paletteName: Name of the palette ("nimbus" or "legacy")
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FlatMaterialJobExecutor implements JobExecutor {

    private static final String EXECUTOR_NAME = "flat-material";

    // Job type constants
    private static final String JOB_TYPE_SET_MATERIAL = "set-material";
    private static final String JOB_TYPE_SET_MATERIALS = "set-materials";
    private static final String JOB_TYPE_SET_PALETTE = "set-palette";

    private final FlatMaterialService flatMaterialService;
    private final WFlatService flatService;

    @Override
    public String getExecutorName() {
        return EXECUTOR_NAME;
    }

    @Override
    public JobResult execute(WJob job) throws JobExecutionException {
        try {
            log.info("Starting flat-material job: jobId={}, type={}", job.getId(), job.getType());

            // Get job type from parameters or job type field
            String jobType = getOptionalParameter(job, "jobType", job.getType());
            if (jobType == null || jobType.isBlank()) {
                throw new JobExecutionException("Job type required (set-material, set-materials, or set-palette)");
            }

            // Execute based on job type
            return switch (jobType) {
                case JOB_TYPE_SET_MATERIAL -> executeSetMaterial(job);
                case JOB_TYPE_SET_MATERIALS -> executeSetMaterials(job);
                case JOB_TYPE_SET_PALETTE -> executeSetPalette(job);
                default -> throw new JobExecutionException("Unknown job type: " + jobType +
                        ". Valid types: set-material, set-materials, set-palette");
            };

        } catch (JobExecutionException e) {
            log.error("Flat-material job failed: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error in flat-material job", e);
            throw new JobExecutionException("Flat-material job failed: " + e.getMessage(), e);
        }
    }

    /**
     * Execute set-material job type.
     * Sets a single material definition on a flat.
     */
    private JobResult executeSetMaterial(WJob job) throws JobExecutionException {
        log.debug("Executing set-material job");

        // Extract required parameters
        String flatId = getRequiredParameter(job, "flatId");
        int materialId = getRequiredIntParameter(job, "materialId");
        String properties = getRequiredParameter(job, "properties");

        // Load flat
        WFlat flat = loadFlat(job, flatId);

        // Validate materialId
        if (materialId < 0 || materialId > 255) {
            throw new JobExecutionException("Material ID must be between 0 and 255, got: " + materialId);
        }

        // Parse properties: "blockDef|nextBlockDef|hasOcean"
        String[] parts = properties.split("\\|", 3);
        if (parts.length < 1) {
            throw new JobExecutionException("Invalid properties format. Expected: blockDef|nextBlockDef|hasOcean");
        }

        String blockDef = parts[0];
        String nextBlockDef = parts.length > 1 && !parts[1].isEmpty() ? parts[1] : null;
        boolean hasOcean = parts.length > 2 && Boolean.parseBoolean(parts[2]);

        // Set material definition (using database ID)
        WFlat updated = flatMaterialService.setMaterialDefinition(flat.getId(), materialId, blockDef, nextBlockDef, hasOcean);

        log.info("Set material definition: flatId={}, materialId={}", flatId, materialId);
        return JobResult.ofSuccess("Material definition set successfully: materialId=" + materialId);
    }

    /**
     * Execute set-materials job type.
     * Sets multiple material definitions on a flat.
     */
    private JobResult executeSetMaterials(WJob job) throws JobExecutionException {
        log.debug("Executing set-materials job");

        // Extract required parameters
        String flatId = getRequiredParameter(job, "flatId");
        String propertiesJson = getRequiredParameter(job, "properties");

        // Load flat
        WFlat flat = loadFlat(job, flatId);

        // Parse JSON properties
        Map<String, String> properties;
        try {
            properties = parsePropertiesJson(propertiesJson);
        } catch (Exception e) {
            throw new JobExecutionException("Failed to parse properties JSON: " + e.getMessage(), e);
        }

        // Set material definitions (using database ID)
        WFlat updated = flatMaterialService.setMaterialDefinitions(flat.getId(), properties);

        log.info("Set material definitions: flatId={}, count={}", flatId, properties.size());
        return JobResult.ofSuccess("Material definitions set successfully: count=" + properties.size());
    }

    /**
     * Execute set-palette job type.
     * Sets a predefined material palette on a flat.
     */
    private JobResult executeSetPalette(WJob job) throws JobExecutionException {
        log.debug("Executing set-palette job");

        // Extract required parameters
        String flatId = getRequiredParameter(job, "flatId");
        String paletteName = getRequiredParameter(job, "paletteName");

        // Load flat
        WFlat flat = loadFlat(job, flatId);

        // Set palette (using database ID)
        WFlat updated = flatMaterialService.setPalette(flat.getId(), paletteName);

        log.info("Set material palette: flatId={}, paletteName={}", flatId, paletteName);
        return JobResult.ofSuccess("Material palette set successfully: " + paletteName);
    }

    /**
     * Load WFlat by flatId with worldId from job.
     * Optionally uses layerDataId if provided.
     */
    private WFlat loadFlat(WJob job, String flatId) throws JobExecutionException {
        String worldId = job.getWorldId();
        String layerDataId = getOptionalParameter(job, "layerDataId", null);

        if (layerDataId != null && !layerDataId.isBlank()) {
            // Use compound lookup with worldId, layerDataId, and flatId
            return flatService.findByWorldIdAndLayerDataIdAndFlatId(worldId, layerDataId, flatId)
                    .orElseThrow(() -> new JobExecutionException("Flat not found: worldId=" + worldId +
                            ", layerDataId=" + layerDataId + ", flatId=" + flatId));
        } else {
            // Search for flat with matching flatId in this world
            return flatService.findByWorldId(worldId).stream()
                    .filter(f -> flatId.equals(f.getFlatId()))
                    .findFirst()
                    .orElseThrow(() -> new JobExecutionException("Flat not found: worldId=" + worldId +
                            ", flatId=" + flatId));
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
     * Parse properties JSON string to Map.
     * Simple parser for format: {"1":"value1","2":"value2"}
     */
    private Map<String, String> parsePropertiesJson(String json) {
        Map<String, String> result = new HashMap<>();

        // Remove braces and whitespace
        json = json.trim();
        if (json.startsWith("{")) json = json.substring(1);
        if (json.endsWith("}")) json = json.substring(0, json.length() - 1);

        if (json.isEmpty()) {
            return result;
        }

        // Split by comma (simple parser, doesn't handle escaped commas in values)
        String[] pairs = json.split(",");
        for (String pair : pairs) {
            // Split by colon
            String[] keyValue = pair.split(":", 2);
            if (keyValue.length != 2) {
                throw new IllegalArgumentException("Invalid JSON pair: " + pair);
            }

            // Remove quotes and whitespace
            String key = keyValue[0].trim().replaceAll("^\"|\"$", "");
            String value = keyValue[1].trim().replaceAll("^\"|\"$", "");

            result.put(key, value);
        }

        return result;
    }
}
