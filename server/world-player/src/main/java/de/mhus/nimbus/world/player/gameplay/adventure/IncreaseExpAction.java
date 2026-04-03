package de.mhus.nimbus.world.player.gameplay.adventure;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.world.player.gameplay.AdventureData;
import de.mhus.nimbus.world.player.gameplay.AdventureGameplay;
import de.mhus.nimbus.world.player.gameplay.GameplayAction;
import de.mhus.nimbus.world.player.service.GameplayUtil;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.shared.world.WEntity;
import de.mhus.nimbus.world.shared.world.WItem;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class IncreaseExpAction implements GameplayAction {

    private final AdventureGameplay adventure;

    public IncreaseExpAction(AdventureGameplay adventure) {
        this.adventure = adventure;
    }

    @Override
    public boolean handleBlockAction(PlayerSession session, int x, int y, int z, String blockId, String groupId, String blockAction, JsonNode params, String userAction, String shortcutKey, Map<String, String> serverInfo) {
        return applyExp(session, GameplayUtil.extractParams(shortcutKey == null ? "int_" : "act_", serverInfo, null), null);
    }

    @Override
    public boolean handleEntityAction(PlayerSession session, WEntity entity, String userAction, String entityAction, String shortcutKey, JsonNode params) {
        if (entity == null || entity.getServer() == null) return false;
        return applyExp(session, GameplayUtil.extractParams(shortcutKey == null ? "int_" : "act_", entity.getServer(), null), null);
    }

    @Override
    public boolean handleItemAction(PlayerSession session, WItem item, String itemAction, JsonNode params) {
        return applyExp(session, GameplayUtil.extractParams("act_", item.getPublicData().getParameters(), item.getServer()), item.getName());
    }

    @Override
    public boolean handlePlayerAction(PlayerSession session, String targetEntityId, String action, String shortcutKey, Long timestamp, JsonNode params) {
        return false;
    }

    private boolean applyExp(PlayerSession session, Map<String, String> parameters, String consumeItemId) {
        if (!(session.getGameplayData() instanceof AdventureData data)) return false;

        String docId = data.getCachedCharacterDocId();
        if (docId == null) return false;

        String expStr = parameters.get("exp");
        if (expStr == null || expStr.isBlank()) {
            log.warn("No exp value defined in parameters");
            return false;
        }

        long exp;
        try {
            exp = Long.parseLong(expStr.trim());
        } catch (NumberFormatException e) {
            log.warn("Invalid exp value: {}", expStr);
            return false;
        }

        if (exp <= 0) return false;

        // Consume item if triggered via item action
        if (consumeItemId != null) {
            adventure.getGameplayService().reduceItem(session, consumeItemId, 1);
        }

        adventure.getCharacterService().addSkillExperience(docId, exp);
        adventure.getClientService().sendNotification(session, 3, "",
                "+ " + exp + " Exp", "n:textures/actions/exp.png");
        log.info("Player {} gained {} exp", session.getEntityId(), exp);
        return true;
    }
}
