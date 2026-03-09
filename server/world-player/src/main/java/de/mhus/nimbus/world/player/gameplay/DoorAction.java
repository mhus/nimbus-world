package de.mhus.nimbus.world.player.gameplay;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.shared.utils.TypeUtil;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.shared.world.WChunk;
import de.mhus.nimbus.world.shared.world.WEntity;
import de.mhus.nimbus.world.shared.world.WItem;
import de.mhus.nimbus.world.shared.world.WWorld;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * GameplayAction for opening/closing doors.
 *
 * Server parameters (from block metadata / serverInfo):
 * - action=door
 * - value=open/close/toggle (default: toggle)
 * - position=x,y,z (optional, if not set uses the interacted block position)
 * - defaultDoorState=open/closed (default: closed)
 * - toggleType=auto/single/group (default: auto)
 *   - auto: also toggle adjacent blocks (up/down) with same action=door
 *   - single: only toggle this block
 *   - group: toggle all blocks in the chunk with the same toggleGroup and action=door
 * - toggleGroup=<name> (required when toggleType=group)
 */
@Slf4j
public class DoorAction implements GameplayAction {

    private static final String DEFAULT_DOOR_STATE = "closed";

    private final BasicGameplay basic;

    public DoorAction(BasicGameplay basic) {
        this.basic = basic;
    }

    @Override
    public boolean handleBlockAction(PlayerSession session, int x, int y, int z, String blockId, String groupId,
                                     String blockAction, JsonNode params, String userAction, String shortcutKey,
                                     Map<String, String> serverInfo) {
        if (session.getWorldId() == null) return false;

        String worldId = session.getWorldId().getId();
        WorldId wid = session.getWorldId();

        // Determine target position
        int targetX = x, targetY = y, targetZ = z;
        String positionParam = serverInfo != null ? serverInfo.get("position") : null;
        if (!Strings.isBlank(positionParam)) {
            var coord = TypeUtil.parseWorldCoord(positionParam);
            targetX = (int) coord.getX();
            targetY = (int) coord.getY();
            targetZ = (int) coord.getZ();
        }

        // Determine value (open/close/toggle)
        String value = serverInfo != null ? serverInfo.get("value") : null;
        if (Strings.isBlank(value)) {
            value = "toggle";
        }

        // Get default door state from block metadata
        String defaultDoorState = serverInfo != null ? serverInfo.get("defaultDoorState") : null;
        if (Strings.isBlank(defaultDoorState)) {
            defaultDoorState = DEFAULT_DOOR_STATE;
        }

        // Compute chunk key and block key
        Optional<WWorld> worldOpt = basic.getWorldService().getByWorldId(worldId);
        if (worldOpt.isEmpty()) {
            log.warn("World not found: {}", worldId);
            return false;
        }
        WWorld world = worldOpt.get();
        String chunkKey = world.getChunkKey(targetX, targetZ);
        String blockKey = targetX + "," + targetY + "," + targetZ;

        // Resolve final status for the interacted block
        String newStatus = resolveStatus(worldId, chunkKey, blockKey, value, defaultDoorState);
        if (newStatus == null) {
            return false;
        }

        // Collect all block keys to toggle
        List<String> blockKeys = collectToggleTargets(wid, serverInfo, chunkKey, targetX, targetY, targetZ);

        // Apply status to all targets
        var sender = basic.getBlockStatusSenderService();
        for (String key : blockKeys) {
            if (newStatus.equals(defaultDoorState)) {
                int[] cc = TypeUtil.parseChunkCoord(chunkKey);
                sender.removeAndBroadcast(worldId, chunkKey, cc[0], cc[1], key);
            } else {
                sender.setAndBroadcast(worldId, chunkKey, key, newStatus);
            }
        }

        log.debug("Door action: worldId={}, chunkKey={}, targets={}, status={}", worldId, chunkKey, blockKeys, newStatus);
        return true;
    }

    /**
     * Collect all block keys that should be toggled based on toggleType.
     *
     * @return List of block keys ("x,y,z") to toggle, always includes the target itself
     */
    private List<String> collectToggleTargets(WorldId worldId, Map<String, String> serverInfo, String chunkKey,
                                               int targetX, int targetY, int targetZ) {
        String targetKey = targetX + "," + targetY + "," + targetZ;
        String toggleType = serverInfo != null ? serverInfo.get("toggleType") : null;
        if (Strings.isBlank(toggleType)) {
            toggleType = "auto";
        }

        return switch (toggleType.toLowerCase()) {
            case "single" -> List.of(targetKey);
            case "group" -> collectGroupTargets(worldId, serverInfo, chunkKey, targetKey);
            default -> collectAutoTargets(worldId, chunkKey, targetX, targetY, targetZ, targetKey);
        };
    }

    /**
     * Auto-detect: find adjacent blocks (up/down) in the same chunk with action=door.
     */
    private List<String> collectAutoTargets(WorldId worldId, String chunkKey,
                                             int targetX, int targetY, int targetZ, String targetKey) {
        WChunk chunk = basic.getChunkService().find(worldId, chunkKey).orElse(null);
        if (chunk == null || chunk.getInfoServer() == null) {
            return List.of(targetKey);
        }

        List<String> targets = new ArrayList<>();
        targets.add(targetKey);

        // Check blocks above and below (up to 2 blocks in each direction)
        int[][] offsets = {{0, 1, 0}, {0, -1, 0}, {0, 2, 0}, {0, -2, 0}};
        for (int[] offset : offsets) {
            String adjacentKey = (targetX + offset[0]) + "," + (targetY + offset[1]) + "," + (targetZ + offset[2]);
            Map<String, String> adjacentInfo = chunk.getInfoServer().get(adjacentKey);
            if (adjacentInfo != null && "door".equals(adjacentInfo.get("action"))) {
                targets.add(adjacentKey);
            }
        }

        return targets;
    }

    /**
     * Group: find all blocks in the same chunk with matching toggleGroup and action=door.
     */
    private List<String> collectGroupTargets(WorldId worldId, Map<String, String> serverInfo,
                                              String chunkKey, String targetKey) {
        String groupName = serverInfo != null ? serverInfo.get("toggleGroup") : null;
        if (Strings.isBlank(groupName)) {
            log.warn("toggleType=group but no toggleGroup specified, falling back to single");
            return List.of(targetKey);
        }

        WChunk chunk = basic.getChunkService().find(worldId, chunkKey).orElse(null);
        if (chunk == null || chunk.getInfoServer() == null) {
            return List.of(targetKey);
        }

        List<String> targets = new ArrayList<>();
        for (var entry : chunk.getInfoServer().entrySet()) {
            Map<String, String> info = entry.getValue();
            if ("door".equals(info.get("action")) && groupName.equals(info.get("toggleGroup"))) {
                targets.add(entry.getKey());
            }
        }

        // Ensure target is included
        if (!targets.contains(targetKey)) {
            targets.add(targetKey);
        }

        return targets;
    }

    private String resolveStatus(String worldId, String chunkKey, String blockKey, String value, String defaultDoorState) {
        return switch (value.toLowerCase()) {
            case "open" -> "open";
            case "close", "closed" -> "closed";
            case "toggle" -> {
                var statusMap = basic.getProgressService().findBlockStatusForChunks(worldId, List.of(chunkKey));
                var chunkStatus = statusMap.get(chunkKey);
                String currentStatus = chunkStatus != null ? (String) chunkStatus.get(blockKey) : null;

                if (currentStatus == null) {
                    currentStatus = defaultDoorState;
                }

                yield "open".equals(currentStatus) ? "closed" : "open";
            }
            default -> {
                log.warn("Unknown door value: {}", value);
                yield null;
            }
        };
    }

    @Override
    public boolean handleEntityAction(PlayerSession session, WEntity entity, String userAction, String entityAction,
                                      String shortcutKey, JsonNode params) {
        return false;
    }

    @Override
    public boolean handleItemAction(PlayerSession session, WItem item, String itemAction, JsonNode params) {
        return false;
    }

    @Override
    public boolean handlePlayerAction(PlayerSession session, String targetEntityId, String action, String shortcutKey,
                                      Long timestamp, JsonNode params) {
        return false;
    }
}
