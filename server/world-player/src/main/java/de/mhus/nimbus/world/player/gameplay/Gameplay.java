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

    /**
     * Called after session is authenticated and ready.
     * Gameplay implementations should initialize GameplayData here, optionally restoring from saved data.
     *
     * @param session The authenticated player session
     * @param savedGameplayData Previously saved gameplay data from WPlayerSession (can be null or empty)
     */
    void onSessionAuthenticated(PlayerSession session, Map<String, Object> savedGameplayData);

    void onEntityInteraction(PlayerSession session, String entityId, String action, Long timestamp, JsonNode params);

    void onItemInteraction(PlayerSession session, String itemId, JsonNode params);

    Map<String, Object> serialize(PlayerSession session);

    void onSessionTick(PlayerSession session, int count);
}
