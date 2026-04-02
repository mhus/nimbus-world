package de.mhus.nimbus.world.player.gameplay;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.world.player.session.PlayerSession;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Action handler for opening the player-interact widget when clicking on another player
 * without an item shortcut.
 *
 * Flow:
 * 1. Acquire WLease (type="player-interact") with target player info
 * 2. Send openComponent command to client with the leaseId
 */
@Slf4j
public class PlayerInteractAction extends AbstractGamplayAction {

    public PlayerInteractAction(BasicGameplay basic) {
        super(basic);
    }

    @Override
    public boolean handlePlayerAction(PlayerSession session, String targetEntityId, String action,
                                       String shortcutKey, Long timestamp, JsonNode params) {
        if (session.getWorldId() == null) return false;

        String worldId = session.getWorldId().getId();
        String playerId = session.getEntityId();

        Map<String, Object> leaseData = new HashMap<>();
        leaseData.put("targetEntityId", targetEntityId);

        var lease = basic.getLeaseService().acquire(
                worldId,
                playerId,
                "player-interact",
                targetEntityId,
                null,
                leaseData
        );

        basic.getBasicClientService().sendCommand(session, "openComponent",
                List.of("player-interact", lease.getLeaseId()));

        log.debug("Sent player-interact to player {}: target={}, leaseId={}",
                playerId, targetEntityId, lease.getLeaseId());
        return true;
    }

    @Override
    public boolean handleAction(PlayerSession session, Map<String, String> serverParameters, JsonNode params) {
        // Not used — player interaction is handled via handlePlayerAction directly
        return false;
    }
}
