package de.mhus.nimbus.world.player.gameplay;

import tools.jackson.databind.JsonNode;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.player.service.GameplayUtil;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.shared.world.WAnything;
import de.mhus.nimbus.world.shared.world.WEntity;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Action handler for starting a dialog with an entity.
 *
 * Server parameters on WEntity:
 * - int_playbook: WAnything reference as "collection/name" containing the dialog definition
 * - profile: NPC profile name in npc-profiles collection (optional, used by DialogService)
 *
 * Flow:
 * 1. Resolve playbook from WAnything
 * 2. Create WProgress with type='dialog'
 * 3. Store playbook reference, entityId, portraitPath in progressData
 * 4. Send openComponent command to client with the progressId
 */
@Slf4j
public class DialogAction extends AbstractGamplayAction {

    public DialogAction(BasicGameplay basic) {
        super(basic);
    }

    @Override
    public boolean handleEntityAction(PlayerSession session, WEntity entity, String userAction,
                                       String entityAction, String shortcutKey, JsonNode params) {
        if (session.getWorldId() == null) return false;

        // Extract server parameters (int_ prefix for interaction)
        Map<String, String> serverParameters = GameplayUtil.extractParams(
                shortcutKey == null ? "int_" : "act_", entity.getServer(), null);

        String playbookRef = serverParameters.get("playbook");
        if (Strings.isBlank(playbookRef)) {
            log.warn("dialog action missing 'playbook' parameter on entity {}", entity.getName());
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

        // Acquire lease for this dialog session with entity info
        String playerId = session.getEntityId();

        Map<String, Object> leaseData = new HashMap<>();
        leaseData.put("playbook", playbookRef);
        leaseData.put("entityId", entity.getName());
        if (entity.getPortraitPath() != null) {
            leaseData.put("portraitPath", entity.getPortraitPath());
        }

        var lease = basic.getLeaseService().acquire(
                worldId.getId(),
                playerId,
                "dialog",
                entity.getName(),  // resourceId = entityId so each NPC has its own lease
                null,
                leaseData
        );

        // Send openComponent command to client (dialog_start is sent by world-control on first GET)
        basic.getBasicClientService().sendCommand(session, "openComponent",
                List.of("dialog", lease.getLeaseId()));

        log.debug("Sent dialog to player {}: playbook={}, entityId={}, leaseId={}",
                playerId, playbookRef, entity.getName(), lease.getLeaseId());
        return true;
    }

    @Override
    public boolean handleAction(PlayerSession session, Map<String, String> serverParameters, JsonNode params) {
        // Fallback for block-triggered dialogs (without entity reference)
        if (session.getWorldId() == null) return false;

        String playbookRef = serverParameters.get("playbook");
        if (Strings.isBlank(playbookRef) || !playbookRef.contains("/")) {
            log.warn("dialog action missing or invalid 'playbook' parameter");
            return false;
        }

        String[] parts = playbookRef.split("/", 2);
        WorldId worldId = session.getWorldId();
        WorldId anythingWorldId = worldId.isInstance() ? worldId.toMainWorld() : worldId;

        Optional<WAnything> playbookOpt = basic.getAnythingService()
                .findByWorldIdAndCollectionAndName(anythingWorldId.getId(), parts[0], parts[1]);

        if (playbookOpt.isEmpty()) {
            log.warn("Dialog playbook not found: {} in world {}", playbookRef, anythingWorldId);
            basic.getBasicClientService().sendNotification(session, 0, "", "Dialog not available", null);
            return false;
        }

        String playerId = session.getEntityId();
        var lease = basic.getLeaseService().acquire(
                worldId.getId(), playerId, "dialog", playbookRef,
                null, Map.of("playbook", playbookRef)
        );

        basic.getBasicClientService().sendCommand(session, "openComponent",
                List.of("dialog", lease.getLeaseId()));

        log.debug("Sent dialog to player {}: playbook={}, leaseId={}",
                playerId, playbookRef, lease.getLeaseId());
        return true;
    }
}
