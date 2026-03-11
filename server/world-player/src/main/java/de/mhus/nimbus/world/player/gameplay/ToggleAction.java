package de.mhus.nimbus.world.player.gameplay;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.shared.utils.TypeUtil;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.shared.world.WEntity;
import de.mhus.nimbus.world.shared.world.WItem;
import de.mhus.nimbus.world.shared.world.WWorld;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * GameplayAction for toggling a single block through a list of states.
 *
 * Server parameters (from block metadata / serverInfo):
 * - action=toggle
 * - states=state1,state2,state3 (required, comma-separated list of states)
 * - type=cycle/random (default: cycle)
 *   - cycle: advance to the next state in the list
 *   - random: pick a random state from the list
 * - sound=<path> (optional, default: n:audio/actions/toggle.ogg)
 */
@Slf4j
public class ToggleAction implements GameplayAction {

    private final BasicGameplay basic;

    public ToggleAction(BasicGameplay basic) {
        this.basic = basic;
    }

    @Override
    public boolean handleBlockAction(PlayerSession session, int x, int y, int z, String blockId, String groupId,
                                     String blockAction, JsonNode params, String userAction, String shortcutKey,
                                     Map<String, String> serverInfo) {
        if (session.getWorldId() == null) return false;
        if (!basic.canUseBlock(session, x, y, z, serverInfo)) return false;

        // Parse states
        String statesStr = serverInfo != null ? serverInfo.get("states") : null;
        if (Strings.isBlank(statesStr)) {
            log.warn("Toggle action at ({},{},{}) missing required 'states' parameter", x, y, z);
            return false;
        }
        String[] states = statesStr.split(",");
        if (states.length < 2) {
            log.warn("Toggle action at ({},{},{}) needs at least 2 states, got {}", x, y, z, states.length);
            return false;
        }

        String worldId = session.getWorldId().getId();

        // Compute chunk key and block key
        Optional<WWorld> worldOpt = basic.getWorldService().getByWorldId(worldId);
        if (worldOpt.isEmpty()) {
            log.warn("World not found: {}", worldId);
            return false;
        }
        WWorld world = worldOpt.get();
        String chunkKey = world.getChunkKey(x, z);
        String blockKey = x + "," + y + "," + z;

        // Get current status
        var statusMap = basic.getProgressService().findBlockStatusForChunks(worldId, List.of(chunkKey));
        var chunkStatus = statusMap.get(chunkKey);
        String currentStatus = chunkStatus != null ? (String) chunkStatus.get(blockKey) : null;

        // Resolve next status
        String type = serverInfo != null ? serverInfo.get("type") : null;
        if (Strings.isBlank(type)) {
            type = "cycle";
        }

        String newStatus = switch (type.toLowerCase()) {
            case "random" -> resolveRandom(states, currentStatus);
            default -> resolveCycle(states, currentStatus);
        };

        // Apply status
        var sender = basic.getBlockStatusSenderService();
        if (newStatus.equals(states[0])) {
            // First state = default → remove from progress
            int[] cc = TypeUtil.parseChunkCoord(chunkKey);
            sender.removeAndBroadcast(worldId, chunkKey, cc[0], cc[1], blockKey);
        } else {
            sender.setAndBroadcast(worldId, chunkKey, blockKey, newStatus);
        }

        // Play sound
        String sound = serverInfo != null ? serverInfo.get("sound") : null;
        if (Strings.isBlank(sound)) {
            sound = "n:audio/actions/toggle.ogg";
        }
        basic.getBasicClientService().sendCommand(session, "playSoundAtPosition",
                List.of(sound, String.valueOf(x), String.valueOf(y), String.valueOf(z)));

        log.debug("Toggle action: worldId={}, block=({},{},{}), {} -> {}", worldId, x, y, z, currentStatus, newStatus);
        return true;
    }

    private String resolveCycle(String[] states, String currentStatus) {
        if (currentStatus == null) {
            return states[1];
        }
        for (int i = 0; i < states.length; i++) {
            if (states[i].equals(currentStatus)) {
                return states[(i + 1) % states.length];
            }
        }
        // Current status not in list → start at first
        return states[0];
    }

    private String resolveRandom(String[] states, String currentStatus) {
        if (states.length == 1) return states[0];
        // Pick random state different from current
        String picked;
        do {
            picked = states[ThreadLocalRandom.current().nextInt(states.length)];
        } while (picked.equals(currentStatus) && states.length > 1);
        return picked;
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
