package de.mhus.nimbus.world.player.gameplay;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.world.player.service.PlayerService;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.shared.world.WChunkService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class BasicGameplay implements Gameplay {

    @Autowired
    protected WChunkService chunkService;
    @Autowired
    protected PlayerService playerService;

    protected Map<String, BlockAction> blockActions = new HashMap<>();

    public BasicGameplay() {
            blockActions.put("teleport", new BlockTeleportation());
    }

    @Override
    public void onBlockInteraction(PlayerSession session, int x, int y, int z, String blockId, String groupId, String userAction, JsonNode params) {
        // Check for teleportation in server metadata
        var worldId = session.getWorldId();
        Map<String, String> serverInfo = chunkService.getServerInfo(session.getWorldId(), x, y, z);
        if (serverInfo == null || serverInfo.isEmpty()) {
            log.trace("No server metadata for block at {} ({}, {}, {})", worldId, x, y, z);
            return;
        }
        String blockAction = serverInfo.get("action");
        if (blockAction == null || blockAction.isBlank()) {
            log.warn("No action entry in server metadata for block at {} ({}, {}, {})", worldId, x, y, z);
            return;
        }
        var handler = blockActions.get(blockAction);
        if (handler == null) {
            log.warn("Unknown block action '{}' in server metadata for block at {} ({}, {}, {})", blockAction, worldId, x, y, z);
            return;
        }
        handler.handleBlockAction(session, x, y, z, blockId, groupId, userAction, params, blockAction, serverInfo);

    }

    @Override
    public void onPlayerInteraction(PlayerSession session, String entityId, String action, Long timestamp, JsonNode params) {
    //     session.getPlayer().character().getPublicData().getShortcuts()
    }

    @Override
    public void onEntityInteraction(PlayerSession session, String entityId, String action, Long timestamp, JsonNode params) {
        //     session.getPlayer().character().getPublicData().getShortcuts()
        // Publish interaction to Redis for world-life processing
        // PlayerRedisSenderService.publishEntityInteraction(session, entityId, action, timestamp, params);
    }

    @Override
    public void onSessionAuthenticated(PlayerSession session) {

    }

    private class BlockTeleportation implements BlockAction {

        /**
         * Handle block teleportation if server metadata contains "teleportation" entry.
         *
         * @param session     PlayerSession
         * @param x           Block x coordinate
         * @param y           Block y coordinate
         * @param z           Block z coordinate
         * @param blockId
         * @param groupId
         * @param userAction
         * @param params
         * @param blockAction
         */
        @Override
        public void handleBlockAction(PlayerSession session, int x, int y, int z, String blockId, String groupId, String userAction, JsonNode params, String blockAction, Map<String, String> serverInfo) {
            // Check for teleportation entry
            String teleportTarget = serverInfo.get("target");
            if (teleportTarget == null || teleportTarget.isBlank()) {
                log.trace("No teleportation entry in server metadata for block at ({}, {}, {})", x, y, z);
                return;
            }

            // Trigger teleportation (PlayerService handles session save and redirect)
            log.info("Teleportation triggered by block interaction at ({}, {}, {}): target={}",
                    x, y, z, teleportTarget);

            boolean success = playerService.teleportPlayer(session, teleportTarget);
            if (!success) {
                log.warn("Failed to trigger teleportation for player {}: target={}", session.getPlayer(), teleportTarget);
            }
        }
    }
}
