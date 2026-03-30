package de.mhus.nimbus.world.life.logic;

import de.mhus.nimbus.world.shared.world.WProgressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Effect handler that changes block status and publishes it via Redis.
 * Follows the same pattern as DoorAction for status resolution.
 *
 * Parameters:
 *   - chunkKey:     chunk coordinate key, e.g. "1:2" (required)
 *   - blockKey:     block position key, e.g. "5,3,8" (required)
 *   - value:        "open", "close", "closed", "toggle", or any custom status (required)
 *   - defaultState: default block state, e.g. "closed" (default: "closed")
 *                   When the resolved status equals defaultState, the entry is REMOVED
 *                   (same behavior as DoorAction — default state = no entry in DB)
 *
 * Example effects:
 *   {"type": "block_status", "parameters": {"chunkKey": "1:2", "blockKey": "5,3,8", "value": "toggle"}}
 *   {"type": "block_status", "parameters": {"chunkKey": "1:2", "blockKey": "5,3,8", "value": "open"}}
 *   {"type": "block_status", "parameters": {"chunkKey": "0:0", "blockKey": "10,5,10", "value": "toggle", "defaultState": "locked"}}
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LogicBlockStatusHandler implements LogicEffectHandler {

    public static final String TYPE = "block_status";
    private static final String DEFAULT_STATE = "closed";

    private final WProgressService progressService;

    @Override
    public Set<String> execute(Map<String, String> parameters, LogicContext context) {
        String chunkKey = parameters.get("chunkKey");
        String blockKey = parameters.get("blockKey");
        String value = parameters.get("value");
        String defaultState = parameters.getOrDefault("defaultState", DEFAULT_STATE);

        if (chunkKey == null || blockKey == null || value == null) {
            log.error("block_status: missing required parameters (chunkKey, blockKey, value) in {}", parameters);
            return Set.of();
        }

        String worldId = context.getWorldId();

        // Resolve the new status (same logic as DoorAction.resolveStatus)
        String newStatus = resolveStatus(worldId, chunkKey, blockKey, value, defaultState);
        if (newStatus == null) {
            log.warn("block_status: unknown value '{}' for block {}/{}", value, chunkKey, blockKey);
            return Set.of();
        }

        // When status equals default state -> remove entry (DoorAction pattern)
        // Otherwise -> set status
        if (newStatus.equals(defaultState)) {
            progressService.removeBlockStatus(worldId, chunkKey, blockKey);
        } else {
            progressService.setBlockStatus(worldId, chunkKey, blockKey, newStatus);
        }

        log.debug("block_status: worldId={}, chunk={}, block={}, status={} (default={})",
                worldId, chunkKey, blockKey, newStatus, defaultState);

        return Set.of();
    }

    /**
     * Resolve final status from value parameter.
     * Supports: open, close/closed, toggle, or any custom value as-is.
     */
    private String resolveStatus(String worldId, String chunkKey, String blockKey,
                                 String value, String defaultState) {
        return switch (value.toLowerCase()) {
            case "open" -> "open";
            case "close", "closed" -> "closed";
            case "toggle" -> {
                var statusMap = progressService.findBlockStatusForChunks(worldId, List.of(chunkKey));
                var chunkStatus = statusMap.get(chunkKey);
                String currentStatus = chunkStatus != null ? (String) chunkStatus.get(blockKey) : null;

                if (currentStatus == null) {
                    currentStatus = defaultState;
                }

                // Toggle between open and closed (or between default and "open" for custom defaults)
                yield "open".equals(currentStatus) ? "closed" : "open";
            }
            default -> value; // Custom status value passed through as-is
        };
    }

    @Override
    public String getType() {
        return TYPE;
    }
}
