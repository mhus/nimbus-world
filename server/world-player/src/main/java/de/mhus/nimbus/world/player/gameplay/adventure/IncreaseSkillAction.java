package de.mhus.nimbus.world.player.gameplay.adventure;

import tools.jackson.databind.JsonNode;
import de.mhus.nimbus.world.player.gameplay.AdventureData;
import de.mhus.nimbus.world.player.gameplay.AdventureGameplay;
import de.mhus.nimbus.world.player.gameplay.GameplayAction;
import de.mhus.nimbus.world.player.service.GameplayUtil;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.shared.gameplay.AdventureSkills;
import de.mhus.nimbus.world.shared.gameplay.Skill;
import de.mhus.nimbus.world.shared.world.WEntity;
import de.mhus.nimbus.world.shared.world.WItem;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class IncreaseSkillAction implements GameplayAction {

    private final AdventureGameplay adventure;

    public IncreaseSkillAction(AdventureGameplay adventure) {
        this.adventure = adventure;
    }

    @Override
    public boolean handleBlockAction(PlayerSession session, int x, int y, int z, String blockId, String groupId, String blockAction, JsonNode params, String userAction, String shortcutKey, Map<String, String> serverInfo) {
        return applySkill(session, GameplayUtil.extractParams(shortcutKey == null ? "int_" : "act_", serverInfo, null), null);
    }

    @Override
    public boolean handleEntityAction(PlayerSession session, WEntity entity, String userAction, String entityAction, String shortcutKey, JsonNode params) {
        if (entity == null || entity.getServer() == null) return false;
        return applySkill(session, GameplayUtil.extractParams(shortcutKey == null ? "int_" : "act_", entity.getServer(), null), null);
    }

    @Override
    public boolean handleItemAction(PlayerSession session, WItem item, String itemAction, JsonNode params) {
        return applySkill(session, GameplayUtil.extractParams("act_", item.getPublicData().getParameters(), item.getServer()), item.getName());
    }

    @Override
    public boolean handlePlayerAction(PlayerSession session, String targetEntityId, String action, String shortcutKey, Long timestamp, JsonNode params) {
        return false;
    }

    private boolean applySkill(PlayerSession session, Map<String, String> parameters, String consumeItemId) {
        if (!(session.getGameplayData() instanceof AdventureData data)) return false;

        String docId = data.getCachedCharacterDocId();
        if (docId == null) return false;

        String skillName = parameters.get("skill");
        if (skillName == null || skillName.isBlank()) {
            log.warn("No skill name defined in parameters");
            return false;
        }

        Skill skillDef = AdventureSkills.byName(skillName);
        if (skillDef == null) {
            log.warn("Unknown skill: {}", skillName);
            return false;
        }

        int amount = 1;
        String amountStr = parameters.get("amount");
        if (amountStr != null && !amountStr.isBlank()) {
            try {
                amount = Integer.parseInt(amountStr.trim());
            } catch (NumberFormatException e) {
                log.warn("Invalid amount value: {}", amountStr);
                return false;
            }
        }
        if (amount <= 0) return false;

        // Check current level
        int currentLevel = skillDef.getValue(data.getCachedSkills());
        if (currentLevel >= skillDef.getMax()) {
            adventure.getClientService().sendNotification(session, 3, "",
                    "Skill already maxed", "n:textures/actions/skill.png");
            return true;
        }

        // Clamp to max
        int actualIncrease = Math.min(amount, skillDef.getMax() - currentLevel);

        // Consume item before applying
        if (consumeItemId != null) {
            adventure.getGameplayService().reduceItem(session, consumeItemId, 1);
        }

        adventure.getCharacterService().incrementSkillAtomic(docId, skillName, actualIncrease);
        adventure.getGameplayService().onSkillsModified(session);

        adventure.getClientService().sendNotification(session, 3, "",
                "+ " + actualIncrease + " " + skillDef.getTitle(), "n:textures/actions/skill.png");
        log.info("Player {} increased skill {} by {} (was {}, now {})",
                session.getEntityId(), skillName, actualIncrease, currentLevel, currentLevel + actualIncrease);
        return true;
    }
}
