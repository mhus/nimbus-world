package de.mhus.nimbus.world.player.gameplay;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.world.shared.gameplay.CombatConstants;
import de.mhus.nimbus.world.player.service.GameplayService;
import de.mhus.nimbus.world.player.service.PlayerService;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.shared.region.RCharacterService;
import de.mhus.nimbus.world.shared.world.WChunkService;
import de.mhus.nimbus.world.shared.world.WEntity;
import de.mhus.nimbus.world.shared.redis.EntityStateRedisService;
import de.mhus.nimbus.world.shared.redis.VitalDeltaPublisher;
import de.mhus.nimbus.world.shared.world.WEntityService;
import de.mhus.nimbus.world.shared.world.WItem;
import de.mhus.nimbus.world.shared.world.WItemService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class BasicGameplay implements Gameplay {

    public static final String SHORTCUT_INTERACT_ACTION = "__shortcut_interact__";
    public static final String FIST_ITEM_ID = CombatConstants.FIST_ITEM_ID;
    public static final String BLOCK_ITEM_ID = CombatConstants.BLOCK_ITEM_ID;

    @Autowired
    protected WChunkService chunkService;
    @Autowired
    @Getter
    protected PlayerService playerService;
    @Autowired
    protected WEntityService entityService;
    @Autowired
    @Getter
    protected WItemService itemService;
    @Autowired
    @Getter
    protected RCharacterService characterService;
    @Autowired
    @Lazy
    @Getter
    protected GameplayService gameplayService;
    @Autowired
    protected EntityStateRedisService entityStateRedisService;
    @Autowired
    @Getter
    protected VitalDeltaPublisher vitalDeltaPublisher;

    protected Map<String, GameplayAction> actions = new HashMap<>();

    public BasicGameplay() {
            actions.put("teleport", new TeleportationAction(this));
    }

    @Override
    public void onBlockInteraction(PlayerSession session, int x, int y, int z, String blockId, String groupId, String userAction, String shortcutKey, JsonNode params) {
        if (!Strings.isBlank(shortcutKey)) {
            // Shortcut on block: route via item action
            String itemAction = resolveShortcutItemAction(session, shortcutKey);
            if (itemAction == null) {
                log.trace("No item action resolvable for shortcut '{}' on block at ({}, {}, {})", shortcutKey, x, y, z);
                return;
            }
            if (!SHORTCUT_INTERACT_ACTION.equals(itemAction)) { // in case of interact shortcut do the same as regular interaction, skip item action routing
                var handler = actions.get(itemAction);
                if (handler == null) {
                    log.trace("No handler for item action '{}' on block at ({}, {}, {})", itemAction, x, y, z);
                    return;
                }
                boolean success = handler.handleBlockAction(session, x, y, z, blockId, groupId, itemAction, params, userAction, shortcutKey, Map.of());
                if (success) sendItemUseFeedback(session, shortcutKey);
                return;
            }
        }
        // Regular interaction: route via block server metadata
        var worldId = session.getWorldId();
        Map<String, String> serverInfo = chunkService.getServerInfo(session.getWorldId(), x, y, z);
        if (serverInfo == null || serverInfo.isEmpty()) {
            log.trace("No server metadata for block at {} ({}, {}, {})", worldId, x, y, z);
            return;
        }
        String blockAction = serverInfo.get("action");
        if (blockAction == null || blockAction.isBlank()) {
            log.warn("No action entry in server metadata for block at {} ({}, {}, {})", worldId, x, y, z);
            return;
        }
        var handler = actions.get(blockAction);
        if (handler == null) {
            log.warn("Unknown block action '{}' in server metadata for block at {} ({}, {}, {})", blockAction, worldId, x, y, z);
            return;
        }
        handler.handleBlockAction(session, x, y, z, blockId, groupId, blockAction, params, userAction, null, serverInfo);
    }

    @Override
    public void onPlayerInteraction(PlayerSession session, String entityId, String userAction, String shortcutKey, Long timestamp, JsonNode params) {
        if (Strings.isBlank(shortcutKey)) {
            log.trace("Player interaction '{}' with {} without shortcut in world {}", userAction, entityId, session.getWorldId());
            return;
        }
        String itemAction = resolveShortcutItemAction(session, shortcutKey);
        if (itemAction == null) {
            log.trace("No item action resolvable for shortcut '{}' on player {}", shortcutKey, entityId);
            return;
        }
        if (!SHORTCUT_INTERACT_ACTION.equals(itemAction)) { // in case of interact shortcut do the same as regular interaction, skip item action routing
            var handler = actions.get(itemAction);
            if (handler == null) {
                log.trace("No handler for item action '{}' on player {}", itemAction, entityId);
                return;
            }
            boolean success = handler.handlePlayerAction(session, entityId, itemAction, shortcutKey, timestamp, params);
            if (success) sendItemUseFeedback(session, shortcutKey);
        }
    }

    @Override
    public void onEntityInteraction(PlayerSession session, String entityId, String userAction, String shortcutKey, Long timestamp, JsonNode params) {
        // Proximity notification: forward to world-life
        if ("entityProximity".equals(userAction)) {
            boolean entered = params != null && params.has("entered") && params.get("entered").asBoolean();
            if (entered) {
                String worldId = session.getWorldId() != null ? session.getWorldId().getId() : null;
                if (worldId != null) {
                    vitalDeltaPublisher.publishProximity(
                            worldId, entityId, session.getEntityId(), session.getSessionId());
                }
            }
            return;
        }
        if (!Strings.isBlank(shortcutKey)) {
            // Shortcut on entity: route via item action
            String itemAction = resolveShortcutItemAction(session, shortcutKey);
            if (itemAction == null) {
                log.trace("No item action resolvable for shortcut '{}' on entity {}", shortcutKey, entityId);
                return;
            }
            if (!SHORTCUT_INTERACT_ACTION.equals(itemAction)) { // in case of interact shortcut do the same as regular interaction, skip item action routing
                var handler = actions.get(itemAction);
                if (handler == null) {
                    log.trace("No handler for item action '{}' on entity {}", itemAction, entityId);
                    return;
                }
                WEntity entity = entityService.findByWorldIdAndEntityId(session.getWorldId(), entityId).orElse(null);
                boolean success = handler.handleEntityAction(session, entity, userAction, itemAction, shortcutKey, params);
                if (success) sendItemUseFeedback(session, shortcutKey);
                return;
            }
        }
        // Regular interaction: route via entity server metadata
        WEntity entity = entityService.findByWorldIdAndEntityId(session.getWorldId(), entityId).orElse(null);
        if (entity == null) {
            log.warn("Entity with ID {} not found in world {}", entityId, session.getWorldId());
            return;
        }

        // Dead entity → route to loot/collect only if player was an attacker
        String worldIdStr = session.getWorldId() != null ? session.getWorldId().getId() : null;
        if (worldIdStr != null && entityStateRedisService.isDead(worldIdStr, entityId)) {
            String playerId = session.getEntityId();
            if (playerId != null && entityStateRedisService.removeLooter(worldIdStr, entityId, playerId)) {
                var collectHandler = actions.get("collect");
                if (collectHandler != null) {
                    collectHandler.handleEntityAction(session, entity, userAction, "collect", null, params);
                }
            } else {
                log.debug("Player {} is not eligible for loot from dead entity {} in world {}",
                        playerId, entityId, worldIdStr);
            }
            return;
        }

        String entityAction = entity.getServer().get("action");
        if (Strings.isBlank(entityAction)) {
            log.warn("No action defined for entity {} in world {}", entityId, session.getWorldId());
            return;
        }
        var handler = actions.get(entityAction);
        if (handler == null) {
            log.warn("Unknown entity action '{}' in server metadata for entity {} ({})", entityAction, session.getWorldId(), entityId);
            return;
        }
        handler.handleEntityAction(session, entity, userAction, entityAction, null, params);
    }

    /**
     * Resolve the item's action from the shortcut key.
     * Looks up the shortcut definition from the session's player data,
     * then loads the item and returns its server action.
     */
    protected String resolveShortcutItemAction(PlayerSession session, String shortcutKey) {
        if (Strings.isBlank(shortcutKey)) return null;

        var character = session.getPlayer() != null ? session.getPlayer().character() : null;
        var playerInfo = character != null ? character.getPublicData() : null;
        if (playerInfo == null || playerInfo.getShortcuts() == null) return null;

        var shortcutDef = playerInfo.getShortcuts().get(shortcutKey);
        if (shortcutDef == null || shortcutDef.getItemId() == null) return null;
        if ("interact".equals(shortcutDef.getType())) {
            return SHORTCUT_INTERACT_ACTION;
        }
        if (!"use".equals(shortcutDef.getType())) {
            log.warn("Unsupported shortcut type '{}' for shortcut '{}' in world {}", shortcutDef.getType(), shortcutKey, session.getWorldId());
            return null;
        }

        String itemId = shortcutDef.getItemId();
        WItem item = itemService.findByItemId(session.getWorldId(), itemId).orElse(null);
        if (item == null || item.getServer() == null) return null;
        return item.getServer().get("action");
    }

    @Override
    public void onItemInteraction(PlayerSession session, String itemId, JsonNode params) {
        WItem item = itemService.findByItemId(session.getWorldId(), itemId).orElse(null);
        if (item == null) {
            log.warn("Item with ID {} not found in world {}", itemId, session.getWorldId());
            return;
        }
        String itemAction = item.getServer().get("action");
        if (Strings.isBlank(itemAction)) {
            log.warn("No action defined for item {} in world {}", itemId, session.getWorldId());
            return;
        }
        var handler = actions.get(itemAction);
        if (handler == null) {
            log.warn("Unknown item action '{}' in server metadata for item {} ({})", itemAction, session.getWorldId(), itemId);
            return;
        }
        handler.handleItemAction(session, item, itemAction, params);
    }

    @Override
    public Map<String, Object> serialize(PlayerSession session) {
        return Map.of();
    }

    @Override
    public void onSessionTick(PlayerSession session, int count) {

    }

    @Override
    public void onShortcutModified(PlayerSession session) {

    }

    @Override
    public void onWearingModified(PlayerSession session) {

    }

    @Override
    public void onBackpackModified(PlayerSession session) {

    }

    @Override
    public void onSkillsModified(PlayerSession session) {

    }

    @Override
    public void onConstitutionModified(PlayerSession session) {

    }

    @Override
    public boolean useEffect(PlayerSession session, Map<String, String> parameters, String targetEntityId) {
        return false;
    }

    /**
     * Send visual feedback to the client after a successful item use.
     * Override in subclasses to resolve the item texture and send flashImage.
     */
    protected void sendItemUseFeedback(PlayerSession session, String shortcutKey) {
        // no-op by default
    }

    @Override
    public void onSimpleInteraction(PlayerSession session, String action, JsonNode data) {

    }

    @Override
    public void onSessionAuthenticated(PlayerSession session, Map<String, Object> savedGameplayData) {

    }

}
