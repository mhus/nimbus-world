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
 * Job executor for manipulating WFlat terrain using registered manipulators.
 * <p>
 * Executes a specific manipulator on a region of a flat terrain.
 * The manipulator modifies the flat in-place and the result is saved to the database.
 * <p>
 * The manipulator name is taken from the job type.
 * Example: job type "raise" will execute the "raise" manipulator.
 *
 * Required parameters:
 * - flatId: Database ID of the WFlat to manipulate
 *
 * Optional parameters:
 * - x: X coordinate of the region start (default: 0)
 * - z: Z coordinate of the region start (default: 0)
 * - sizeX: Width of the region to manipulate (default: flat's sizeX)
 * - sizeZ: Height of the region to manipulate (default: flat's sizeZ)
 * - parameters: JSON string with manipulator-specific parameters
 *   Format: {"key1":"value1","key2":"value2"}
 *   Example: {"height":"10","strength":"0.5"}
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FlatManipulateJobExecutor implements JobExecutor {

    private static final String EXECUTOR_NAME = "flat-manipulate";

    private final FlatManipulatorService manipulatorService;
    private final WFlatService flatService;

    @Override
    public String getExecutorName() {
        return EXECUTOR_NAME;
    }

    @Override
    public JobResult execute(WJob job) throws JobExecutionException {
        try {
            log.info("Starting flat manipulate job: jobId={}, type={}", job.getId(), job.getType());

            // Get manipulator name from job type
            String manipulatorName = job.getType();
            if (manipulatorName == null || manipulatorName.isBlank()) {
                throw new JobExecutionException("Job type required (must be the manipulator name)");
            }

            // Extract required parameters
            String flatId = getRequiredParameter(job, "flatId");
            String worldId = job.getWorldId();

            // Extract optional layerDataId
            String layerDataId = getOptionalParameter(job, "layerDataId", null);

            // Load flat first (needed for default sizeX/sizeZ)
            WFlat flat;
            if (layerDataId != null && !layerDataId.isBlank()) {
                // Use compound lookup with worldId, layerDataId, and flatId
                flat = flatService.findByWorldIdAndLayerDataIdAndFlatId(worldId, layerDataId, flatId)
                        .orElseThrow(() -> new JobExecutionException("Flat not found: worldId=" + worldId +
                                ", layerDataId=" + layerDataId + ", flatId=" + flatId));
            } else {
                // Search for flat with matching flatId in this world
                flat = flatService.findByWorldId(worldId).stream()
                        .filter(f -> flatId.equals(f.getFlatId()))
                        .findFirst()
                        .orElseThrow(() -> new JobExecutionException("Flat not found: worldId=" + worldId +
                                ", flatId=" + flatId));
            }

            // Extract optional region parameters (defaults to entire flat)
            int x = getOptionalIntParameter(job, "x", 0);
            int z = getOptionalIntParameter(job, "z", 0);
            int sizeX = getOptionalIntParameter(job, "sizeX", flat.getSizeX());
            int sizeZ = getOptionalIntParameter(job, "sizeZ", flat.getSizeZ());

            // Extract optional manipulator parameters
            String parametersJson = getOptionalParameter(job, "parameters", null);
            Map<String, String> parameters = new HashMap<>();
            if (parametersJson != null && !parametersJson.isBlank()) {
                try {
                    parameters = parseParametersJson(parametersJson);
                } catch (Exception e) {
                    throw new JobExecutionException("Failed to parse parameters JSON: " + e.getMessage(), e);
                }
            }

            log.info("Manipulating flat: flatId={}, manipulator={}, region=({},{},{},{}), parameters={}",
                    flatId, manipulatorName, x, z, sizeX, sizeZ, parameters);

            // Execute manipulator
            manipulatorService.executeManipulator(manipulatorName, flat, x, z, sizeX, sizeZ, parameters);

            // Save updated flat
            flat.touchUpdate();
            WFlat updated = flatService.update(flat);

            // Build success result
            String resultData = String.format(
                    "Successfully manipulated flat: flatId=%s, manipulator=%s, region=(%d,%d,%d,%d), parameters=%s",
                    flatId, manipulatorName, x, z, sizeX, sizeZ, parameters.isEmpty() ? "none" : parameters.toString()
            );

            log.info("Flat manipulation completed successfully: flatId={}, manipulator={}", flatId, manipulatorName);
            return JobResult.ofSuccess(resultData);

        } catch (IllegalArgumentException e) {
            log.error("Invalid parameters for flat manipulation", e);
            throw new JobExecutionException("Invalid parameters: " + e.getMessage(), e);
        } catch (JobExecutionException e) {
            log.error("Flat manipulation job failed: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Flat manipulation failed", e);
            throw new JobExecutionException("Manipulation failed: " + e.getMessage(), e);
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

    /**
     * Parse parameters JSON string to Map.
     * Simple parser for format: {"key1":"value1","key2":"value2"}
     */
    private Map<String, String> parseParametersJson(String json) {
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
