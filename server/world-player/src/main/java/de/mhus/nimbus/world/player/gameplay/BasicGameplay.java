package de.mhus.nimbus.world.player.gameplay;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.world.player.service.GameplayService;
import de.mhus.nimbus.world.player.service.PlayerService;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.shared.region.RCharacterService;
import de.mhus.nimbus.world.shared.world.WChunkService;
import de.mhus.nimbus.world.shared.world.WEntity;
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

    @Autowired
    protected WChunkService chunkService;
    @Autowired
    @Getter
    protected PlayerService playerService;
    @Autowired
    protected WEntityService entityService;
    @Autowired
    protected WItemService itemService;
    @Autowired
    protected RCharacterService characterService;
    @Autowired
    @Lazy
    @Getter
    protected GameplayService gameplayService;

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
            var handler = actions.get(itemAction);
            if (handler == null) {
                log.trace("No handler for item action '{}' on block at ({}, {}, {})", itemAction, x, y, z);
                return;
            }
            handler.handleBlockAction(session, x, y, z, blockId, groupId, itemAction, params, userAction, shortcutKey, Map.of());
            return;
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
        var handler = actions.get(itemAction);
        if (handler == null) {
            log.trace("No handler for item action '{}' on player {}", itemAction, entityId);
            return;
        }
        handler.handlePlayerAction(session, entityId, itemAction, shortcutKey, timestamp, params);
    }

    @Override
    public void onEntityInteraction(PlayerSession session, String entityId, String userAction, String shortcutKey, Long timestamp, JsonNode params) {
        if (!Strings.isBlank(shortcutKey)) {
            // Shortcut on entity: route via item action
            String itemAction = resolveShortcutItemAction(session, shortcutKey);
            if (itemAction == null) {
                log.trace("No item action resolvable for shortcut '{}' on entity {}", shortcutKey, entityId);
                return;
            }
            var handler = actions.get(itemAction);
            if (handler == null) {
                log.trace("No handler for item action '{}' on entity {}", itemAction, entityId);
                return;
            }
            WEntity entity = entityService.findByWorldIdAndEntityId(session.getWorldId(), entityId).orElse(null);
            handler.handleEntityAction(session, entity, userAction, itemAction, shortcutKey, params);
            return;
        }
        // Regular interaction: route via entity server metadata
        WEntity entity = entityService.findByWorldIdAndEntityId(session.getWorldId(), entityId).orElse(null);
        if (entity == null) {
            log.warn("Entity with ID {} not found in world {}", entityId, session.getWorldId());
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
    public boolean useEffect(PlayerSession session, Map<String, String> parameters, String targetEntityId) {
        return false;
    }

    @Override
    public void onSimpleInteraction(PlayerSession session, String action, String shortcutKey, JsonNode data) {
        if (Strings.isBlank(shortcutKey)) {
            log.trace("Simple interaction without shortcut: action={}, player={}", action, session.getTitle());
            return;
        }
        // Shortcut without target: route via item action (e.g., self-application)
        String itemAction = resolveShortcutItemAction(session, shortcutKey);
        if (itemAction == null) {
            log.trace("No item action resolvable for simple shortcut '{}', player={}", shortcutKey, session.getTitle());
            return;
        }
        var handler = actions.get(itemAction);
        if (handler == null) {
            log.trace("No handler for item action '{}' from simple shortcut '{}'", itemAction, shortcutKey);
            return;
        }
        handler.handlePlayerAction(session, null, itemAction, shortcutKey, null, null);
    }

    @Override
    public void onSessionAuthenticated(PlayerSession session, Map<String, Object> savedGameplayData) {

    }

}
