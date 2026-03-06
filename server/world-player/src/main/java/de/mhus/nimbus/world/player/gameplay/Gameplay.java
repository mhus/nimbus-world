package de.mhus.nimbus.world.player.gameplay;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.world.player.session.PlayerSession;

import java.util.Map;

public interface Gameplay {

    default String getName() {
        return getClass().getSimpleName();
    }

    /**
     * If the shortcut key is specified, the shortcut item action will be executed on the block.
     * Otherwise, session owner interacts with a block (e.g. right-click or left-click). Executes the
     * block action for 'interaction' defined in the block parameters on the player.
     *
     * @param session
     * @param x
     * @param y
     * @param z
     * @param blockId
     * @param groupId
     * @param userAction
     * @param shortcutKey
     * @param params
     */
    void onBlockInteraction(PlayerSession session, int x, int y, int z, String blockId, String groupId, String userAction, String shortcutKey, JsonNode params);

    /**
     * Session owner 'interacts' with another player (Space-Key or A-Button on X-Box).
     * If shortcut is provided, the shortcut action (item) will be executet on the other player.
     * Otherwise TODO: Currently not specified what happens.
     *
     * @param session
     * @param entityId
     * @param userAction
     * @param shortcutKey
     * @param timestamp
     * @param params
     */
    void onPlayerInteraction(PlayerSession session, String entityId, String userAction, String shortcutKey, Long timestamp, JsonNode params);

    /**
     * Called after session is authenticated and ready.
     * Gameplay implementations should initialize GameplayData here, optionally restoring from saved data.
     *
     * @param session The authenticated player session
     * @param savedGameplayData Previously saved gameplay data from WPlayerSession (can be null or empty)
     */
    void onSessionAuthenticated(PlayerSession session, Map<String, Object> savedGameplayData);

    /**
     * If the shortcut key is specified, the shortcut item action will be executed on the entity.
     * Otherwise, session owner 'interacts' with an entity (Space-Key or A-Button on X-Box).
     * This should execute the action for 'interaction', defined on the entity's parameters on the player.
     **/
     void onEntityInteraction(PlayerSession session, String entityId, String userAction, String shortcutKey, Long timestamp, JsonNode params);

    /**
     * The item action will be executed on the player.
     * Will be called if an item without a target is used.
     *
     * @param session
     * @param itemId
     * @param params
     */
    void onItemInteraction(PlayerSession session, String itemId, JsonNode params);

    /**
     * Session owner performs fall, underwater, ...
     *
     * @param session
     * @param action
     * @param data
     */
    void onSimpleInteraction(PlayerSession session, String action, JsonNode data);

    Map<String, Object> serialize(PlayerSession session);

    void onSessionTick(PlayerSession session, int count);

    void onShortcutModified(PlayerSession session);

    void onWearingModified(PlayerSession session);

    void onBackpackModified(PlayerSession session);

    void onSkillsModified(PlayerSession session);

    void onConstitutionModified(PlayerSession session);

    /**
     * Apply effects from an item's parameters to the player (or a target entity).
     *
     * @param session       The player session
     * @param parameters    The item's parameters map (contains "effects" list etc.)
     * @param targetEntityId Target entity ID, or null for self-application
     * @return true if effects were applied successfully
     */
    boolean useEffect(PlayerSession session, Map<String, String> parameters, String targetEntityId);

    /**
     * Maximum number of items (distinct slots and per-item amount) allowed in the backpack.
     * Will be derived from player skills in the future.
     */
    default int getMaxBackpackItems(PlayerSession session) {
        return 1000;
    }
}
