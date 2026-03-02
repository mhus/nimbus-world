package de.mhus.nimbus.world.player.gameplay;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.world.player.session.PlayerSession;

import java.util.Map;

public interface Gameplay {

    default String getName() {
        return getClass().getSimpleName();
    }

    void onBlockInteraction(PlayerSession session, int x, int y, int z, String blockId, String groupId, String userAction, JsonNode params);

    void onPlayerInteraction(PlayerSession session, String entityId, String userAction, Long timestamp, JsonNode params);

    void onSessionAuthenticated(PlayerSession session);

    void onEntityInteraction(PlayerSession session, String entityId, String action, Long timestamp, JsonNode params);

    Map<String, Object> serialize(PlayerSession session);
}
