package de.mhus.nimbus.world.player.gameplay;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.world.player.service.GameplayUtil;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.shared.world.WEntity;
import de.mhus.nimbus.world.shared.world.WItem;

import java.util.Map;

public abstract class AbstractGamplayAction implements GameplayAction {

    protected final BasicGameplay basic;

    public AbstractGamplayAction(BasicGameplay basic) {
        this.basic = basic;
    }

    @Override
    public boolean handleBlockAction(PlayerSession session, int x, int y, int z, String blockId, String groupId, String blockAction, JsonNode params, String userAction, String shortcutKey, Map<String, String> serverInfo) {
        return handleAction(session, GameplayUtil.extractParams(shortcutKey == null ? "int_" : "act_", serverInfo, null), params);
    }

    @Override
    public boolean handleEntityAction(PlayerSession session, WEntity entity, String userAction, String entityAction, String shortcutKey, JsonNode params) {
        return handleAction(session, GameplayUtil.extractParams(shortcutKey == null ? "int_" : "act_", entity.getServer(), null), params);
    }

    @Override
    public boolean handleItemAction(PlayerSession session, WItem item, String itemAction, JsonNode params) {
        return handleAction(session, GameplayUtil.extractParams("act_", item.getPublicData().getParameters(), item.getServer()), params);
    }

    @Override
    public boolean handlePlayerAction(PlayerSession session, String targetEntityId, String action, String shortcutKey, Long timestamp, JsonNode params) {
        return handleAction(session, Map.of(), params);
    }

    public abstract boolean handleAction(PlayerSession session, Map<String, String> serverParameters, JsonNode params);

}
