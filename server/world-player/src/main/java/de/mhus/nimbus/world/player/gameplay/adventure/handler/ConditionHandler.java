package de.mhus.nimbus.world.player.gameplay.adventure.handler;

import de.mhus.nimbus.world.player.gameplay.AdventureGameplay;
import de.mhus.nimbus.world.player.session.PlayerSession;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * Handles block usage conditions such as key-based access checks.
 */
@Slf4j
public class ConditionHandler {

    private final AdventureGameplay gameplay;

    public ConditionHandler(AdventureGameplay gameplay) {
        this.gameplay = gameplay;
    }

    /**
     * Check if a player can use/interact with a block based on its serverInfo conditions.
     *
     * @param session    The player session
     * @param x          Block X coordinate
     * @param y          Block Y coordinate
     * @param z          Block Z coordinate
     * @param serverInfo Block server metadata
     * @return true if the block can be used
     */
    public boolean canUseBlock(PlayerSession session, int x, int y, int z, Map<String, String> serverInfo) {
        if (serverInfo == null) return true;

        String condition = serverInfo.get("condition");
        if (condition == null) return true;

        return switch (condition.toLowerCase()) {
            case "key" -> checkKeyCondition(session, serverInfo);
            default -> {
                log.warn("Unknown block condition '{}' at ({},{},{})", condition, x, y, z);
                yield true;
            }
        };
    }

    /**
     * Check if the player has a key item in their backpack matching the required keyId.
     */
    public boolean checkKeyCondition(PlayerSession session, Map<String, String> serverInfo) {
        String requiredKeyId = serverInfo.get("keyId");
        if (requiredKeyId == null) {
            log.warn("condition=key but no keyId specified");
            return true;
        }

        var keyItems = gameplay.getGameplayService().findItemsByEffect(session, "key");
        for (var item : keyItems) {
            if (item.getServer() != null && requiredKeyId.equals(item.getServer().get("keyId"))) {
                log.debug("Key condition met: player has key with keyId={}", requiredKeyId);
                return true;
            }
        }

        log.debug("Key condition NOT met: player missing key with keyId={}", requiredKeyId);
        return false;
    }
}
