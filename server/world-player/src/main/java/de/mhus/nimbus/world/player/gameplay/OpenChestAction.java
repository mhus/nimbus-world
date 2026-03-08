package de.mhus.nimbus.world.player.gameplay;

import com.fasterxml.jackson.databind.JsonNode;
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
 * 2. Create or update WProgress (type="chest-access", quest=chestName)
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

        // Create or update WProgress for this chest access
        var progress = basic.getProgressService().save(
                worldId,
                playerId,
                "chest-access",
                chestName,
                chest.getTitle(),
                Map.of("chestId", chestName)
        );

        // Send openComponent command to client
        basic.getBasicClientService().sendCommand(session, "openComponent",
                List.of("chest", progress.getProgressId()));

        log.debug("Sent open.chest to player {}: chest={}, progressId={}",
                playerId, chestName, progress.getProgressId());
        return true;
    }
}
