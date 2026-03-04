package de.mhus.nimbus.world.player.gameplay.adventure;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.world.player.gameplay.AdventureGameplay;
import de.mhus.nimbus.world.player.gameplay.GameplayAction;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.shared.world.WEntity;
import de.mhus.nimbus.world.shared.world.WItem;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EffectAction implements GameplayAction {

    protected final AdventureGameplay basic;

    public EffectAction(AdventureGameplay basic) {
        this.basic = basic;
    }

    @Override
    public void handleBlockAction(PlayerSession session, int x, int y, int z, String blockId, String groupId, String blockAction, JsonNode params, String userAction, String shortcutKey, Map<String, String> serverInfo) {
        if (shortcutKey != null) {
            // 'use' on block - no effects on blocks
            return;
        }
        // 'interact' on effect block → apply block effects to self
        applyServerEffects(session, serverInfo);
    }

    @Override
    public void handleEntityAction(PlayerSession session, WEntity entity, String userAction, String entityAction, String shortcutKey, JsonNode params) {
        if (shortcutKey != null) {
            // 'use' on entity → apply item effects to entity
            String itemId = basic.resolveShortcutItemId(session, shortcutKey);
            if (itemId == null) return;
            String targetEntityId = entity != null ? entity.getEntityId() : null;
            basic.getGameplayService().useItemEffect(session, itemId, targetEntityId);
        } else {
            // 'interact' on effect entity → apply entity effects to self
            if (entity != null && entity.getServer() != null) {
                applyServerEffects(session, entity.getServer());
            }
        }
    }

    @Override
    public void handleItemAction(PlayerSession session, WItem item, String itemAction, JsonNode params) {
        // Item used directly → self-application
        basic.getGameplayService().useItemEffect(session, item.getItemId(), null);
    }

    @Override
    public void handlePlayerAction(PlayerSession session, String targetEntityId, String action, String shortcutKey, Long timestamp, JsonNode params) {
        // Only 'use' on player (interact on player goes through separate method, not GameplayAction)
        String itemId = basic.resolveShortcutItemId(session, shortcutKey);
        if (itemId == null) return;
        basic.getGameplayService().useItemEffect(session, itemId, targetEntityId);
    }

    /**
     * Apply effects from a block's or entity's server info to the interacting player (self).
     * Effects are stored as comma-separated effect definitions in the "effects" key.
     */
    private void applyServerEffects(PlayerSession session, Map<String, String> serverInfo) {
        String effects = serverInfo.get("effects");
        if (effects == null || effects.isBlank()) return;

        List<String> effectList = Arrays.stream(effects.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        Map<String, Object> parameters = new HashMap<>();
        parameters.put("effects", effectList);
        parameters.put("name", serverInfo.getOrDefault("name", "interaction"));

        basic.useEffect(session, parameters, null);
    }
}
