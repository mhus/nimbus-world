package de.mhus.nimbus.world.player.gameplay.adventure;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.shared.types.PlayerId;
import de.mhus.nimbus.world.player.gameplay.AdventureData;
import de.mhus.nimbus.world.player.gameplay.AdventureGameplay;
import de.mhus.nimbus.world.player.gameplay.GameplayAction;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.shared.world.WEntity;
import de.mhus.nimbus.world.shared.world.WItem;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Random;

@Slf4j
public class CollectAction implements GameplayAction {

    private final AdventureGameplay adventure;
    private final Random random = new Random();

    public CollectAction(AdventureGameplay adventure) {
        this.adventure = adventure;
    }

    @Override
    public boolean handleBlockAction(PlayerSession session, int x, int y, int z, String blockId, String groupId, String blockAction, JsonNode params, String userAction, String shortcutKey, Map<String, String> serverInfo) {
        if (shortcutKey != null) return false;
        return collect(session, serverInfo);
    }

    @Override
    public boolean handleEntityAction(PlayerSession session, WEntity entity, String userAction, String entityAction, String shortcutKey, JsonNode params) {
        if (shortcutKey != null) return false;
        if (entity == null || entity.getServer() == null) return false;
        return collect(session, entity.getServer());
    }

    @Override
    public boolean handleItemAction(PlayerSession session, WItem item, String itemAction, JsonNode params) {
        // Collect only via interact on blocks/entities
        return false;
    }

    @Override
    public boolean handlePlayerAction(PlayerSession session, String targetEntityId, String action, String shortcutKey, Long timestamp, JsonNode params) {
        // Collect only via interact on blocks/entities
        return false;
    }

    private boolean collect(PlayerSession session, Map<String, String> serverInfo) {
        if (!(session.getGameplayData() instanceof AdventureData data)) return false;

        long now = System.currentTimeMillis();
        if (data.getNextCollectAllowed() > now) {
            log.debug("Collect on cooldown for player {} ({}ms remaining)",
                    session.getEntityId(), data.getNextCollectAllowed() - now);
            return false;
        }

        String rewardStr = serverInfo.get("collectReward");
        if (rewardStr == null || rewardStr.isBlank()) {
            log.debug("No collectReward defined in server info");
            return false;
        }

        int cooldownSeconds = 0;
        String cooldownStr = serverInfo.get("collectCooldown");
        if (cooldownStr != null && !cooldownStr.isBlank()) {
            try {
                cooldownSeconds = Integer.parseInt(cooldownStr.trim());
            } catch (NumberFormatException e) {
                log.warn("Invalid collectCooldown value: {}", cooldownStr);
            }
        }

        String[] rewards = rewardStr.split(",");
        for (String reward : rewards) {
            String trimmed = reward.trim();
            if (trimmed.isEmpty()) continue;

            String[] parts = trimmed.split(":");
            if (parts.length != 3) {
                log.warn("Invalid collectReward entry '{}', expected probability:itemId:quantity", trimmed);
                continue;
            }

            try {
                int probability = Integer.parseInt(parts[0].trim());
                String itemId = parts[1].trim();
                int quantity = Integer.parseInt(parts[2].trim());

                if (random.nextInt(100) < probability) {
                    if (handleSyntheticReward(session, data, itemId, quantity)) {
                        log.info("Player {} collected synthetic {} x{} (probability {}%)",
                                session.getEntityId(), itemId, quantity, probability);
                    } else {
                        boolean added = adventure.getGameplayService().putIntoBackpack(session, itemId, quantity);
                        if (added) {
                            sendCollectNotification(session, itemId, quantity);
                            log.info("Player {} collected {} x{} (probability {}%)",
                                    session.getEntityId(), itemId, quantity, probability);
                        } else {
                            log.debug("Player {} backpack full, could not add {} x{}",
                                    session.getEntityId(), itemId, quantity);
                        }
                    }
                }
            } catch (NumberFormatException e) {
                log.warn("Invalid number in collectReward entry '{}': {}", trimmed, e.getMessage());
            }
        }

        data.setNextCollectAllowed(now + cooldownSeconds * 1000L);
        return true;
    }

    private boolean handleSyntheticReward(PlayerSession session, AdventureData data, String itemId, int quantity) {
        String docId = data.getCachedCharacterDocId();
        if (docId == null) return false;

        switch (itemId) {
            case "_gold_" -> {
                String entityId = session.getEntityId();
                PlayerId playerId = entityId != null ? PlayerId.of(entityId).orElse(null) : null;
                if (playerId == null) return false;
                var userOpt = adventure.getUserService().getByUsername(playerId.getUserId());
                if (userOpt.isEmpty()) return false;
                adventure.getUserService().changeGold(userOpt.get().getId(), quantity);
                adventure.getClientService().sendNotification(session, 3, "",
                        "+ " + quantity + " Gold", "n:textures/currencies/gold-coin.png");
                return true;
            }
            case "_silver_" -> {
                adventure.getCharacterService().changeSilver(docId, quantity);
                adventure.getClientService().sendNotification(session, 3, "",
                        "+ " + quantity + " Silver", "n:textures/currencies/silver-coin.png");
                return true;
            }
            case "_exp_" -> {
                adventure.getCharacterService().addSkillExperience(docId, quantity);
                adventure.getClientService().sendNotification(session, 3, "",
                        "+ " + quantity + " Exp", "n:textures/actions/exp.png");
                return true;
            }
            case "_skill_" -> {
                adventure.getCharacterService().addSkillPoints(docId, quantity);
                adventure.getClientService().sendNotification(session, 3, "",
                        "+ " + quantity + " Skill", "n:textures/actions/skill.png");
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void sendCollectNotification(PlayerSession session, String itemId, int quantity) {
        try {
            // Try cached items first, then fall back to DB
            WItem item = null;
            if (session.getGameplayData() instanceof AdventureData data && data.getCachedItems() != null) {
                item = data.getCachedItems().get(itemId);
            }
            if (item == null) {
                item = adventure.getItemService().findByItemId(session.getWorldId(), itemId).orElse(null);
            }
            String title = item != null && item.getPublicData() != null ? item.getPublicData().getTitle() : itemId;
            String texture = item != null && item.getPublicData() != null ? item.getPublicData().getTexture() : null;
            adventure.getClientService().sendNotification(session, 3, "", "+ " + quantity + " " + title, texture);
        } catch (Exception e) {
            log.warn("Failed to send collect notification for {}: {}", itemId, e.getMessage());
        }
    }
}
