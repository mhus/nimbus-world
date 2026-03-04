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
    public void onBlockInteraction(PlayerSession session, int x, int y, int z, String blockId, String groupId, String userAction, JsonNode params) {
        // Check for teleportation in server metadata
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
        handler.handleBlockAction(session, x, y, z, blockId, groupId, blockAction, params, userAction, serverInfo);

    }

    @Override
    public void onPlayerInteraction(PlayerSession session, String entityId, String action, Long timestamp, JsonNode params) {
    //     session.getPlayer().character().getPublicData().getShortcuts()
    }

    @Override
    public void onEntityInteraction(PlayerSession session, String entityId, String userAction, Long timestamp, JsonNode params) {
        //     session.getPlayer().character().getPublicData().getShortcuts()
        // Publish interaction to Redis for world-life processing
        // PlayerRedisSenderService.publishEntityInteraction(session, entityId, action, timestamp, params);
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
        handler.handleEntityAction(session, entity, userAction, entityAction, params);
    }

    @Override
    public void onItemInteraction(PlayerSession session, String itemId, JsonNode params) {
        // Handle item interactions if needed
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
        // For item interactions, we can define a separate interface or reuse BlockAction with null coordinates
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
    public void onSimpleInteraction(PlayerSession session, String action, String shortcutKey) {
        log.trace("Simple interaction: action={}, shortcutKey={}, player={}", action, shortcutKey, session.getTitle());
    }

    @Override
    public void onSessionAuthenticated(PlayerSession session, Map<String, Object> savedGameplayData) {

    }

}
