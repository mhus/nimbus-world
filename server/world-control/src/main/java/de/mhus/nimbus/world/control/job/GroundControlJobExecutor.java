package de.mhus.nimbus.world.control.job;

import de.mhus.nimbus.world.control.service.GroundControlService;
import de.mhus.nimbus.world.shared.job.JobExecutionException;
import de.mhus.nimbus.world.shared.job.JobExecutor;
import de.mhus.nimbus.world.shared.job.WJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Job executor for ground control operations.
 * Supports two job types:
 *
 * 1. "check-ground" — run checkGround on a single chunk in a terrain layer.
 *    Parameters:
 *    - layerDataId (required): Layer data ID of the GROUND layer
 *    - cx (required): Chunk X coordinate
 *    - cz (required): Chunk Z coordinate
 *    - sides (optional): Bit flags for neighbor sides (default: 15 = all)
 *    - cleanupBlocks (optional): "true"/"false" (default: "true")
 *
 * 2. "check-hex-grid-ground" — run checkHexGridGround for a hex cell.
 *    Parameters:
 *    - epoch (required): Epoch number
 *    - hexQ (required): Hex axial Q coordinate
 *    - hexR (required): Hex axial R coordinate
 *
 * 3. "check-layer-ground" — run checkLayerGround on all chunks of a layer.
 *    Parameters:
 *    - layerDataId (required): Layer data ID of the GROUND layer
 *    - sides (optional): Bit flags for neighbor sides (default: 15 = all)
 *    - cleanupBlocks (optional): "true"/"false" (default: "true")
 *
 * 4. "check-world-ground" — run checkWorldGround on all GROUND layers of a world.
 *    Parameters:
 *    - sides (optional): Bit flags for neighbor sides (default: 15 = all)
 *    - cleanupBlocks (optional): "true"/"false" (default: "true")
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GroundControlJobExecutor implements JobExecutor {

    private final GroundControlService groundControlService;

    @Override
    public String getExecutorName() {
        return "ground-control";
    }

    @Override
    public JobResult execute(WJob job) throws JobExecutionException {
        try {
            Map<String, String> params = job.getParameters();
            String worldId = job.getWorldId();
            String type = params.get("type");

            if (type == null || type.isBlank()) {
                throw new JobExecutionException("Missing required parameter: type");
            }

            return switch (type) {
                case "check-ground" -> executeCheckGround(worldId, params);
                case "check-hex-grid-ground" -> executeCheckHexGridGround(worldId, params);
                case "check-layer-ground" -> executeCheckLayerGround(worldId, params);
                case "check-world-ground" -> executeCheckWorldGround(worldId, params);
                default -> throw new JobExecutionException("Unknown type: " + type);
            };

        } catch (JobExecutionException e) {
            throw e;
        } catch (Exception e) {
            log.error("Ground control job failed", e);
            throw new JobExecutionException("Ground control failed: " + e.getMessage(), e);
        }
    }

    private JobResult executeCheckGround(String worldId, Map<String, String> params) throws JobExecutionException {
        String layerDataId = requireParam(params, "layerDataId");
        int cx = requireIntParam(params, "cx");
        int cz = requireIntParam(params, "cz");
        int sides = parseIntParameter(params, "sides", GroundControlService.SIDE_ALL);
        boolean cleanupBlocks = parseBooleanParameter(params, "cleanupBlocks", true);

        log.info("check-ground: worldId={} layerDataId={} cx={} cz={} sides={} cleanup={}",
                worldId, layerDataId, cx, cz, sides, cleanupBlocks);

        boolean modified = groundControlService.checkGround(worldId, layerDataId, cx, cz, sides, cleanupBlocks);

        String msg = String.format("check-ground chunk %d:%d %s", cx, cz, modified ? "modified" : "unchanged");
        log.info(msg);
        return JobResult.success(msg);
    }

    private JobResult executeCheckHexGridGround(String worldId, Map<String, String> params) throws JobExecutionException {
        int epoch = requireIntParam(params, "epoch");
        int q = requireIntParam(params, "hexQ");
        int r = requireIntParam(params, "hexR");

        log.info("check-hex-grid-ground: worldId={} epoch={} q={} r={}", worldId, epoch, q, r);

        int modified = groundControlService.checkHexGridGround(worldId, epoch, q, r);

        String msg = String.format("check-hex-grid-ground hex (%d,%d) epoch=%d: %d chunks modified", q, r, epoch, modified);
        log.info(msg);
        return JobResult.success(msg);
    }

    private JobResult executeCheckLayerGround(String worldId, Map<String, String> params) throws JobExecutionException {
        String layerDataId = requireParam(params, "layerDataId");
        int sides = parseIntParameter(params, "sides", GroundControlService.SIDE_ALL);
        boolean cleanupBlocks = parseBooleanParameter(params, "cleanupBlocks", true);

        log.info("check-layer-ground: worldId={} layerDataId={} sides={} cleanup={}",
                worldId, layerDataId, sides, cleanupBlocks);

        int modified = groundControlService.checkLayerGround(worldId, layerDataId, sides, cleanupBlocks);

        String msg = String.format("check-layer-ground layerDataId=%s: %d chunks modified", layerDataId, modified);
        log.info(msg);
        return JobResult.success(msg);
    }

    private JobResult executeCheckWorldGround(String worldId, Map<String, String> params) throws JobExecutionException {
        int sides = parseIntParameter(params, "sides", GroundControlService.SIDE_ALL);
        boolean cleanupBlocks = parseBooleanParameter(params, "cleanupBlocks", true);

        log.info("check-world-ground: worldId={} sides={} cleanup={}", worldId, sides, cleanupBlocks);

        int modified = groundControlService.checkWorldGround(worldId, sides, cleanupBlocks);

        String msg = String.format("check-world-ground worldId=%s: %d chunks modified", worldId, modified);
        log.info(msg);
        return JobResult.success(msg);
    }

    private String requireParam(Map<String, String> params, String key) throws JobExecutionException {
        String value = params.get(key);
        if (value == null || value.isBlank()) {
            throw new JobExecutionException("Missing required parameter: " + key);
        }
        return value;
    }

    private int requireIntParam(Map<String, String> params, String key) throws JobExecutionException {
        String value = requireParam(params, key);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new JobExecutionException("Parameter '" + key + "' must be an integer, got: " + value);
        }
    }

    private int parseIntParameter(Map<String, String> params, String key, int defaultValue) {
        String value = params.get(key);
        if (value == null || value.isBlank()) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private boolean parseBooleanParameter(Map<String, String> params, String key, boolean defaultValue) {
        String value = params.get(key);
        if (value == null || value.isBlank()) return defaultValue;
        return Boolean.parseBoolean(value);
    }
}
