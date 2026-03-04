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
import java.util.Random;

@Slf4j
public class CollectAction implements GameplayAction {

    private final AdventureGameplay adventure;
    private final Random random = new Random();

    public CollectAction(AdventureGameplay adventure) {
        this.adventure = adventure;
    }

    @Override
    public void handleBlockAction(PlayerSession session, int x, int y, int z, String blockId, String groupId, String blockAction, JsonNode params, String userAction, String shortcutKey, Map<String, String> serverInfo) {
        if (shortcutKey != null) return;
        collect(session, serverInfo);
    }

    @Override
    public void handleEntityAction(PlayerSession session, WEntity entity, String userAction, String entityAction, String shortcutKey, JsonNode params) {
        if (shortcutKey != null) return;
        if (entity == null || entity.getServer() == null) return;
        collect(session, entity.getServer());
    }

    @Override
    public void handleItemAction(PlayerSession session, WItem item, String itemAction, JsonNode params) {
        // Collect only via interact on blocks/entities
    }

    @Override
    public void handlePlayerAction(PlayerSession session, String targetEntityId, String action, String shortcutKey, Long timestamp, JsonNode params) {
        // Collect only via interact on blocks/entities
    }

    private void collect(PlayerSession session, Map<String, String> serverInfo) {
        if (!(session.getGameplayData() instanceof AdventureData data)) return;

        long now = System.currentTimeMillis();
        if (data.getNextCollectAllowed() > now) {
            log.debug("Collect on cooldown for player {} ({}ms remaining)",
                    session.getEntityId(), data.getNextCollectAllowed() - now);
            return;
        }

        String rewardStr = serverInfo.get("collectReward");
        if (rewardStr == null || rewardStr.isBlank()) {
            log.debug("No collectReward defined in server info");
            return;
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
                    boolean added = adventure.getGameplayService().putIntoBackpack(session, itemId, quantity);
                    if (added) {
                        log.info("Player {} collected {} x{} (probability {}%)",
                                session.getEntityId(), itemId, quantity, probability);
                    } else {
                        log.debug("Player {} backpack full, could not add {} x{}",
                                session.getEntityId(), itemId, quantity);
                    }
                }
            } catch (NumberFormatException e) {
                log.warn("Invalid number in collectReward entry '{}': {}", trimmed, e.getMessage());
            }
        }

        data.setNextCollectAllowed(now + cooldownSeconds * 1000L);
    }
}
