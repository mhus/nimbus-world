package de.mhus.nimbus.world.player.gameplay;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.shared.world.WAnything;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Action handler for starting a dialog with an entity.
 *
 * Server parameters:
 * - playbook: WAnything reference as "collection/name" containing the dialog definition
 *
 * The playbook (WAnything) contains entityIds and dialog structure.
 *
 * Flow:
 * 1. Resolve playbook from WAnything
 * 2. Create or find WProgress with collection='dialogs' and name=playbookName (name part without /)
 * 3. Store playbook reference in progressData
 * 4. Send openComponent command to client with the progressId
 */
@Slf4j
public class DialogAction extends AbstractGamplayAction {

    public DialogAction(BasicGameplay basic) {
        super(basic);
    }

    @Override
    public boolean handleAction(PlayerSession session, Map<String, String> serverParameters, JsonNode params) {
        if (session.getWorldId() == null) return false;

        String playbookRef = serverParameters.get("playbook");
        if (Strings.isBlank(playbookRef)) {
            log.warn("dialog action missing 'playbook' parameter");
            return false;
        }

        if (!playbookRef.contains("/")) {
            log.warn("dialog action 'playbook' must be in format 'collection/name', got: {}", playbookRef);
            return false;
        }

        String[] parts = playbookRef.split("/", 2);
        String collection = parts[0];
        String name = parts[1];

        WorldId worldId = session.getWorldId();
        WorldId anythingWorldId = worldId.isInstance() ? worldId.toMainWorld() : worldId;

        // Verify playbook exists in WAnything
        Optional<WAnything> playbookOpt = basic.getAnythingService()
                .findByWorldIdAndCollectionAndName(anythingWorldId.getId(), collection, name);

        if (playbookOpt.isEmpty()) {
            log.warn("Dialog playbook not found: {} in world {}", playbookRef, anythingWorldId);
            basic.getBasicClientService().sendNotification(session, 0, "", "Dialog not available", null);
            return false;
        }

        // Create WProgress for this dialog session
        String playerId = session.getEntityId();
        String progressName = name; // use playbook name as progress name

        var progress = basic.getProgressService().save(
                worldId.getId(),
                playerId,
                "dialog",
                null,
                Map.of("playbook", playbookRef)
        );

        // Send openComponent command to client
        basic.getBasicClientService().sendCommand(session, "openComponent",
                List.of("dialog", progress.getProgressId()));

        log.debug("Sent dialog to player {}: playbook={}, progressId={}",
                playerId, playbookRef, progress.getProgressId());
        return true;
    }
}
