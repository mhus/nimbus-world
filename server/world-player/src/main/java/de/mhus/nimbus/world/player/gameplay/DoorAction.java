package de.mhus.nimbus.world.player.gameplay;

import tools.jackson.databind.JsonNode;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.shared.utils.TypeUtil;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.shared.world.WChunk;
import de.mhus.nimbus.world.shared.world.WEntity;
import de.mhus.nimbus.world.shared.world.WItem;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.player.service.GameplayUtil;
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
 * - toggleType=auto/single/group (default: auto for door, single for window)
 *   - auto: also toggle adjacent blocks (up/down) with same action
 *   - single: only toggle this block
 *   - group: toggle all blocks in the chunk with the same toggleGroup and action
 * - toggleGroup=<name> (required when toggleType=group)
 * - sound_open=<path> (optional, sound to play when opening, default: n:audio/actions/door_open.ogg)
 * - sound_close=<path> (optional, sound to play when closing, default: n:audio/actions/door_close.ogg)
 */
@Slf4j
public class DoorAction implements GameplayAction {

    private static final String DEFAULT_DOOR_STATE = "closed";

    protected final BasicGameplay basic;

    public DoorAction(BasicGameplay basic) {
        this.basic = basic;
    }

    protected String getActionName() {
        return "door";
    }

    protected String getDefaultToggleType() {
        return "auto";
    }

    protected String getDefaultSoundOpen() {
        return GameplayUtil.SOUND_DOOR_OPEN;
    }

    protected String getDefaultSoundClose() {
        return GameplayUtil.SOUND_DOOR_CLOSE;
    }

    @Override
    public boolean handleBlockAction(PlayerSession session, int x, int y, int z, String blockId, String groupId,
                                     String blockAction, JsonNode params, String userAction, String shortcutKey,
                                     Map<String, String> serverInfo) {
        if (session.getWorldId() == null) return false;
        if (!basic.canUseBlock(session, x, y, z, serverInfo)) return false;

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

        // Play sound at the interacted block position (only once)
        playSound(session, serverInfo, newStatus, x, y, z);

        // Fire logic effect with status variables for placeholder replacement
        basic.fireLogicEffect(session, serverInfo, Map.of(
                "status", newStatus,
                "open", String.valueOf("open".equals(newStatus)),
                "closed", String.valueOf("closed".equals(newStatus))
        ));

        log.debug("{} action: worldId={}, chunkKey={}, targets={}, status={}", getActionName(), worldId, chunkKey, blockKeys, newStatus);
        return true;
    }

    protected void playSound(PlayerSession session, Map<String, String> serverInfo, String newStatus, int x, int y, int z) {
        boolean isOpen = "open".equals(newStatus);
        String soundKey = isOpen ? "sound_open" : "sound_close";
        String soundValue = serverInfo != null ? serverInfo.get(soundKey) : null;
        String defaultSound = isOpen ? getDefaultSoundOpen() : getDefaultSoundClose();
        String sound = GameplayUtil.resolveSound(soundValue, defaultSound);
        basic.getBasicClientService().sendCommand(session, "playSoundAtPosition",
                List.of(sound, String.valueOf(x), String.valueOf(y), String.valueOf(z)));
    }

    /**
     * Collect all block keys that should be toggled based on toggleType.
     *
     * @return List of block keys ("x,y,z") to toggle, always includes the target itself
     */
    protected List<String> collectToggleTargets(WorldId worldId, Map<String, String> serverInfo, String chunkKey,
                                               int targetX, int targetY, int targetZ) {
        String targetKey = targetX + "," + targetY + "," + targetZ;
        String toggleType = serverInfo != null ? serverInfo.get("toggleType") : null;
        if (Strings.isBlank(toggleType)) {
            toggleType = getDefaultToggleType();
        }

        return switch (toggleType.toLowerCase()) {
            case "single" -> List.of(targetKey);
            case "group" -> collectGroupTargets(worldId, serverInfo, chunkKey, targetKey);
            default -> collectAutoTargets(worldId, chunkKey, targetX, targetY, targetZ, targetKey);
        };
    }

    /**
     * Auto-detect: find adjacent blocks (up/down) in the same chunk with same action.
     */
    protected List<String> collectAutoTargets(WorldId worldId, String chunkKey,
                                             int targetX, int targetY, int targetZ, String targetKey) {
        WChunk chunk = basic.getChunkService().find(worldId, chunkKey).orElse(null);
        if (chunk == null || chunk.getInfoServer() == null) {
            return List.of(targetKey);
        }

        String actionName = getActionName();
        List<String> targets = new ArrayList<>();
        targets.add(targetKey);

        // Check blocks above and below (up to 2 blocks in each direction)
        int[][] offsets = {{0, 1, 0}, {0, -1, 0}, {0, 2, 0}, {0, -2, 0}};
        for (int[] offset : offsets) {
            String adjacentKey = (targetX + offset[0]) + "," + (targetY + offset[1]) + "," + (targetZ + offset[2]);
            Map<String, String> adjacentInfo = chunk.getInfoServer().get(adjacentKey);
            if (adjacentInfo != null && actionName.equals(adjacentInfo.get("action"))) {
                targets.add(adjacentKey);
            }
        }

        return targets;
    }

    /**
     * Group: find all blocks in the same chunk with matching toggleGroup and same action.
     */
    protected List<String> collectGroupTargets(WorldId worldId, Map<String, String> serverInfo,
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

        String actionName = getActionName();
        List<String> targets = new ArrayList<>();
        for (var entry : chunk.getInfoServer().entrySet()) {
            Map<String, String> info = entry.getValue();
            if (actionName.equals(info.get("action")) && groupName.equals(info.get("toggleGroup"))) {
                targets.add(entry.getKey());
            }
        }

        // Ensure target is included
        if (!targets.contains(targetKey)) {
            targets.add(targetKey);
        }

        return targets;
    }

    protected String resolveStatus(String worldId, String chunkKey, String blockKey, String value, String defaultDoorState) {
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
