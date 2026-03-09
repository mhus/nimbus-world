package de.mhus.nimbus.world.player.gameplay;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.shared.utils.TypeUtil;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.shared.world.WEntity;
import de.mhus.nimbus.world.shared.world.WItem;
import de.mhus.nimbus.world.shared.world.WWorld;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;

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

        // Resolve final status
        String newStatus = resolveStatus(worldId, chunkKey, blockKey, value, defaultDoorState);
        if (newStatus == null) {
            return false;
        }

        // Set/remove status AND broadcast to clients
        var sender = basic.getBlockStatusSenderService();
        if (newStatus.equals(defaultDoorState)) {
            int[] cc = TypeUtil.parseChunkCoord(chunkKey);
            sender.removeAndBroadcast(worldId, chunkKey, cc[0], cc[1], blockKey);
        } else {
            sender.setAndBroadcast(worldId, chunkKey, blockKey, newStatus);
        }

        log.debug("Door action: worldId={}, chunkKey={}, blockKey={}, status={}", worldId, chunkKey, blockKey, newStatus);
        return true;
    }

    private String resolveStatus(String worldId, String chunkKey, String blockKey, String value, String defaultDoorState) {
        return switch (value.toLowerCase()) {
            case "open" -> "open";
            case "close", "closed" -> "closed";
            case "toggle" -> {
                // Check current status in WProgress
                var statusMap = basic.getProgressService().findBlockStatusForChunks(worldId, java.util.List.of(chunkKey));
                var chunkStatus = statusMap.get(chunkKey);
                String currentStatus = chunkStatus != null ? (String) chunkStatus.get(blockKey) : null;

                // If no override exists, the current state is the default
                if (currentStatus == null) {
                    currentStatus = defaultDoorState;
                }

                // Toggle
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
