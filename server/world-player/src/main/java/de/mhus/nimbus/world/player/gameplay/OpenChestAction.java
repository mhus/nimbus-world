package de.mhus.nimbus.world.player.gameplay;

import tools.jackson.databind.JsonNode;
import de.mhus.nimbus.world.player.service.GameplayUtil;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.shared.world.WChest;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Action handler for opening a world chest.
 *
 * Server parameters:
 * - chest: chest name (WChest.name) in the current world
 *
 * Flow:
 * 1. Resolve WChest by name and worldId
 * 2. Acquire WLease (type="chest-access", resourceId=chestName)
 * 3. Send openComponent command to client with the progressId
 */
@Slf4j
public class OpenChestAction extends AbstractGamplayAction {

    public OpenChestAction(BasicGameplay basic) {
        super(basic);
    }

    @Override
    public boolean handleAction(PlayerSession session, Map<String, String> serverParameters, JsonNode params) {
        if (session.getWorldId() == null) return false;

        String chestName = serverParameters.get("chest");
        if (Strings.isBlank(chestName)) {
            log.warn("open.chest action missing 'chest' parameter");
            return false;
        }

        String worldId = session.getWorldId().getId();

        // Verify chest exists
        Optional<WChest> chestOpt = basic.getChestService().getByWorldIdAndName(worldId, chestName);
        if (chestOpt.isEmpty()) {
            log.warn("Chest not found: {} in world {}", chestName, worldId);
            basic.getBasicClientService().sendNotification(session, 0, "", "Chest not found", null);
            return false;
        }

        WChest chest = chestOpt.get();
        String playerId = session.getEntityId();

        // Acquire lease for chest access
        var lease = basic.getLeaseService().acquire(
                worldId,
                playerId,
                "chest-access",
                chestName,
                chest.getTitle(),
                Map.of("chestId", chestName)
        );

        // Play chest open sound
        String soundValue = serverParameters.get("sound_chest_open");
        String sound = GameplayUtil.resolveSound(soundValue, GameplayUtil.SOUND_CHEST_OPEN);
        basic.getBasicClientService().sendCommand(session, "playSound", List.of(sound));

        // Send openComponent command to client
        basic.getBasicClientService().sendCommand(session, "openComponent",
                List.of("chest", lease.getLeaseId()));

        log.debug("Sent open.chest to player {}: chest={}, leaseId={}",
                playerId, chestName, lease.getLeaseId());
        return true;
    }
}
