package de.mhus.nimbus.world.player.gameplay.adventure;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.world.player.gameplay.AdventureGameplay;
import de.mhus.nimbus.world.player.gameplay.GameplayAction;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.shared.world.WEntity;
import de.mhus.nimbus.world.shared.world.WItem;

import java.util.HashMap;
import java.util.Map;

public class EffectAction implements GameplayAction {

    protected final AdventureGameplay basic;

    public EffectAction(AdventureGameplay basic) {
        this.basic = basic;
    }

    @Override
    public boolean handleBlockAction(PlayerSession session, int x, int y, int z, String blockId, String groupId, String blockAction, JsonNode params, String userAction, String shortcutKey, Map<String, String> serverInfo) {
        if (shortcutKey != null) {
            // 'use' on block - no effects on blocks
            return false;
        }
        // 'interact' on effect block → apply block effects to self
        applyServerEffects(session, serverInfo);
        return true;
    }

    @Override
    public boolean handleEntityAction(PlayerSession session, WEntity entity, String userAction, String entityAction, String shortcutKey, JsonNode params) {
        if (shortcutKey != null) {
            // 'use' on entity → apply item effects to entity
            String itemId = basic.resolveShortcutItemId(session, shortcutKey, params);
            if (itemId == null) return false;
            String targetEntityId = entity != null ? entity.getEntityId() : null;
            basic.getGameplayService().useItemEffect(session, itemId, targetEntityId);
            return true;
        } else {
            // 'interact' on effect entity → apply entity effects to self
            if (entity != null && entity.getServer() != null) {
                applyServerEffects(session, entity.getServer());
                return true;
            }
            return false;
        }
    }

    @Override
    public boolean handleItemAction(PlayerSession session, WItem item, String itemAction, JsonNode params) {
        // Item used directly → self-application
        basic.getGameplayService().useItemEffect(session, item.getItemId(), null);
        return true;
    }

    @Override
    public boolean handlePlayerAction(PlayerSession session, String targetEntityId, String action, String shortcutKey, Long timestamp, JsonNode params) {
        // Only 'use' on player (interact on player goes through separate method, not GameplayAction)
        String itemId = basic.resolveShortcutItemId(session, shortcutKey, params);
        if (itemId == null) return false;
        basic.getGameplayService().useItemEffect(session, itemId, targetEntityId);
        return true;
    }

    /**
     * Apply effects from a block's or entity's server info to the interacting player (self).
     * Effects are stored as comma-separated effect definitions in the "effects" key.
     */
    private void applyServerEffects(PlayerSession session, Map<String, String> serverInfo) {
        String effects = serverInfo.get("effects");
        if (effects == null || effects.isBlank()) return;

        Map<String, String> parameters = new HashMap<>();
        parameters.put("effects", effects);
        parameters.put("name", serverInfo.getOrDefault("name", "interaction"));

        basic.useEffect(session, parameters, null);
    }
}
