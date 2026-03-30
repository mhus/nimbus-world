package de.mhus.nimbus.world.life.logic;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.world.WChunk;
import de.mhus.nimbus.world.shared.world.WChunkService;
import de.mhus.nimbus.world.shared.world.WProgressService;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Effect handler that changes block status and publishes it via Redis.
 * Follows the same pattern as DoorAction for status resolution and toggle behavior.
 *
 * The chunkKey is automatically computed from blockKey (world coordinates) using WWorld.
 *
 * Parameters:
 *   - blockKey:      block world position, e.g. "5,3,8" (x,y,z) (required)
 *   - value:         "open", "close", "closed", "toggle", or any custom status (required)
 *   - defaultState:  default block state (default: "closed")
 *   - toggleType:    "single" (default), "auto", or "group"
 *     - single: only the specified block
 *     - auto:   also adjacent blocks up/down (up to 2) with same action in serverInfo
 *     - group:  all blocks in chunk with matching toggleGroup and action
 *   - toggleGroup:   group name (required when toggleType=group)
 *   - action:        action name for auto/group matching (default: "door")
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LogicBlockStatusHandler implements LogicEffectHandler {

    public static final String TYPE = "block_status";
    private static final String DEFAULT_STATE = "closed";
    private static final String DEFAULT_ACTION = "door";

    private final WProgressService progressService;
    private final WWorldService worldService;
    private final WChunkService chunkService;

    @Override
    public Set<String> execute(Map<String, String> parameters, LogicContext context) {
        String blockKey = parameters.get("blockKey");
        String value = parameters.get("value");
        String defaultState = parameters.getOrDefault("defaultState", DEFAULT_STATE);
        String toggleType = parameters.getOrDefault("toggleType", "single");

        if (blockKey == null || value == null) {
            log.error("block_status: missing required parameters (blockKey, value) in {}", parameters);
            return Set.of();
        }

        String worldId = context.getWorldId();

        // Parse block coordinates from blockKey "x,y,z"
        int[] coords = parseBlockKey(blockKey);
        if (coords == null) return Set.of();

        int x = coords[0], y = coords[1], z = coords[2];

        // Resolve world and chunkKey
        WWorld world = worldService.getByWorldId(worldId).orElse(null);
        if (world == null) {
            log.error("block_status: world not found for worldId={}", worldId);
            return Set.of();
        }
        String chunkKey = world.getChunkKey(x, z);
        WorldId wid = WorldId.unchecked(worldId);

        // Resolve the new status for the target block
        String newStatus = resolveStatus(worldId, chunkKey, blockKey, value, defaultState);
        if (newStatus == null) {
            log.warn("block_status: unknown value '{}' for block {}/{}", value, chunkKey, blockKey);
            return Set.of();
        }

        // Collect all target blocks based on toggleType
        List<String> targets = collectTargets(wid, chunkKey, x, y, z, toggleType, parameters);

        // Apply status to all targets
        for (String target : targets) {
            if (newStatus.equals(defaultState)) {
                progressService.removeBlockStatus(worldId, chunkKey, target);
            } else {
                progressService.setBlockStatus(worldId, chunkKey, target, newStatus);
            }
        }

        log.debug("block_status: worldId={}, chunk={}, targets={}, status={} (type={}, default={})",
                worldId, chunkKey, targets, newStatus, toggleType, defaultState);

        return Set.of();
    }

    /**
     * Collect target block keys based on toggleType.
     */
    private List<String> collectTargets(WorldId worldId, String chunkKey,
                                        int x, int y, int z,
                                        String toggleType, Map<String, String> parameters) {
        String targetKey = x + "," + y + "," + z;

        return switch (toggleType.toLowerCase()) {
            case "auto" -> collectAutoTargets(worldId, chunkKey, x, y, z, targetKey, parameters);
            case "group" -> collectGroupTargets(worldId, chunkKey, targetKey, parameters);
            default -> List.of(targetKey);
        };
    }

    /**
     * Auto: find adjacent blocks (up/down, up to 2 in each direction) with same action.
     */
    private List<String> collectAutoTargets(WorldId worldId, String chunkKey,
                                            int x, int y, int z, String targetKey,
                                            Map<String, String> parameters) {
        String actionName = parameters.getOrDefault("action", DEFAULT_ACTION);
        WChunk chunk = chunkService.find(worldId, chunkKey).orElse(null);
        if (chunk == null || chunk.getInfoServer() == null) {
            return List.of(targetKey);
        }

        List<String> targets = new ArrayList<>();
        targets.add(targetKey);

        int[][] offsets = {{0, 1, 0}, {0, -1, 0}, {0, 2, 0}, {0, -2, 0}};
        for (int[] offset : offsets) {
            String adjacentKey = (x + offset[0]) + "," + (y + offset[1]) + "," + (z + offset[2]);
            Map<String, String> adjacentInfo = chunk.getInfoServer().get(adjacentKey);
            if (adjacentInfo != null && actionName.equals(adjacentInfo.get("action"))) {
                targets.add(adjacentKey);
            }
        }
        return targets;
    }

    /**
     * Group: find all blocks in the chunk with matching toggleGroup and action.
     */
    private List<String> collectGroupTargets(WorldId worldId, String chunkKey,
                                             String targetKey, Map<String, String> parameters) {
        String groupName = parameters.get("toggleGroup");
        String actionName = parameters.getOrDefault("action", DEFAULT_ACTION);

        if (groupName == null || groupName.isBlank()) {
            log.warn("block_status: toggleType=group but no toggleGroup specified, falling back to single");
            return List.of(targetKey);
        }

        WChunk chunk = chunkService.find(worldId, chunkKey).orElse(null);
        if (chunk == null || chunk.getInfoServer() == null) {
            return List.of(targetKey);
        }

        List<String> targets = new ArrayList<>();
        for (var entry : chunk.getInfoServer().entrySet()) {
            Map<String, String> info = entry.getValue();
            if (actionName.equals(info.get("action")) && groupName.equals(info.get("toggleGroup"))) {
                targets.add(entry.getKey());
            }
        }

        if (!targets.contains(targetKey)) {
            targets.add(targetKey);
        }
        return targets;
    }

    private String resolveStatus(String worldId, String chunkKey, String blockKey,
                                 String value, String defaultState) {
        return switch (value.toLowerCase()) {
            case "open" -> "open";
            case "close", "closed" -> "closed";
            case "toggle" -> {
                var statusMap = progressService.findBlockStatusForChunks(worldId, List.of(chunkKey));
                var chunkStatus = statusMap.get(chunkKey);
                String currentStatus = chunkStatus != null ? (String) chunkStatus.get(blockKey) : null;
                if (currentStatus == null) currentStatus = defaultState;
                yield "open".equals(currentStatus) ? "closed" : "open";
            }
            default -> value;
        };
    }

    private int[] parseBlockKey(String blockKey) {
        String[] parts = blockKey.split(",");
        if (parts.length != 3) {
            log.error("block_status: invalid blockKey format '{}', expected 'x,y,z'", blockKey);
            return null;
        }
        try {
            return new int[]{
                    Integer.parseInt(parts[0].trim()),
                    Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim())
            };
        } catch (NumberFormatException e) {
            log.error("block_status: invalid coordinates in blockKey '{}'", blockKey);
            return null;
        }
    }

    @Override
    public String getType() {
        return TYPE;
    }
}
