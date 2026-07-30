package de.mhus.nimbus.world.player.gameplay.adventure;

import tools.jackson.databind.JsonNode;
import de.mhus.nimbus.world.player.gameplay.AdventureData;
import de.mhus.nimbus.world.player.gameplay.AdventureGameplay;
import de.mhus.nimbus.world.player.gameplay.GameplayAction;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.shared.world.WEntity;
import de.mhus.nimbus.world.shared.world.WItem;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class RestoreConstitutionAction implements GameplayAction {

    private final AdventureGameplay adventure;

    public RestoreConstitutionAction(AdventureGameplay adventure) {
        this.adventure = adventure;
    }

    @Override
    public boolean handleBlockAction(PlayerSession session, int x, int y, int z, String blockId, String groupId, String blockAction, JsonNode params, String userAction, String shortcutKey, Map<String, String> serverInfo) {
        if (shortcutKey != null) return false;
        return restoreConstitution(session, serverInfo);
    }

    @Override
    public boolean handleEntityAction(PlayerSession session, WEntity entity, String userAction, String entityAction, String shortcutKey, JsonNode params) {
        if (shortcutKey != null) return false;
        if (entity == null || entity.getServer() == null) return false;
        return restoreConstitution(session, entity.getServer());
    }

    @Override
    public boolean handleItemAction(PlayerSession session, WItem item, String itemAction, JsonNode params) {
        return false;
    }

    @Override
    public boolean handlePlayerAction(PlayerSession session, String targetEntityId, String action, String shortcutKey, Long timestamp, JsonNode params) {
        return false;
    }

    private boolean restoreConstitution(PlayerSession session, Map<String, String> serverInfo) {
        if (!(session.getGameplayData() instanceof AdventureData data)) return false;

        String docId = data.getCachedCharacterDocId();
        if (docId == null) return false;

        // Check silver cost
        String costStr = serverInfo.get("cost");
        if (costStr != null && !costStr.isBlank()) {
            try {
                long cost = Long.parseLong(costStr.trim());
                if (cost > 0) {
                    boolean paid = adventure.getCharacterService().changeSilver(docId, -cost);
                    if (!paid) {
                        adventure.getClientService().sendNotification(session, 3, "",
                                "Not enough Silver", "n:textures/currencies/silver-coin.png");
                        return false;
                    }
                }
            } catch (NumberFormatException e) {
                log.warn("Invalid cost value: {}", costStr);
            }
        }

        boolean restored = false;

        // Check for restore.all
        String restoreAllStr = serverInfo.get("restore.all");
        if (restoreAllStr != null && !restoreAllStr.isBlank()) {
            double targetPercent = parsePercent(restoreAllStr);
            if (targetPercent > 0) {
                adventure.getCharacterService().restoreAllConstitution(docId);
                restored = true;
            }
        }

        // Check for individual restore.<category> entries
        for (var entry : serverInfo.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith("restore.") || key.equals("restore.all")) continue;

            String category = key.substring("restore.".length());
            double targetPercent = parsePercent(entry.getValue());
            if (targetPercent <= 0) continue;

            double targetValue = targetPercent / 100.0;
            adventure.getCharacterService().setConstitution(docId, category, targetValue);
            restored = true;
        }

        if (restored) {
            adventure.getGameplayService().onConstitutionModified(session);
            adventure.getClientService().sendNotification(session, 3, "",
                    "Repaired", "n:textures/actions/repair.png");
            log.info("Player {} restored constitution at block/entity", session.getEntityId());
        }

        return restored;
    }

    private double parsePercent(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            log.warn("Invalid restore percent value: {}", value);
            return 0;
        }
    }
}
