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

    void onSimpleInteraction(PlayerSession session, String action, String shortcutKey);

    Map<String, Object> serialize(PlayerSession session);

    void onSessionTick(PlayerSession session, int count);

    void onShortcutModified(PlayerSession session);

    void onWearingModified(PlayerSession session);

    void onBackpackModified(PlayerSession session);

    void onSkillsModified(PlayerSession session);

    /**
     * Apply effects from an item's parameters to the player (or a target entity).
     *
     * @param session       The player session
     * @param parameters    The item's parameters map (contains "effects" list etc.)
     * @param targetEntityId Target entity ID, or null for self-application
     * @return true if effects were applied successfully
     */
    boolean useEffect(PlayerSession session, Map<String, Object> parameters, String targetEntityId);
}
