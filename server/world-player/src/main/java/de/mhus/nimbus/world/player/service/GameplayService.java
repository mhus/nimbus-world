package de.mhus.nimbus.world.player.service;

import com.fasterxml.jackson.databind.JsonNode;
import de.mhus.nimbus.generated.types.ShortcutDefinition;
import de.mhus.nimbus.shared.types.PlayerId;
import de.mhus.nimbus.world.player.gameplay.AdventureGameplay;
import de.mhus.nimbus.world.player.gameplay.EditorGameplay;
import de.mhus.nimbus.world.player.gameplay.Gameplay;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.player.session.SessionAuthenticatedConsumer;
import de.mhus.nimbus.world.shared.region.RCharacter;
import de.mhus.nimbus.world.shared.region.RCharacterService;
import de.mhus.nimbus.world.shared.session.WPlayerSessionService;
import de.mhus.nimbus.world.shared.world.WItem;
import de.mhus.nimbus.world.shared.world.WItemPosition;
import de.mhus.nimbus.world.shared.world.WItemPositionService;
import de.mhus.nimbus.world.shared.world.WItemService;
import de.mhus.nimbus.world.shared.world.WWorld;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class GameplayService implements SessionAuthenticatedConsumer {

    private final List<Gameplay> gameplays;
    private final WPlayerSessionService playerSessionService;
    private final ClientService clientService;
    private final RCharacterService characterService;
    private final WItemService itemService;
    private final WItemPositionService itemPositionService;
    private Map<String, Gameplay> gameplayMap;

    @PostConstruct
    public void init() {
        gameplayMap = gameplays.stream().collect(Collectors.toMap(Gameplay::getName, g -> g));
        log.info("Initialized GameplayService with gameplays: {}", gameplayMap.keySet());
    }

    /**
     * Session owner 'interacts' with another player (Space-Key or A-Button on X-Box).
     * If shortcut is provided, the shortcut action (item) will be executed on the other player.
     * Otherwise ...
     *
     * @param session
     * @param entityId
     * @param userAction
     * @param shortcutKey
     * @param timestamp
     * @param params
     */
    public void onPlayerPlayerInteraction(PlayerSession session, String entityId, String userAction, String shortcutKey, Long timestamp, JsonNode params) {
        log.info("Player {} interacted with player {}: action={}, shortcut={}, timestamp={}",
                GameplayUtil.toString(session.getPlayer()), entityId, userAction, shortcutKey, timestamp);
        if (session.getWorldId() == null) {
            return;
        }
        var gameplay = session.getGameplay();
        if (gameplay == null) {
            log.warn("No gameplay set for session {}, cannot handle player interaction", session.getPlayer());
            return;
        }
        gameplay.onPlayerInteraction(session, entityId, userAction, shortcutKey, timestamp, params);
    }

    /**
     * If the shortcut key is specified, the shortcut item action will be executed on the entity.
     * Otherwise, session owner 'interacts' with an entity (Space-Key or A-Button on X-Box).
     * This should execute the action for 'interaction', defined on the entity's parameters on the player.
     *
     * @param session
     * @param entityId
     * @param userAction
     * @param shortcutKey
     * @param timestamp
     * @param params
     */
    public void onPlayerEntityInteraction(PlayerSession session, String entityId, String userAction, String shortcutKey, Long timestamp, JsonNode params) {
        log.info("Player {} interacted with entity {}: action={}, shortcut={}, timestamp={}",
                GameplayUtil.toString(session.getPlayer()), entityId, userAction, shortcutKey, timestamp);
        if (session.getWorldId() == null) {
            return;
        }
        var gameplay = session.getGameplay();
        if (gameplay == null) {
            log.warn("No gameplay set for session {}, cannot handle entity interaction", session.getPlayer());
            return;
        }
        gameplay.onEntityInteraction(session, entityId, userAction, shortcutKey, timestamp, params);
    }

    /**
     * Session owner performs a simple interaction that is not targeting a block or entity (e.g. pressing a shortcut without target)
     * or fall, underwater, ...
     *
     * @param session
     * @param action
     * @param shortcutKey
     * @param data
     */
    public void onSimpleInteraction(PlayerSession session, String action, String shortcutKey, JsonNode data) {
        log.info("Player {} simple interaction: action={}, shortcutKey={}",
                GameplayUtil.toString(session.getPlayer()), action, shortcutKey);
        if (session.getWorldId() == null) {
            return;
        }
        var gameplay = session.getGameplay();
        if (gameplay == null) {
            log.warn("No gameplay set for session {}, cannot handle simple interaction", session.getPlayer());
            return;
        }
        if (shortcutKey != null && !shortcutKey.isEmpty()) {
            gameplay.onItemInteraction(session, shortcutKey, data);
        } else {
            gameplay.onSimpleInteraction(session, action, data);
        }
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
    public void onPlayerBlockInteraction(PlayerSession session, int x, int y, int z, String blockId, String groupId, String userAction, String shortcutKey, JsonNode params) {
        log.info("Player {} interacted with block at ({}, {}, {}): action={}, shortcut={}, blockId={}, groupId={}",
                GameplayUtil.toString(session.getPlayer()), x, y, z, userAction, shortcutKey, blockId, groupId);

        if (session.getWorldId() == null) {
            return;
        }
        var gameplay = session.getGameplay();
        if (gameplay == null) {
            log.warn("No gameplay set for session {}, cannot handle block interaction", session.getPlayer());
            return;
        }
        gameplay.onBlockInteraction(session, x, y, z, blockId, groupId, userAction, shortcutKey, params);
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
     * @param itemId
     * @param groupId
     * @param userAction
     * @param shortcutKey
     * @param params
     */
    public void onPlayerItemInteraction(PlayerSession session, int x, int y, int z, String itemId, String groupId, String userAction, String shortcutKey, JsonNode params) {
        log.info("Player {} interacted with item at ({}, {}, {}): action={}, shortcut={}, itemId={}, groupId={}",
                GameplayUtil.toString(session.getPlayer()), x, y, z, userAction, shortcutKey, itemId, groupId);

        if (session.getWorldId() == null) {
            return;
        }
        var gameplay = session.getGameplay();
        if (gameplay == null) {
            log.warn("No gameplay set for session {}, cannot handle block interaction", session.getPlayer());
            return;
        }
        // check item pos
        Optional<WItemPosition> itemPositionOpt = itemPositionService.getItemAt(session.getWorldId(), x, y , z);
        if (itemPositionOpt.isEmpty() || !itemId.equals(itemPositionOpt.get().getItemId())) {
            log.warn("No item {} found at position ({}, {}, {}) for world {}, cannot handle item interaction",
                    itemId, x, y, z, session.getWorldId());
            return;
        }
        gameplay.onItemInteraction(session, x, y, z, itemPositionOpt.get().getPublicData(), groupId, userAction, shortcutKey, params);
    }

    /**
     * Initialize gameplay for a player session based on world settings.
     * If session is in edit mode, CreativeGameplay is used regardless of world settings.
     * If world has no gameplay set or gameplay not found, defaults to AdventureGameplay.
     *
     * This wil be done before session is registered and ready. Do not send any messages to the client from here,
     * just set up the session state. Gameplay implementations will handle sending initial data to the client when
     * the session is ready.
     *
     * @param session PlayerSession to initialize
     * @param world   WWorld whose gameplay settings to use
     */
    public void initSessionGameplay(PlayerSession session, WWorld world) {
        String gameplay = null;
        if (session.isEditActor()) {
            gameplay = EditorGameplay.class.getSimpleName();
        } else {
            gameplay = world.getGameplay();
        }
        if (gameplay == null || !gameplayMap.containsKey(gameplay)) {
            log.warn("Gameplay {} not found for world {}, defaulting to AdventureGameplay",
                    gameplay, world.getId());
            gameplay = AdventureGameplay.class.getSimpleName();
        }
        session.setGameplay(gameplayMap.get(gameplay));
    }

    /**
     * Called when a player's shortcuts have been modified via the shortcut panel.
     * Implementation will be added later.
     */
    public void onShortcutModified(PlayerSession session) {
        log.info("Shortcut modified for player {}", GameplayUtil.toString(session.getPlayer()));
        var gameplay = session.getGameplay();
        if (gameplay == null) {
            log.warn("No gameplay set for session {}, cannot handle shortcut modification", session.getPlayer());
            return;
        }
        clientService.sendCommand(session, "ShortcutModified", List.of());
        gameplay.onShortcutModified(session);
    }

    /**
     * Called when a player's wearing items have been modified via the wearing panel.
     * Implementation will be added later.
     */
    public void onWearingModified(PlayerSession session) {
        log.info("Wearing modified for player {}", GameplayUtil.toString(session.getPlayer()));
        var gameplay = session.getGameplay();
        if (gameplay == null) {
            log.warn("No gameplay set for session {}, cannot handle wearing modification", session.getPlayer());
            return;
        }
        clientService.sendCommand(session, "ShortcutModified", List.of());
        gameplay.onWearingModified(session);
    }

    /**
     * Called when a player's skills have been modified.
     */
    public void onSkillsModified(PlayerSession session) {
        log.info("Skills modified for player {}", GameplayUtil.toString(session.getPlayer()));
        var gameplay = session.getGameplay();
        if (gameplay == null) {
            log.warn("No gameplay set for session {}, cannot handle skills modification", session.getPlayer());
            return;
        }
        gameplay.onSkillsModified(session);
    }

    /**
     * Called when a player's constitution has been modified (weapon/armor/magic wear).
     */
    public void onConstitutionModified(PlayerSession session) {
        log.info("Constitution modified for player {}", GameplayUtil.toString(session.getPlayer()));
        var gameplay = session.getGameplay();
        if (gameplay == null) {
            log.warn("No gameplay set for session {}, cannot handle constitution modification", session.getPlayer());
            return;
        }
        clientService.sendCommand(session, "ConstitutionModified", List.of());
        gameplay.onConstitutionModified(session);
    }

    /**
     * Use an item's effects on the player or a target entity.
     * Checks if the item has effects, if there's sufficient quantity, applies effects via gameplay,
     * then consumes the item (quantity 1) via reduceItem.
     *
     * @param session        The player session
     * @param itemId         The item to use
     * @param targetEntityId Target entity ID, or null for self-application
     * @return true if the item was used successfully
     */
    @SuppressWarnings("unchecked")
    public boolean useItemEffect(PlayerSession session, String itemId, String targetEntityId) {
        if (session.getWorldId() == null) return false;

        var gameplay = session.getGameplay();
        if (gameplay == null) {
            log.warn("No gameplay set for session {}, cannot use item effect", session.getSessionId());
            return false;
        }

        // Load item
        var wItemOpt = itemService.findByItemId(session.getWorldId(), itemId);
        if (wItemOpt.isEmpty()) {
            log.warn("Item {} not found in world {}", itemId, session.getWorldId());
            return false;
        }
        WItem wItem = wItemOpt.get();

        // Check if item has at least one effect (in server-side hidden parameters)
        String effects = wItem.getServer() != null ? wItem.getServer().get("effects") : null;
        if (effects == null || effects.isBlank()) {
            log.debug("Item {} has no effects defined", itemId);
            return false;
        }

        // Check if player has at least 1 of this item in backpack
        var character = session.getPlayer() != null ? session.getPlayer().character() : null;
        var backpack = character != null ? character.getBackpack() : null;
        if (backpack == null || backpack.getItemIds() == null) return false;

        Integer currentCount = backpack.getItemIds().get(itemId);
        if (currentCount == null || currentCount < 1) {
            log.debug("Player {} has no item {} in backpack", session.getEntityId(), itemId);
            return false;
        }

        // Apply effects via gameplay
        boolean applied = gameplay.useEffect(session, wItem.getServer(), targetEntityId);
        if (!applied) {
            log.debug("Gameplay rejected effect application for item {}", itemId);
            return false;
        }

        // Consume the item (quantity 1)
        reduceItem(session, itemId, 1);

        log.info("Player {} used item {} effect on {}",
                session.getEntityId(), itemId, targetEntityId != null ? targetEntityId : "self");

        return true;
    }

    /**
     * Reduce an item's quantity in the player's backpack.
     * If the item has server parameter 'immortal' = true, nothing is consumed and true is returned.
     * If insufficient quantity is available, nothing is consumed and false is returned (no events).
     * On successful reduction (or removal at 0), fires onBackpackModified and optionally onShortcutModified.
     *
     * @param session  The player session
     * @param itemId   The item to consume
     * @param quantity The amount to reduce
     * @return true if the item was consumed (or immortal), false if insufficient quantity
     */
    public boolean reduceItem(PlayerSession session, String itemId, int quantity) {
        if (session.getWorldId() == null || quantity <= 0) return false;

        String entityId = session.getEntityId();
        if (entityId == null) return false;

        PlayerId playerId = PlayerId.of(entityId).orElse(null);
        if (playerId == null) return false;

        String regionId = session.getWorldId().getRegionId();

        // Check if item is immortal
        var wItemOpt = itemService.findByItemId(session.getWorldId(), itemId);
        if (wItemOpt.isPresent()) {
            WItem wItem = wItemOpt.get();
            if (wItem.getServer() != null && "true".equals(wItem.getServer().get("immortal"))) {
                log.debug("Item {} is immortal, not consuming for player {}", itemId, entityId);
                return true;
            }
        }

        // Load character to get ID and check current state
        var characterOpt = characterService.getCharacter(
                playerId.getUserId(), regionId, playerId.getCharacterId());
        if (characterOpt.isEmpty()) return false;

        RCharacter character = characterOpt.get();
        var backpack = character.getBackpack();
        if (backpack == null || backpack.getItemIds() == null) return false;

        Integer currentCount = backpack.getItemIds().get(itemId);
        if (currentCount == null || currentCount < quantity) {
            return false;
        }

        // Atomic MongoDB update via $inc (validates sufficient quantity, cleans up at 0)
        boolean updated = characterService.removeBackpackItem(character.getId(), itemId, quantity);
        if (!updated) {
            log.warn("Atomic backpack reduce failed for player {} item {} x{}", entityId, itemId, quantity);
            return false;
        }

        boolean removed = currentCount - quantity <= 0;

        // If item fully removed, clean up shortcuts referencing this item
        boolean shortcutsChanged = false;
        if (removed && character.getPublicData() != null && character.getPublicData().getShortcuts() != null) {
            var shortcuts = character.getPublicData().getShortcuts();
            List<String> keysToRemove = new ArrayList<>();
            for (var entry : shortcuts.entrySet()) {
                ShortcutDefinition def = entry.getValue();
                if (def != null && itemId.equals(def.getItemId())) {
                    keysToRemove.add(entry.getKey());
                }
            }
            if (!keysToRemove.isEmpty()) {
                for (String key : keysToRemove) {
                    shortcuts.remove(key);
                }
                characterService.updateCharater(character);
                shortcutsChanged = true;
            }
        }

        log.info("Reduced item {} by {} for player {} (remaining: {}, removed: {})",
                itemId, quantity, entityId, removed ? 0 : currentCount - quantity, removed);

        // Reload character from DB and refresh caches
        onBackpackModified(session);
        if (shortcutsChanged) {
            onShortcutModified(session);
        }

        return true;
    }

    /**
     * Put an item into the player's backpack.
     * Creates backpack/itemIds map if they don't exist yet.
     * Returns false if the backpack has too many distinct items or the total amount would exceed the limit.
     *
     * @param session  The player session
     * @param itemId   The item to add
     * @param quantity The amount to add
     * @return true if the item was added successfully, false if backpack is full or amount exceeds limit
     */
    public boolean putIntoBackpack(PlayerSession session, String itemId, int quantity) {
        if (session.getWorldId() == null || quantity <= 0) return false;

        String entityId = session.getEntityId();
        if (entityId == null) return false;

        PlayerId playerId = PlayerId.of(entityId).orElse(null);
        if (playerId == null) return false;

        String regionId = session.getWorldId().getRegionId();

        var characterOpt = characterService.getCharacter(
                playerId.getUserId(), regionId, playerId.getCharacterId());
        if (characterOpt.isEmpty()) return false;

        RCharacter character = characterOpt.get();
        var backpack = character.getBackpack();

        // Check capacity
        int maxItems = session.getGameplay() != null
                ? session.getGameplay().getMaxBackpackItems(session)
                : 1000;

        int currentCount = 0;
        if (backpack != null && backpack.getItemIds() != null) {
            currentCount = backpack.getItemIds().getOrDefault(itemId, 0);
        }

        // Check total amount for this item
        if (currentCount + quantity > maxItems) {
            log.debug("Backpack amount limit reached for player {} item {} ({} + {} > {})",
                    entityId, itemId, currentCount, quantity, maxItems);
            return false;
        }

        // Check distinct item count (only for new items)
        if (currentCount == 0 && backpack != null && backpack.getItemIds() != null
                && backpack.getItemIds().size() >= maxItems) {
            log.debug("Backpack item slot limit reached for player {} ({} >= {})",
                    entityId, backpack.getItemIds().size(), maxItems);
            return false;
        }

        // Atomic MongoDB update via $inc
        boolean updated = characterService.addBackpackItem(character.getId(), itemId, quantity);
        if (!updated) {
            log.warn("Atomic backpack update failed for player {} item {} x{}", entityId, itemId, quantity);
            return false;
        }

        log.info("Put item {} x{} into backpack for player {} (total: {})",
                itemId, quantity, entityId, currentCount + quantity);

        // Reload character from DB and refresh caches
        onBackpackModified(session);

        return true;
    }

    /**
     * Find all items in the player's backpack that have a specific server "effect" value.
     * Uses cached backpack and item data from AdventureData for fast lookup.
     *
     * @param session The player session
     * @param effect  The effect value to search for (e.g. "1up")
     * @return List of matching WItems (never null, may be empty)
     */
    public List<WItem> findItemsByEffect(PlayerSession session, String effect) {
        if (effect == null || !(session.getGameplayData() instanceof de.mhus.nimbus.world.player.gameplay.AdventureData data)) {
            return List.of();
        }

        var backpack = data.getCachedBackpack();
        var items = data.getCachedItems();
        if (backpack == null || backpack.getItemIds() == null || items == null) return List.of();

        List<WItem> result = new ArrayList<>();
        for (var entry : backpack.getItemIds().entrySet()) {
            Integer count = entry.getValue();
            if (count == null || count <= 0) continue;

            WItem item = items.get(entry.getKey());
            if (item == null || item.getServer() == null) continue;

            if (effect.equals(item.getServer().get("effect"))) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * Called when a player's backpack has been modified via the chest panel.
     */
    public void onBackpackModified(PlayerSession session) {
        log.info("Backpack modified for player {}", GameplayUtil.toString(session.getPlayer()));
        var gameplay = session.getGameplay();
        if (gameplay == null) {
            log.warn("No gameplay set for session {}, cannot handle backpack modification", session.getPlayer());
            return;
        }
        gameplay.onBackpackModified(session);
    }

    /**
     * Handle session authenticated event. This is called after the player has successfully authenticated and the session is ready.
     * Loads saved gameplay data from WPlayerSession (MongoDB) and passes it to the gameplay implementation for restoration.
     *
     * @param session The session that was authenticated
     */
    @Override
    public void onSessionAuthenticated(PlayerSession session) {
        var gameplay = session.getGameplay();
        if (gameplay == null) {
            log.warn("No gameplay set for session {}, cannot handle session authenticated", session.getPlayer());
            return;
        }

        // Load saved gameplay data from WPlayerSession
        Map<String, Object> savedGameplayData = null;
        try {
            String worldId = session.getWorldId() != null ? session.getWorldId().getId() : null;
            String playerId = session.getEntityId();
            if (worldId != null && playerId != null && !playerId.isBlank()) {
                var playerSessionOpt = playerSessionService.loadSession(worldId, playerId);
                if (playerSessionOpt.isPresent()) {
                    savedGameplayData = playerSessionOpt.get().getGameplayData();
                    log.info("Loaded saved gameplay data for session {}: worldId={}, playerId={}, hasData={}",
                            session.getSessionId(), worldId, playerId, savedGameplayData != null && !savedGameplayData.isEmpty());
                } else {
                    log.info("No saved player session found for worldId={}, playerId={}", worldId, playerId);
                }
            } else {
                log.debug("Cannot load saved gameplay data: worldId={}, playerId={}", worldId, playerId);
            }
        } catch (Exception e) {
            log.error("Failed to load saved gameplay data for session {}: {}", session.getSessionId(), e.getMessage(), e);
        }

        gameplay.onSessionAuthenticated(session, savedGameplayData);
    }

}
