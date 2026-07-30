package de.mhus.nimbus.world.player.gameplay;

import tools.jackson.databind.JsonNode;
import de.mhus.nimbus.world.player.session.PlayerSession;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class TeleportationAction extends AbstractGamplayAction {

    public TeleportationAction(BasicGameplay basic) {
        super(basic);
    }

    @Override
    public boolean handleAction(PlayerSession session, Map<String, String> serverParameters, JsonNode params) {
        // Check for teleportation entry
        String teleportTarget = serverParameters.get("target");
        if (teleportTarget == null || teleportTarget.isBlank()) {
            log.trace("No teleportation entry");
            return false;
        }
        // Trigger teleportation (PlayerService handles session save and redirect)
        log.info("Teleportation triggered: {}", teleportTarget);
        boolean success = basic.getPlayerService().teleportPlayer(session, teleportTarget);
        if (!success) {
            log.warn("Failed to trigger teleportation for player {}: target={}", session.getPlayer(), teleportTarget);
        }
        return success;
    }
}
