package de.mhus.nimbus.world.player.service;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.shared.world.WChunkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class GameplayService {

    private final WChunkService chunkService;
    private final PlayerService playerService;
    public void onPlayerEntityInteraction(PlayerSession session, String entityId, String action, Long timestamp, JsonNode params) {
        log.info("Player {} interacted with entity {}: action={}, timestamp={}, params={}",
                session.getPlayer(), entityId, action, timestamp, params);
    }

    public void onPlayerBlockInteraction(PlayerSession session, int x, int y, int z, String blockId, String groupId, String action, JsonNode params) {
        log.info("Player {} interacted with block at ({}, {}, {}): blockId={}, groupId={}, action={}, params={}",
                session.getPlayer(), x, y, z, blockId, groupId, action, params);

        // Check for teleportation in server metadata
        if (session.getWorldId() != null) {
            handleBlockTeleportation(session, x, y, z);
        }
    }

    /**
     * Handle block teleportation if server metadata contains "teleportation" entry.
     *
     * @param session PlayerSession
     * @param x       Block x coordinate
     * @param y       Block y coordinate
     * @param z       Block z coordinate
     */
    private void handleBlockTeleportation(PlayerSession session, int x, int y, int z) {
        // Get server metadata for block from chunk service
        Map<String, String> serverInfo = chunkService.getServerInfo(session.getWorldId(), x, y, z);
        if (serverInfo == null || serverInfo.isEmpty()) {
            log.trace("No server metadata for block at ({}, {}, {})", x, y, z);
            return;
        }

        // Check for teleportation entry
        String teleportTarget = serverInfo.get("teleportation");
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
