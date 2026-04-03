package de.mhus.nimbus.world.player.gameplay;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.generated.types.ItemBlockRef;
import de.mhus.nimbus.world.shared.gameplay.CombatConstants;
import de.mhus.nimbus.world.player.service.GameplayService;
import de.mhus.nimbus.world.player.service.OccupationService;
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
import de.mhus.nimbus.world.shared.world.WDocumentService;
import de.mhus.nimbus.world.shared.world.WProgressService;
import de.mhus.nimbus.world.shared.world.LogicConditionService;
import de.mhus.nimbus.world.shared.world.WAnythingService;
import de.mhus.nimbus.world.shared.world.WChestService;
import de.mhus.nimbus.world.shared.util.I18nUtil;
import de.mhus.nimbus.world.shared.world.WLeaseService;
import de.mhus.nimbus.world.shared.world.WWorldService;
import de.mhus.nimbus.world.player.service.ClientService;
import de.mhus.nimbus.world.player.ws.BlockStatusSenderService;
import de.mhus.nimbus.world.shared.session.SessionCommandService;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class BasicGameplay implements Gameplay {

    public static final String SHORTCUT_INTERACT_ACTION = "__shortcut_interact__";
    public static final String FIST_ITEM_ID = CombatConstants.FIST_ITEM_ID;
    public static final String BLOCK_ITEM_ID = CombatConstants.BLOCK_ITEM_ID;

    @Autowired
    @Getter
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
    @Autowired
    @Getter
    protected WWorldService worldService;
    @Autowired
    @Getter
    protected ClientService basicClientService;
    @Autowired
    @Getter
    protected WDocumentService documentService;
    @Autowired
    @Getter
    protected WProgressService progressService;
    @Autowired
    @Getter
    protected WAnythingService anythingService;
    @Autowired
    @Getter
    protected WChestService chestService;
    @Autowired
    @Getter
    protected WLeaseService leaseService;
    @Autowired
    @Getter
    protected BlockStatusSenderService blockStatusSenderService;
    @Autowired
    @Getter
    protected de.mhus.nimbus.world.shared.world.WItemPositionService itemPositionService;
    @Autowired
    @Getter
    protected de.mhus.nimbus.world.shared.redis.ItemBlockUpdatePublisher itemBlockUpdatePublisher;
    @Autowired
    protected SessionCommandService sessionCommandService;
    @Autowired
    @Getter
    protected LogicConditionService logicConditionService;
    @Autowired
    @Getter
    protected OccupationService occupationService;
    @Autowired
    @Getter
    protected de.mhus.nimbus.world.shared.world.WTraderService traderService;
    @Autowired
    protected de.mhus.nimbus.world.shared.client.WorldClientService worldClientService;
    @Autowired
    @Getter
    protected de.mhus.nimbus.world.player.service.PlayerRedisSenderService playerRedisSenderService;
    @Autowired
    protected de.mhus.nimbus.world.shared.util.ForbiddenWordFilter forbiddenWordFilter;

    protected Map<String, GameplayAction> actions = new HashMap<>();

    /**
     * Check if a player can use/interact with a block based on its serverInfo conditions.
     * Override in subclasses (e.g. AdventureGameplay) to add condition checks.
     */
    public boolean canUseBlock(PlayerSession session, int x, int y, int z, Map<String, String> serverInfo) {
        return true;
    }

    /**
     * Check the "condition" parameter in serverInfo against Logic Machine state.
     * If no condition is set, returns true (action allowed).
     * If condition evaluates to false, the action is blocked.
     *
     * @param session    player session (provides worldId)
     * @param serverInfo block metadata containing optional "condition" SpEL expression
     * @return true if action is allowed
     */
    /**
     * Fire a LogicEvent based on the "logic" parameter in serverInfo.
     * Supports variable placeholders that get replaced before sending:
     *   {status}  - raw status string (e.g. "open", "closed")
     *   {open}    - "true" if status is "open", "false" otherwise
     *   {closed}  - "true" if status is "closed", "false" otherwise
     *
     * Example serverInfo entries:
     *   logic=state.pkg.key1 = true                     (simple assignment)
     *   logic=state.pkg.doorOpen = {open}              (replaced with true/false)
     *   logic=state.pkg.doorState = '{status}'         (replaced with 'open' or 'closed')
     *
     * @param session    player session
     * @param serverInfo block metadata containing optional "logic" SpEL expression(s)
     * @param variables  action result variables for placeholder replacement (may be empty)
     */
    public void fireLogicEffect(PlayerSession session, Map<String, String> serverInfo, Map<String, String> variables) {
        if (serverInfo == null) return;
        String logic = serverInfo.get("logic");
        if (logic == null || logic.isBlank()) return;

        // Replace placeholders
        String resolved = logic;
        for (var entry : variables.entrySet()) {
            resolved = resolved.replace("{" + entry.getKey() + "}", entry.getValue());
        }

        String worldId = session.getWorldId().getId();
        String source = "session:" + session.getSessionId();

        // Split multiple expressions by semicolon
        List<String> eval = java.util.Arrays.stream(resolved.split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        if (!eval.isEmpty()) {
            worldClientService.sendLogicEvent(worldId, eval, source);
            log.debug("Fired logic effect: worldId={}, eval={}, source={}", worldId, eval, source);
        }
    }

    /**
     * Check if the "logic" parameter contains placeholders that require
     * action-specific variable replacement (e.g. {status}, {open}).
     * If true, the action itself must call fireLogicEffect with variables.
     */
    protected boolean logicHasPlaceholders(Map<String, String> serverInfo) {
        if (serverInfo == null) return false;
        String logic = serverInfo.get("logic");
        return logic != null && logic.contains("{");
    }

    protected boolean checkLogicCondition(PlayerSession session, Map<String, String> serverInfo) {
        if (serverInfo == null) return true;
        String condition = serverInfo.get("condition");
        if (condition == null || condition.isBlank()) return true;

        String worldId = session.getWorldId().getId();
        boolean result = logicConditionService.checkCondition(worldId, condition);
        if (!result) {
            log.debug("Logic condition blocked action: worldId={}, condition={}", worldId, condition);
        }
        return result;
    }

    public BasicGameplay() {
            actions.put("teleport", new TeleportationAction(this));
            actions.put("show.time", new ShowTimeAction(this));
            actions.put("show.coordinates", new ShowCoordinatesAction(this));
            actions.put("show.document", new ShowDocumentAction(this));
            actions.put("open.chest", new OpenChestAction(this));
            actions.put("door", new DoorAction(this));
        actions.put("window", new WindowAction(this));
        actions.put("toggle", new ToggleAction(this));
        actions.put("crafting", new CraftingAction(this));
        actions.put("open.trade", new OpenTradeAction(this));
    }

    @PostConstruct
    protected void initActions() {
        actions.put("occupy", new OccupyAction(this, occupationService));
    }

    @Override
    public void onBlockInteraction(PlayerSession session, int x, int y, int z, String blockId, String groupId, String userAction, String shortcutKey, JsonNode params) {
        var worldId = session.getWorldId();

        // Always load block serverInfo
        Map<String, String> serverInfo = chunkService.getServerInfo(worldId, x, y, z, session.getEpoch());
        if (serverInfo == null) {
            serverInfo = Map.of();
        }

        if (!Strings.isBlank(shortcutKey)) {
            // Check if block forces its own action (overrides item action)
            String blockAction = serverInfo.get("action");
            boolean forceAction = "true".equals(serverInfo.get("forceAction"));

            if (forceAction && blockAction != null && !blockAction.isBlank()) {
                // Block forces interaction: use block's action with shortcut context
                if (!checkLogicCondition(session, serverInfo)) return;
                var handler = actions.get(blockAction);
                if (handler == null) {
                    log.warn("Unknown forced block action '{}' at {} ({}, {}, {})", blockAction, worldId, x, y, z);
                    return;
                }
                boolean success = handler.handleBlockAction(session, x, y, z, blockId, groupId, blockAction, params, userAction, shortcutKey, serverInfo);
                if (success) {
                    sendItemUseFeedback(session, shortcutKey);
                    if (!logicHasPlaceholders(serverInfo)) fireLogicEffect(session, serverInfo, Map.of());
                }
                return;
            }

            // Shortcut on block: route via item action
            String itemAction = resolveShortcutItemAction(session, shortcutKey, params);
            if (itemAction == null) {
                log.trace("No item action resolvable for shortcut '{}' on block at ({}, {}, {})", shortcutKey, x, y, z);
                return;
            }
            if (!SHORTCUT_INTERACT_ACTION.equals(itemAction)) { // in case of interact shortcut do the same as regular interaction, skip item action routing
                if (!checkLogicCondition(session, serverInfo)) return;
                var handler = actions.get(itemAction);
                if (handler == null) {
                    log.trace("No handler for item action '{}' on block at ({}, {}, {})", itemAction, x, y, z);
                    return;
                }
                boolean success = handler.handleBlockAction(session, x, y, z, blockId, groupId, itemAction, params, userAction, shortcutKey, serverInfo);
                if (success) {
                    sendItemUseFeedback(session, shortcutKey);
                    if (!logicHasPlaceholders(serverInfo)) fireLogicEffect(session, serverInfo, Map.of());
                }
                return;
            }
        }
        // Regular interaction: route via block server metadata
        if (serverInfo.isEmpty()) {
            log.trace("No server metadata for block at {} ({}, {}, {})", worldId, x, y, z);
            return;
        }
        String blockAction = serverInfo.get("action");
        if (blockAction == null || blockAction.isBlank()) {
            log.warn("No action entry in server metadata for block at {} ({}, {}, {})", worldId, x, y, z);
            return;
        }
        if (!checkLogicCondition(session, serverInfo)) return;
        var handler = actions.get(blockAction);
        if (handler == null) {
            log.warn("Unknown block action '{}' in server metadata for block at {} ({}, {}, {})", blockAction, worldId, x, y, z);
            return;
        }
        boolean success = handler.handleBlockAction(session, x, y, z, blockId, groupId, blockAction, params, userAction, null, serverInfo);
        if (success) {
            fireLogicEffect(session, serverInfo, Map.of());
        }
    }

    @Override
    public void onItemInteraction(PlayerSession session, int x, int y, int z, ItemBlockRef itemRef, String groupId, String userAction, String shortcutKey, JsonNode params) {
        String itemName = itemRef != null ? itemRef.getName() : "unknown";
        log.debug("Item interaction: item={}, action={}, pos=({},{},{})", itemName, userAction, x, y, z);
        basicClientService.sendNotification(session, 3, "", "Item: " + itemName, null);
    }

    @Override
    public void onPlayerInteraction(PlayerSession session, String entityId, String userAction, String shortcutKey, Long timestamp, JsonNode params) {
        if (Strings.isBlank(shortcutKey)) {
            // Only open interact widget on explicit user actions (click, interact), not on proximity/collision
            if ("interact".equals(userAction) || "click".equals(userAction)) {
                var interactAction = new PlayerInteractAction(this);
                interactAction.handlePlayerAction(session, entityId, userAction, null, timestamp, params);
            }
            return;
        }
        String itemAction = resolveShortcutItemAction(session, shortcutKey, params);
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
            String itemAction = resolveShortcutItemAction(session, shortcutKey, params);
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
                WEntity entity = entityService.findByWorldIdAndEntityId(session.getWorldId(), entityId, session.getEpoch()).orElse(null);
                boolean success = handler.handleEntityAction(session, entity, userAction, itemAction, shortcutKey, params);
                if (success) sendItemUseFeedback(session, shortcutKey);
                return;
            }
        }
        // Regular interaction: route via entity server metadata
        WEntity entity = entityService.findByWorldIdAndEntityId(session.getWorldId(), entityId, session.getEpoch()).orElse(null);
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
        return resolveShortcutItemAction(session, shortcutKey, null);
    }

    protected String resolveShortcutItemAction(PlayerSession session, String shortcutKey, JsonNode params) {
        if (Strings.isBlank(shortcutKey)) return null;

        // Backpack mode: itemId comes from params, not from shortcut definitions
        if ("backpack".equals(shortcutKey)) {
            return resolveBackpackItemAction(session, params);
        }

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

    /**
     * Resolve item action from backpack interaction params.
     * The itemId is passed in params.itemId instead of via shortcut definition.
     */
    protected String resolveBackpackItemAction(PlayerSession session, JsonNode params) {
        if (params == null || !params.has("itemId")) return null;
        String itemId = params.get("itemId").asText();
        if (Strings.isBlank(itemId)) return null;
        WItem item = itemService.findByItemId(session.getWorldId(), itemId).orElse(null);
        if (item == null || item.getServer() == null) return null;
        return item.getServer().get("action");
    }

    @Override
    public void onItemInteraction(PlayerSession session, String itemId, JsonNode params) {
        // Backpack mode: itemId is 'backpack', real itemId is in params
        if ("backpack".equals(itemId) && params != null && params.has("itemId")) {
            itemId = params.get("itemId").asText();
        }
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

    private static final int MAX_MESSAGE_LENGTH = 140;

    @Override
    public void onSimpleInteraction(PlayerSession session, String action, JsonNode data) {
        if ("msg".equals(action)) {
            handleTeamMessage(session, data);
        } else if ("dismount".equals(action)) {
            occupationService.release(session);
        }
    }

    /**
     * Handle team message from InputPanel.
     * Normalizes text (trim, max length), checks team membership,
     * and broadcasts as notification to all team members.
     */
    private void handleTeamMessage(PlayerSession session, JsonNode data) {
        String teamId = session.getCachedTeamId();
        if (teamId == null) {
            basicClientService.sendSystemNotification(session, "Team", "You are not in a team");
            return;
        }

        String msg = data != null && data.has("msg") ? data.get("msg").asText("") : "";
        msg = msg.strip();
        if (msg.isEmpty()) return;
        if (msg.length() > MAX_MESSAGE_LENGTH) {
            msg = msg.substring(0, MAX_MESSAGE_LENGTH);
        }
        // Remove control characters
        msg = msg.replaceAll("[\\p{Cntrl}]", "");
        if (msg.isEmpty()) return;
        // Filter forbidden words
        msg = forbiddenWordFilter.filter(msg);

        // Extract character name from playerId (@userId:charName)
        String playerId = session.getEntityId();
        String charName = playerId;
        if (playerId != null) {
            int colonIdx = playerId.indexOf(':');
            if (colonIdx >= 0) charName = playerId.substring(colonIdx + 1);
        }

        String encodedMsg = I18nUtil.builder()
                .en(msg)
                .put("action", "message")
                .build();
        sessionCommandService.sendToTeam(teamId, "notification", List.of("1", charName, encodedMsg));
        log.debug("Team message from {} to team {}: {}", charName, teamId, msg);
    }

    @Override
    public void onSessionAuthenticated(PlayerSession session, Map<String, Object> savedGameplayData) {

    }

}
