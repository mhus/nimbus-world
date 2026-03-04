package de.mhus.nimbus.world.player.gameplay.adventure;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.world.player.gameplay.AdventureGameplay;
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
    public void handleBlockAction(PlayerSession session, int x, int y, int z, String blockId, String groupId, String blockAction, JsonNode params, String userAction, String shortcutKey, Map<String, String> serverInfo) {
        // No effects on blocks
    }

    @Override
    public void handleItemAction(PlayerSession session, WItem item, String itemAction, JsonNode params) {
        // Item used directly → self-application
        basic.getGameplayService().useItemEffect(session, item.getItemId(), null);
    }

    @Override
    public void handlePlayerAction(PlayerSession session, String targetEntityId, String action, String shortcutKey, Long timestamp, JsonNode params) {
        // Shortcut on player → apply effect on target player
        String itemId = basic.resolveShortcutItemId(session, shortcutKey);
        if (itemId == null) return;
        basic.getGameplayService().useItemEffect(session, itemId, targetEntityId);
    }

    @Override
    public void handleEntityAction(PlayerSession session, WEntity entity, String userAction, String entityAction, String shortcutKey, JsonNode params) {
        // Shortcut on entity → apply effect on entity
        String itemId = basic.resolveShortcutItemId(session, shortcutKey);
        if (itemId == null) return;
        String targetEntityId = entity != null ? entity.getEntityId() : null;
        basic.getGameplayService().useItemEffect(session, itemId, targetEntityId);
    }
}
