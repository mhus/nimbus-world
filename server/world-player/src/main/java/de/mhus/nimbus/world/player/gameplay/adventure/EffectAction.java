package de.mhus.nimbus.world.player.gameplay.adventure;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.world.player.gameplay.AbstractGamplayAction;
import de.mhus.nimbus.world.player.gameplay.AdventureGameplay;
import de.mhus.nimbus.world.player.gameplay.BasicGameplay;
import de.mhus.nimbus.world.player.gameplay.GameplayAction;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.shared.world.WEntity;
import de.mhus.nimbus.world.shared.world.WItem;

import java.util.Map;

public class EffectAction implements GameplayAction {

    protected final AdventureGameplay basic;

    public EffectAction(AdventureGameplay basic) {
        this.basic = basic;
    }

    @Override
    public void handleBlockAction(PlayerSession session, int x, int y, int z, String blockId, String groupId, String blockAction, JsonNode params, String userAction, Map<String, String> serverInfo) {

    }

    @Override
    public void handleEntityAction(PlayerSession session, WEntity entity, String userAction, String entityAction, JsonNode params) {

    }

    @Override
    public void handleItemAction(PlayerSession session, WItem item, String itemAction, JsonNode params) {
        // Item used directly → self-application
        basic.getGameplayService().useItemEffect(session, item.getItemId(), null);
    }

    @Override
    public void handlePlayerAction(PlayerSession session, String targetEntityId, String action, Long timestamp, JsonNode params) {
        // Player interaction → apply effect on target player
        String itemId = resolveItemId(session, params);
        if (itemId == null) {
            return;
        }
        basic.getGameplayService().useItemEffect(session, itemId, targetEntityId);
    }

    @Override
    public void handleEntityAction(PlayerSession session, WEntity entity, String userAction, String entityAction, JsonNode params) {
        // Entity interaction → apply effect on entity
        String itemId = resolveItemId(session, params);
        if (itemId == null) {
            return;
        }
        basic.getGameplayService().useItemEffect(session, itemId, entity.getEntityId());
    }

    /**
     * Resolve the itemId from params (shortcutKey → shortcut → itemId).
     */
    private String resolveItemId(PlayerSession session, JsonNode params) {
        // Try to get shortcutKey from params
        String shortcutKey = null;
        if (params != null && params.has("shortcutKey")) {
            shortcutKey = params.get("shortcutKey").asText(null);
        }
        if (shortcutKey == null) return null;

        // Look up shortcut to get itemId
        var character = session.getPlayer() != null ? session.getPlayer().character() : null;
        var playerInfo = character != null ? character.getPublicData() : null;
        if (playerInfo == null || playerInfo.getShortcuts() == null) return null;

        var shortcut = playerInfo.getShortcuts().get(shortcutKey);
        if (shortcut == null || shortcut.getItemId() == null) return null;

        return shortcut.getItemId();
    }
}
