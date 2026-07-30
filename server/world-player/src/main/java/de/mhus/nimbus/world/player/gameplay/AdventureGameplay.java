package de.mhus.nimbus.world.player.gameplay;

import java.util.Arrays;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.generated.types.Vector3;
import de.mhus.nimbus.shared.utils.TypeUtil;
import de.mhus.nimbus.world.player.service.GameplayUtil;
import de.mhus.nimbus.world.player.gameplay.adventure.AttackAction;
import de.mhus.nimbus.world.player.gameplay.adventure.CollectAction;
import de.mhus.nimbus.world.player.gameplay.adventure.BuffAction;
import de.mhus.nimbus.world.player.gameplay.adventure.ReviveAction;
import de.mhus.nimbus.world.player.gameplay.adventure.DropItemAction;
import de.mhus.nimbus.world.player.gameplay.adventure.EffectAction;
import de.mhus.nimbus.world.player.gameplay.adventure.IncreaseExpAction;
import de.mhus.nimbus.world.player.gameplay.adventure.IncreaseSkillAction;
import de.mhus.nimbus.world.player.gameplay.adventure.RestoreConstitutionAction;
import de.mhus.nimbus.world.player.gameplay.adventure.handler.CombatHandler;
import de.mhus.nimbus.world.player.gameplay.adventure.handler.ConditionHandler;
import de.mhus.nimbus.world.player.gameplay.adventure.handler.ExplorationHandler;
import de.mhus.nimbus.world.player.gameplay.adventure.handler.InventoryHandler;
import de.mhus.nimbus.world.player.gameplay.adventure.handler.SerializationHandler;
import de.mhus.nimbus.world.player.gameplay.adventure.handler.StatsHandler;
import de.mhus.nimbus.world.player.gameplay.adventure.handler.VitalsHandler;
import de.mhus.nimbus.world.player.service.ClientService;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.shared.gameplay.ActiveEffect;
import de.mhus.nimbus.world.shared.gameplay.Skill;
import de.mhus.nimbus.world.shared.redis.EntityStatusPublisher;
import de.mhus.nimbus.world.shared.redis.VitalDeltaBroadcastMessage;
import de.mhus.nimbus.world.shared.util.HexMathUtil;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import de.mhus.nimbus.world.shared.world.WHexGridService;
import de.mhus.nimbus.generated.types.ItemBlockRef;
import de.mhus.nimbus.world.shared.world.WItem;
import de.mhus.nimbus.world.player.ws.SessionManager;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class AdventureGameplay extends BasicGameplay {

    private static final int VITALS_SEND_INTERVAL_TICKS = 2;
    private static final long DEATH_TIMEOUT_MS = 120_000; // 120 seconds death state before disconnect

    @Autowired
    @Getter
    private ClientService clientService;

    @Autowired
    @Lazy
    private SessionManager sessionManager;

    @Autowired
    @Getter
    private de.mhus.nimbus.world.shared.sector.RUserService userService;

    @Autowired
    @Getter
    private ObjectMapper objectMapper;

    @Autowired
    @Getter
    private WHexGridService hexGridService;

    @Autowired
    @Getter
    private EntityStatusPublisher entityStatusPublisher;

    @Autowired
    @Getter
    private de.mhus.nimbus.world.shared.world.WProgressService progressService;

    @Getter
    private final EffectProcessor effectProcessor = new EffectProcessor();

    // --- Handlers ---

    @Getter
    private final VitalsHandler vitalsHandler = new VitalsHandler(this);
    @Getter
    private final CombatHandler combatHandler = new CombatHandler(this);
    @Getter
    private final InventoryHandler inventoryHandler = new InventoryHandler(this);
    @Getter
    private final ConditionHandler conditionHandler = new ConditionHandler(this);
    @Getter
    private final StatsHandler statsHandler = new StatsHandler(this);
    @Getter
    private final ExplorationHandler explorationHandler = new ExplorationHandler(this);
    @Getter
    private final SerializationHandler serializationHandler = new SerializationHandler(this);

    @PostConstruct
    public void init() {
        actions.put("effect", new EffectAction(this));
        actions.put("collect", new CollectAction(this));
        actions.put("attack", new AttackAction(this));
        actions.put("restore.constitution", new RestoreConstitutionAction(this));
        actions.put("increase.exp", new IncreaseExpAction(this));
        actions.put("increase.skill", new IncreaseSkillAction(this));
        actions.put("dialog", new DialogAction(this));
        actions.put("drop.item", new DropItemAction(this));
        actions.put("buff", new BuffAction(this));
        actions.put("revive", new ReviveAction(this));
    }

    // --- Condition delegation ---

    @Override
    public boolean canUseBlock(PlayerSession session, int x, int y, int z, Map<String, String> serverInfo) {
        return conditionHandler.canUseBlock(session, x, y, z, serverInfo);
    }

    // --- Session lifecycle delegation ---

    @Override
    public void onSessionAuthenticated(PlayerSession session, Map<String, Object> savedGameplayData) {
        var data = new AdventureData();
        data.initDefaults();

        if (savedGameplayData != null && !savedGameplayData.isEmpty()) {
            serializationHandler.restoreData(data, savedGameplayData);
            log.info("Restored adventure data for session {}: health={}, hunger={}, thirst={}, stamina={}",
                    session.getSessionId(), data.getHealth(), data.getHunger(), data.getThirst(), data.getStamina());
        } else {
            log.info("No saved adventure data for session {}, using defaults", session.getSessionId());
        }

        session.setGameplayData(data);

        // Load initial caches
        inventoryHandler.refreshInventoryCache(session, data);
        statsHandler.refreshSkillsCache(session, data);
        statsHandler.refreshConstitutionCache(session, data);

        // Send initial vitals to client
        vitalsHandler.sendVitalsUpdate(session, data);
    }

    @Override
    public void onSessionTick(PlayerSession session, int count) {
        if (!(session.getGameplayData() instanceof AdventureData data)) return;

        // If player is in death state, only check the death timer
        if (data.getDeathTimestamp() > 0) {
            long elapsed = System.currentTimeMillis() - data.getDeathTimestamp();
            if (elapsed >= DEATH_TIMEOUT_MS) {
                handleDeathTimeout(session, data);
            }
            return;
        }

        long now = System.currentTimeMillis();
        double deltaSeconds = (now - data.getLastTickTimestamp()) / 1000.0;
        if (deltaSeconds <= 0 || deltaSeconds > 5.0) {
            // Clamp to avoid huge jumps (e.g., after lag)
            deltaSeconds = 1.0;
        }
        data.setLastTickTimestamp(now);

        // Collect remote vital deltas during tick processing
        List<VitalDeltaBroadcastMessage> outgoingDeltas = new ArrayList<>();
        String worldId = session.getWorldId() != null ? session.getWorldId().getId() : null;
        String sourceEntityId = session.getEntityId();

        boolean died = getEffectProcessor().processTick(data, deltaSeconds, outgoingDeltas, worldId, sourceEntityId);

        // Publish collected remote vital deltas
        if (!outgoingDeltas.isEmpty()) {
            vitalDeltaPublisher.publishDeltas(outgoingDeltas);
        }

        if (died) {
            log.info("Player {} died in session {}", session.getEntityId(), session.getSessionId());
            combatHandler.onPlayerDeath(session, data);
        }

        // Check stamina depletion -> slow speed
        vitalsHandler.checkStaminaSpeed(session, data);

        // Check hex exploration
        explorationHandler.checkHexExploration(session, data);

        // Send vitals update periodically
        if (count % VITALS_SEND_INTERVAL_TICKS == 0) {
            vitalsHandler.sendVitalsUpdate(session, data);
        }
    }

    /**
     * Handle death timeout: close WebSocket connection.
     * Session deletion happens in PlayerSessionPersistenceService.onSessionClosed()
     * which checks deathTimestamp > 0 and deletes instead of saving.
     */
    private void handleDeathTimeout(PlayerSession session, AdventureData data) {
        log.info("Death timeout reached for player {} in session {}, disconnecting",
                session.getEntityId(), session.getSessionId());

        // Close WebSocket connection — triggers removeSession → onSessionClosed → delete session
        try {
            sessionManager.removeSession(session.getWebSocketSession().getId());
            session.getWebSocketSession().close();
        } catch (Exception e) {
            log.warn("Failed to close WebSocket for dead player {}: {}", session.getEntityId(), e.getMessage());
        }
    }

    /**
     * Check if the player is currently in death state.
     */
    public boolean isPlayerDead(PlayerSession session) {
        if (session.getGameplayData() instanceof AdventureData data) {
            return data.getDeathTimestamp() > 0;
        }
        return false;
    }

    @Override
    public Map<String, Object> serialize(PlayerSession session) {
        if (!(session.getGameplayData() instanceof AdventureData data)) {
            return Map.of();
        }
        return serializationHandler.serializeData(session, data);
    }

    // --- Inventory delegation ---

    @Override
    public void onBackpackModified(PlayerSession session) {
        inventoryHandler.onBackpackModified(session);
    }

    @Override
    public void onShortcutModified(PlayerSession session) {
        inventoryHandler.onShortcutModified(session);
    }

    @Override
    public void onWearingModified(PlayerSession session) {
        inventoryHandler.onWearingModified(session);
    }

    // --- Stats delegation ---

    @Override
    public void onSkillsModified(PlayerSession session) {
        statsHandler.onSkillsModified(session);
    }

    @Override
    public void onConstitutionModified(PlayerSession session) {
        statsHandler.onConstitutionModified(session);
    }

    // --- Effect handling ---

    @Override
    public boolean useEffect(PlayerSession session, Map<String, String> parameters, String targetEntityId) {
        if (!(session.getGameplayData() instanceof AdventureData data)) return false;
        if (parameters == null) return false;

        // Extract effects from comma-separated string
        String effectsStr = parameters.get("effects");
        if (effectsStr == null || effectsStr.isBlank()) return false;

        List<String> effectDefs = Arrays.stream(effectsStr.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        if (effectDefs.isEmpty()) return false;

        String source = "consumable:" + parameters.getOrDefault("name", "unknown");

        String texture = parameters.get("texture");
        if (texture == null || texture.isBlank()) {
            texture = parameters.get("icon");
        }

        for (String def : effectDefs) {
            try {
                ActiveEffect effect = ActiveEffect.parse(def, source);
                if (targetEntityId != null) {
                    effect.setTargetEntityId(targetEntityId);
                }
                data.addEffect(effect);

                // Send timed effects to client for UI display
                if (!effect.isPermanent() && !effect.isRemote() && texture != null) {
                    long durationMs = (long) (effect.getMaxDuration() * 1000);
                    clientService.sendCommand(session, "effect",
                            List.of("add", texture, String.valueOf(durationMs)));
                }

                log.debug("Applied effect {} to {} (source: {}, target: {})",
                        def, session.getEntityId(), source, targetEntityId != null ? targetEntityId : "self");
            } catch (Exception e) {
                log.warn("Failed to parse effect definition '{}': {}", def, e.getMessage());
            }
        }

        return true;
    }

    // --- Simple interaction delegation ---

    @Override
    public void onSimpleInteraction(PlayerSession session, String action, JsonNode messageData) {
        if (!(session.getGameplayData() instanceof AdventureData data)) return;

        switch (action) {
            case "movementState" -> {
                String state = messageData.has("state") ? messageData.get("state").asText() : "WALK";
                data.setMovementState(state);
                log.debug("Player {} movement state: {}", session.getEntityId(), state);
            }
            case "fall" -> explorationHandler.handleFallDamage(session, data, messageData);
            case "minHeight" -> {
                log.info("Player {} hit minHeight in session {}", session.getEntityId(), session.getSessionId());
                combatHandler.onPlayerDeath(session, data);
            }
            default -> super.onSimpleInteraction(session, action, messageData);
        }
    }

    // --- Item interaction ---

    @Override
    public void onItemInteraction(PlayerSession session, int x, int y, int z, ItemBlockRef itemRef, String groupId, String userAction, String shortcutKey, JsonNode params) {
        if (itemRef == null || itemRef.getName() == null) {
            log.warn("Item interaction without itemRef at ({},{},{})", x, y, z);
            return;
        }

        String itemName = itemRef.getName();
        int amount = Math.max(1, itemRef.getAmount());

        boolean added = gameplayService.putIntoBackpack(session, itemName, amount);
        if (!added) {
            log.debug("Could not add item {} to backpack for player {}", itemName, session.getEntityId());
            clientService.sendNotification(session, 0, "", "Backpack full", null);
            return;
        }

        // Remove item from world
        itemPositionService.deleteItemPosition(session.getWorldId(), itemName);

        // Broadcast removal to all clients
        itemBlockUpdatePublisher.publishItemRemoved(session.getWorldId(), itemName, x, y, z);

        // Play sound at item position
        String soundValue = null;
        WItem wItem = itemService.findByItemId(session.getWorldId(), itemName).orElse(null);
        if (wItem != null && wItem.getServer() != null) {
            soundValue = wItem.getServer().get("sound_collect");
        }
        String sound = GameplayUtil.resolveSound(soundValue, GameplayUtil.SOUND_ITEM_COLLECT);
        clientService.sendCommand(session, "playSoundAtPosition",
                List.of(sound, String.valueOf(x), String.valueOf(y), String.valueOf(z)));

        // Notify player
        String title = itemRef.getTitle() != null ? itemRef.getTitle() : itemName;
        String texture = itemRef.getTexture();
        clientService.sendNotification(session, 3, "", "+ " + amount + " " + title, texture);
        log.info("Player {} collected item {} x{} at ({},{},{})", session.getEntityId(), itemName, amount, x, y, z);
    }

    // --- Shortcut resolution delegation ---

    /**
     * Resolve the itemId from a shortcut key using cached data.
     * For backpack mode (shortcutKey='backpack'), pass params containing the itemId.
     */
    public String resolveShortcutItemId(PlayerSession session, String shortcutKey) {
        return resolveShortcutItemId(session, shortcutKey, null);
    }

    public String resolveShortcutItemId(PlayerSession session, String shortcutKey, JsonNode params) {
        if ("backpack".equals(shortcutKey) && params != null && params.has("itemId")) {
            return params.get("itemId").asText();
        }
        return inventoryHandler.resolveShortcutItemId(session, shortcutKey);
    }

    @Override
    protected String resolveShortcutItemAction(PlayerSession session, String shortcutKey, JsonNode params) {
        // Backpack mode: delegate to base which resolves itemId from params
        if ("backpack".equals(shortcutKey)) {
            return super.resolveShortcutItemAction(session, shortcutKey, params);
        }
        if (!(session.getGameplayData() instanceof AdventureData)) {
            return super.resolveShortcutItemAction(session, shortcutKey, params);
        }
        return inventoryHandler.resolveShortcutItemAction(session, shortcutKey);
    }

    @Override
    protected void sendItemUseFeedback(PlayerSession session, String shortcutKey) {
        inventoryHandler.sendItemUseFeedback(session, shortcutKey);
    }

    // --- Combat delegation (public API for actions) ---

    /**
     * Handle an incoming ATTACK broadcast on the defender side.
     */
    public void handleIncomingAttack(PlayerSession session, AdventureData data, VitalDeltaBroadcastMessage msg) {
        combatHandler.handleIncomingAttack(session, data, msg);
    }

    /**
     * Handle an incoming REVIVE broadcast on the dead player side.
     * Exits death state, restores vitals, and notifies client.
     */
    public void handleIncomingRevive(PlayerSession session, AdventureData data, VitalDeltaBroadcastMessage msg) {
        if (data.getDeathTimestamp() <= 0) {
            log.debug("Revive ignored for player {} — not dead", session.getEntityId());
            return;
        }

        log.info("Player {} revived by {}", session.getEntityId(), msg.getSourceEntityId());

        // Exit death state
        data.setDeathTimestamp(0);

        // Restore vitals (partial — same as old death reset)
        vitalsHandler.resetVitalsToDefaults(data);

        // Remove all non-permanent effects
        data.getActiveEffects().removeIf(e -> !e.isPermanent());
        data.setMovementState("WALK");

        // Send vitals update and notification to client
        vitalsHandler.sendVitalsUpdate(session, data);
        clientService.sendSystemNotification(session, "Revived",
                "You have been revived by " + msg.getSourceEntityId());

        // Tell client to exit dead mode
        clientService.sendCommand(session, "revived", List.of());
    }

    /**
     * Check if an attack is allowed based on the attacker's current hex grid gameMode.
     */
    public boolean isAttackAllowed(PlayerSession session, String targetEntityId) {
        return combatHandler.isAttackAllowed(session, targetEntityId);
    }

    /**
     * Apply constitution wear after attack or defense.
     */
    public void applyConstitutionWear(PlayerSession session, AdventureData data,
                                       String category, double itemWear, Skill careSkill) {
        combatHandler.applyConstitutionWear(session, data, category, itemWear, careSkill);
    }

    /**
     * Add +1 skill experience for the player in the given session.
     */
    public void addSkillExperienceForSession(PlayerSession session) {
        statsHandler.addSkillExperienceForSession(session);
    }

    /**
     * Get the wear value from an item's server properties.
     */
    public double getItemWear(WItem item, double defaultWear) {
        return combatHandler.getItemWear(item, defaultWear);
    }

    // --- GameMode resolution ---

    /**
     * Resolve the gameMode for the player's current hex grid position.
     * Uses a cache on PlayerSession to avoid DB queries when the hex position hasn't changed.
     *
     * @param session The player session
     * @return gameMode string (e.g. "P", "E", "PE") or empty string if no hex grid / no gameMode
     */
    public String resolveGameMode(PlayerSession session) {
        Vector3 pos = session.getLastPosition();
        if (pos == null || session.getWorldId() == null) return "";

        int hexGridSize = session.getHexGridSize();
        if (hexGridSize <= 0) return "";

        int worldX = (int) pos.getX();
        int worldZ = (int) pos.getZ();
        HexVector2 hexPos = HexMathUtil.flatToHex(TypeUtil.vector2int(worldX, worldZ), hexGridSize);

        // Check cache: only query DB if hex position changed
        if (session.getCachedHexQ() != null && session.getCachedHexR() != null
                && session.getCachedHexQ() == hexPos.getQ() && session.getCachedHexR() == hexPos.getR()) {
            return session.getCachedGameMode() != null ? session.getCachedGameMode() : "";
        }

        // Update cache
        session.setCachedHexQ(hexPos.getQ());
        session.setCachedHexR(hexPos.getR());

        String worldId = session.getWorldId().getId();
        String gameMode = hexGridService.findByWorldIdAndPosition(worldId, hexPos, session.getEpoch())
                .map(WHexGrid::getParameters)
                .map(params -> params.get("gameMode"))
                .orElse("");

        session.setCachedGameMode(gameMode);

        // Also cache in AdventureData for easy access
        if (session.getGameplayData() instanceof AdventureData data) {
            data.setCachedGameMode(gameMode);
        }

        return gameMode;
    }
}
