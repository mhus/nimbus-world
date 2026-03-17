package de.mhus.nimbus.world.player.gameplay.adventure;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.world.player.gameplay.AdventureData;
import de.mhus.nimbus.world.player.gameplay.AdventureGameplay;
import de.mhus.nimbus.world.player.gameplay.GameplayAction;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.shared.world.WEntity;
import de.mhus.nimbus.world.shared.world.WItem;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Revive action: uses a revive item on a dead player.
 * Consumes the item and publishes a REVIVE message via Redis
 * to the target player's pod for revival processing.
 */
@Slf4j
public class ReviveAction implements GameplayAction {

    private final AdventureGameplay gameplay;

    public ReviveAction(AdventureGameplay gameplay) {
        this.gameplay = gameplay;
    }

    @Override
    public boolean handleBlockAction(PlayerSession session, int x, int y, int z, String blockId, String groupId, String blockAction, JsonNode params, String userAction, String shortcutKey, Map<String, String> serverInfo) {
        return false;
    }

    @Override
    public boolean handleEntityAction(PlayerSession session, WEntity entity, String userAction, String entityAction, String shortcutKey, JsonNode params) {
        return false;
    }

    @Override
    public boolean handleItemAction(PlayerSession session, WItem item, String itemAction, JsonNode params) {
        return false;
    }

    @Override
    public boolean handlePlayerAction(PlayerSession session, String targetEntityId, String action, String shortcutKey, Long timestamp, JsonNode params) {
        if (targetEntityId == null) return false;
        return performRevive(session, targetEntityId, shortcutKey);
    }

    private boolean performRevive(PlayerSession session, String targetEntityId, String shortcutKey) {
        if (!(session.getGameplayData() instanceof AdventureData data)) return false;

        String worldId = session.getWorldId() != null ? session.getWorldId().getId() : null;
        if (worldId == null) return false;

        // Resolve the revive item from shortcut
        String itemId = gameplay.resolveShortcutItemId(session, shortcutKey);
        if (itemId == null) {
            log.debug("No revive item found for shortcut {}", shortcutKey);
            return false;
        }

        // Consume the revive item
        boolean consumed = gameplay.getGameplayService().reduceItem(session, itemId, 1);
        if (!consumed) {
            log.debug("Could not consume revive item {} for player {}", itemId, session.getEntityId());
            return false;
        }

        // Publish REVIVE via Redis to the target player
        gameplay.getVitalDeltaPublisher().publishRevive(worldId, targetEntityId, session.getEntityId());

        gameplay.getClientService().sendSystemNotification(session, "Revive",
                "Reviving " + targetEntityId);

        log.info("Player {} used revive item {} on {}", session.getEntityId(), itemId, targetEntityId);
        return true;
    }
}
