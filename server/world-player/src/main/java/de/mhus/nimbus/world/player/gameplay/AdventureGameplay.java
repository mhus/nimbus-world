package de.mhus.nimbus.world.player.gameplay;

import java.util.Arrays;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.mhus.nimbus.generated.configs.PlayerBackpack;
import de.mhus.nimbus.generated.configs.WEARABLE_SLOT;
import de.mhus.nimbus.generated.types.Item;
import de.mhus.nimbus.generated.types.PlayerInfo;
import de.mhus.nimbus.generated.types.ShortcutDefinition;
import de.mhus.nimbus.shared.types.PlayerId;
import de.mhus.nimbus.world.player.gameplay.adventure.AttackAction;
import de.mhus.nimbus.world.player.gameplay.adventure.CollectAction;
import de.mhus.nimbus.world.player.gameplay.adventure.EffectAction;
import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.generated.types.Vector3;
import de.mhus.nimbus.shared.utils.TypeUtil;
import de.mhus.nimbus.world.player.service.ClientService;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.shared.gameplay.ActiveEffect;
import de.mhus.nimbus.world.shared.redis.EntityStatusPublisher;
import de.mhus.nimbus.world.shared.gameplay.CombatResolver;
import de.mhus.nimbus.world.shared.gameplay.CombatStat;
import de.mhus.nimbus.world.shared.gameplay.PassiveStats;
import de.mhus.nimbus.world.shared.gameplay.VitalValue;
import de.mhus.nimbus.world.shared.redis.VitalDeltaBroadcastMessage;
import de.mhus.nimbus.world.shared.util.HexMathUtil;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import de.mhus.nimbus.world.shared.world.WHexGridService;
import de.mhus.nimbus.world.shared.world.WItem;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class AdventureGameplay extends BasicGameplay {

    private static final int VITALS_SEND_INTERVAL_TICKS = 2;
    private static final double FALL_DAMAGE_PER_METER = 3.0;
    private static final double STAMINA_DEPLETED_SPEED = 0.1;

    @Autowired
    @Getter
    private ClientService clientService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WHexGridService hexGridService;

    @Autowired
    private EntityStatusPublisher entityStatusPublisher;

    @Getter
    private final EffectProcessor effectProcessor = new EffectProcessor();

    @PostConstruct
    public void init() {
        actions.put("effect", new EffectAction(this));
        actions.put("collect", new CollectAction(this));
        actions.put("attack", new AttackAction(this));
    }

    @Override
    public void onSessionAuthenticated(PlayerSession session, Map<String, Object> savedGameplayData) {
        var data = new AdventureData();
        data.initDefaults();

        if (savedGameplayData != null && !savedGameplayData.isEmpty()) {
            restoreData(data, savedGameplayData);
            log.info("Restored adventure data for session {}: health={}, hunger={}, thirst={}, stamina={}",
                    session.getSessionId(), data.getHealth(), data.getHunger(), data.getThirst(), data.getStamina());
        } else {
            log.info("No saved adventure data for session {}, using defaults", session.getSessionId());
        }

        session.setGameplayData(data);

        // Load initial caches
        refreshInventoryCache(session, data);
        refreshSkillsCache(session, data);
        refreshConstitutionCache(session, data);

        // Send initial vitals to client
        sendVitalsUpdate(session, data);
    }

    @Override
    public void onSessionTick(PlayerSession session, int count) {
        if (!(session.getGameplayData() instanceof AdventureData data)) return;

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

        boolean died = effectProcessor.processTick(data, deltaSeconds, outgoingDeltas, worldId, sourceEntityId);

        // Publish collected remote vital deltas
        if (!outgoingDeltas.isEmpty()) {
            vitalDeltaPublisher.publishDeltas(outgoingDeltas);
        }

        if (died) {
            log.info("Player {} died in session {}", session.getEntityId(), session.getSessionId());
            onPlayerDeath(session, data);
        }

        // Check stamina depletion → slow speed
        checkStaminaSpeed(session, data);

        // Send vitals update periodically
        if (count % VITALS_SEND_INTERVAL_TICKS == 0) {
            sendVitalsUpdate(session, data);
        }
    }

    @Override
    public Map<String, Object> serialize(PlayerSession session) {
        if (!(session.getGameplayData() instanceof AdventureData data)) {
            return Map.of();
        }

        var map = new HashMap<String, Object>();
        map.put("type", "adventure");

        // Serialize vitals
        var vitalsMap = new LinkedHashMap<String, Object>();
        for (var entry : data.getVitals().entrySet()) {
            vitalsMap.put(entry.getKey(), entry.getValue().toMap());
        }
        map.put("vitals", vitalsMap);

        // Serialize combat stats (replace dots in keys - MongoDB does not allow dots in map keys)
        var combatMap = new LinkedHashMap<String, Object>();
        for (var entry : data.getCombatStats().entrySet()) {
            combatMap.put(entry.getKey().replace('.', '_'), entry.getValue().toMap());
        }
        map.put("combatStats", combatMap);

        // Serialize active effects (only timed effects, permanent equipment effects are re-applied on load)
        var effectsList = new ArrayList<Map<String, Object>>();
        for (var effect : data.getActiveEffects()) {
            if (!effect.isPermanent()) {
                effectsList.add(effect.toMap());
            }
        }
        map.put("activeEffects", effectsList);

        map.put("combatIdleTimer", data.getCombatIdleTimer());

        return map;
    }

    @Override
    public void onBackpackModified(PlayerSession session) {
        if (session.getGameplayData() instanceof AdventureData data) {
            refreshInventoryCache(session, data);
        }
    }

    @Override
    public void onShortcutModified(PlayerSession session) {
        if (session.getGameplayData() instanceof AdventureData data) {
            refreshInventoryCache(session, data);
        }
    }

    @Override
    public void onWearingModified(PlayerSession session) {
        if (session.getGameplayData() instanceof AdventureData data) {
            refreshInventoryCache(session, data);
        }
    }

    @Override
    public void onSkillsModified(PlayerSession session) {
        if (session.getGameplayData() instanceof AdventureData data) {
            refreshSkillsCache(session, data);
        }
    }

    @Override
    public void onConstitutionModified(PlayerSession session) {
        if (session.getGameplayData() instanceof AdventureData data) {
            refreshConstitutionCache(session, data);
        }
    }

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

    /**
     * Reload character data from DB and refresh inventory cache in AdventureData.
     * Loads all items referenced by backpack, wearings, and shortcuts.
     */
    private void refreshInventoryCache(PlayerSession session, AdventureData data) {
        try {
            String entityId = session.getEntityId();
            if (entityId == null || session.getWorldId() == null) return;

            PlayerId playerId = PlayerId.of(entityId).orElse(null);
            if (playerId == null) return;

            String regionId = session.getWorldId().getRegionId();
            var freshData = playerService.getPlayer(playerId, session.getClientType(), regionId);
            if (freshData.isEmpty()) return;

            // Update session with fresh player data
            session.setPlayer(freshData.get());

            var character = freshData.get().character();
            PlayerBackpack backpack = character.getBackpack();
            PlayerInfo playerInfo = character.getPublicData();

            // Cache raw data
            data.setCachedBackpack(backpack != null ? backpack : new PlayerBackpack());
            data.setCachedShortcuts(playerInfo != null && playerInfo.getShortcuts() != null
                    ? playerInfo.getShortcuts() : Map.of());

            // Reuse already cached items (items don't change during a session)
            Map<String, WItem> existingItems = data.getCachedItems();
            Map<String, WItem> items = existingItems != null ? new HashMap<>(existingItems) : new HashMap<>();
            var worldId = session.getWorldId();

            // Backpack items
            if (backpack != null && backpack.getItemIds() != null) {
                for (String itemId : backpack.getItemIds().keySet()) {
                    if (!items.containsKey(itemId)) {
                        itemService.findByItemId(worldId, itemId).ifPresent(item -> items.put(itemId, item));
                    }
                }
            }

            // Wearing items
            if (backpack != null && backpack.getWearingItemIds() != null) {
                for (String itemId : backpack.getWearingItemIds().values()) {
                    if (itemId != null && !items.containsKey(itemId)) {
                        itemService.findByItemId(worldId, itemId).ifPresent(item -> items.put(itemId, item));
                    }
                }
            }

            // Shortcut-referenced items
            if (playerInfo != null && playerInfo.getShortcuts() != null) {
                for (ShortcutDefinition shortcut : playerInfo.getShortcuts().values()) {
                    if (shortcut != null && shortcut.getItemId() != null && !items.containsKey(shortcut.getItemId())) {
                        itemService.findByItemId(worldId, shortcut.getItemId())
                                .ifPresent(item -> items.put(shortcut.getItemId(), item));
                    }
                }
            }

            // Add synthetic fist/block items (always available)
            items.put(BasicGameplay.FIST_ITEM_ID, createSyntheticFistItem(data));
            items.put(BasicGameplay.BLOCK_ITEM_ID, createSyntheticBlockItem(data));

            data.setCachedItems(items);

            // Recalculate passive stats from wearings + skills
            recalculatePassiveStats(data);

            log.debug("Refreshed inventory cache for player {}: backpack={}, wearings={}, shortcuts={}, items={}",
                    entityId,
                    backpack != null && backpack.getItemIds() != null ? backpack.getItemIds().size() : 0,
                    backpack != null && backpack.getWearingItemIds() != null ? backpack.getWearingItemIds().size() : 0,
                    data.getCachedShortcuts().size(),
                    items.size());
        } catch (Exception e) {
            log.error("Failed to refresh inventory cache for session {}: {}",
                    session.getSessionId(), e.getMessage(), e);
        }
    }

    /**
     * Reload skills from RCharacter and cache in AdventureData.
     */
    private void refreshSkillsCache(PlayerSession session, AdventureData data) {
        try {
            String entityId = session.getEntityId();
            if (entityId == null || session.getWorldId() == null) return;

            PlayerId playerId = PlayerId.of(entityId).orElse(null);
            if (playerId == null) return;

            String regionId = session.getWorldId().getRegionId();
            var characterOpt = characterService.getCharacter(
                    playerId.getUserId(), regionId, playerId.getCharacterId());
            if (characterOpt.isEmpty()) return;

            data.setCachedSkills(new HashMap<>(characterOpt.get().getSkills()));

            // Recalculate passive stats (skills affect them)
            recalculatePassiveStats(data);

            log.debug("Refreshed skills cache for player {}: skills={}",
                    entityId, data.getCachedSkills().size());
        } catch (Exception e) {
            log.error("Failed to refresh skills cache for session {}: {}",
                    session.getSessionId(), e.getMessage(), e);
        }
    }

    /**
     * Reload constitution from RCharacter and cache in AdventureData.
     */
    private void refreshConstitutionCache(PlayerSession session, AdventureData data) {
        try {
            String entityId = session.getEntityId();
            if (entityId == null || session.getWorldId() == null) return;

            PlayerId playerId = PlayerId.of(entityId).orElse(null);
            if (playerId == null) return;

            String regionId = session.getWorldId().getRegionId();
            var characterOpt = characterService.getCharacter(
                    playerId.getUserId(), regionId, playerId.getCharacterId());
            if (characterOpt.isEmpty()) return;

            data.setCachedConstitution(new HashMap<>(characterOpt.get().getConstitution()));

            log.debug("Refreshed constitution cache for player {}: {}",
                    entityId, data.getCachedConstitution());
        } catch (Exception e) {
            log.error("Failed to refresh constitution cache for session {}: {}",
                    session.getSessionId(), e.getMessage(), e);
        }
    }

    /**
     * Recalculate passive stats from worn equipment and skills.
     * Called when inventory or skills change. Equipment effects are parsed from
     * item.server "effects" (format: "stat:value[:duration[:probability]]").
     * Only permanent effects (no duration) from wearings are considered.
     *
     * Skills apply multiplicative bonuses via {@link Skill#applyMultiplicative}:
     * - combat.melee/ranged/magic: multiply physical/magical damage
     * - combat.defense/magicDefense: multiply physical/magical defense
     * - survival.*: additive bonuses to vitals
     */
    private void recalculatePassiveStats(AdventureData data) {
        PassiveStats stats = data.getPassiveStats();
        if (stats == null) {
            stats = new PassiveStats();
            data.setPassiveStats(stats);
        }
        stats.reset();

        var skills = data.getCachedSkills();

        // 1. Collect effects from worn items (non-weapon slots provide passive stats)
        var backpack = data.getCachedBackpack();
        var items = data.getCachedItems();
        if (backpack != null && backpack.getWearingItemIds() != null && items != null) {
            for (var entry : backpack.getWearingItemIds().entrySet()) {
                String itemId = entry.getValue();
                if (itemId == null) continue;

                WItem item = items.get(itemId);
                if (item == null || item.getServer() == null) continue;

                String effectsDef = item.getServer().get("effects");
                if (effectsDef == null || effectsDef.isBlank()) continue;

                // Effects are comma-separated or a JSON array string; items store them as single strings
                // Format in server map: "physical.defense:30,physical.evasion:-0.05"
                // or as individual "effects" entries following ActiveEffect.parse format
                for (String effectStr : effectsDef.split(",")) {
                    String trimmed = effectStr.trim();
                    if (trimmed.isEmpty()) continue;
                    try {
                        ActiveEffect effect = ActiveEffect.parse(trimmed, "item:" + itemId);
                        // Only permanent effects (no duration) count as passive
                        if (effect.isPermanent() && !effect.isInstant()) {
                            stats.addEffect(effect.getStat(), effect.getValue());
                        }
                    } catch (Exception e) {
                        log.trace("Skipping unparseable wearing effect '{}' on item {}", trimmed, itemId);
                    }
                }
            }
        }

        // 2. Apply skill bonuses

        // Survival skills (additive): level directly adds to vitals
        // survival.vitality: +1.0 health.max, +0.01 health.regen per level
        int vitality = AdventureSkills.SURVIVAL_VITALITY.getValue(skills);
        stats.addEffect("health.max", vitality * 1.0);
        stats.addEffect("health.regen", vitality * 0.01);

        // survival.endurance: +0.5 stamina.max, +0.02 stamina.regen per level
        int endurance = AdventureSkills.SURVIVAL_ENDURANCE.getValue(skills);
        stats.addEffect("stamina.max", endurance * 0.5);
        stats.addEffect("stamina.regen", endurance * 0.02);

        // survival.willpower: +1.0 mana.max, +0.01 mana.regen per level
        int willpower = AdventureSkills.SURVIVAL_WILLPOWER.getValue(skills);
        stats.addEffect("mana.max", willpower * 1.0);
        stats.addEffect("mana.regen", willpower * 0.01);

        // survival.resilience: reduces hunger/thirst degen (additive regen buff)
        int resilience = AdventureSkills.SURVIVAL_RESILIENCE.getValue(skills);
        stats.addEffect("hunger.regen", resilience * 0.001);
        stats.addEffect("thirst.regen", resilience * 0.001);

        // Combat skills (multiplicative): applied as percent buffs
        // combat.melee: multiplies physical damage (start=100 = no change)
        double meleePercent = AdventureSkills.COMBAT_MELEE.getValue(skills) / 100.0 - 1.0;
        if (meleePercent != 0) stats.addEffect("physical.damagePercent", meleePercent);

        // combat.ranged: also contributes to physical accuracy
        double rangedPercent = AdventureSkills.COMBAT_RANGED.getValue(skills) / 100.0 - 1.0;
        if (rangedPercent != 0) stats.addEffect("physical.accuracy", rangedPercent * 0.1);

        // combat.magic: multiplies magical damage
        double magicPercent = AdventureSkills.COMBAT_MAGIC.getValue(skills) / 100.0 - 1.0;
        if (magicPercent != 0) stats.addEffect("magical.damagePercent", magicPercent);

        // combat.defense: multiplies physical defense
        double defensePercent = AdventureSkills.COMBAT_DEFENSE.getValue(skills) / 100.0 - 1.0;
        if (defensePercent != 0) stats.addEffect("physical.defensePercent", defensePercent);

        // combat.magicDefense: multiplies magical defense
        double mDefensePercent = AdventureSkills.COMBAT_MAGIC_DEFENSE.getValue(skills) / 100.0 - 1.0;
        if (mDefensePercent != 0) stats.addEffect("magical.defensePercent", mDefensePercent);

        log.debug("Recalculated passive stats: physDef={}, magDef={}, healthMax=+{}, manaMax=+{}",
                stats.getPhysicalDefense(), stats.getMagicalDefense(),
                stats.getHealthMax(), stats.getManaMax());
    }

    /**
     * Send vitals data to the client as a server command.
     */
    private void sendVitalsUpdate(PlayerSession session, AdventureData data) {
        try {
            ArrayNode vitalsArray = objectMapper.createArrayNode();
            for (var vital : data.getVitals().values()) {
                // Skip vitals with sendThreshold if percentage has not yet crossed the threshold
                if (vital.getSendThreshold() > 0 && vital.getPercentage() <= vital.getSendThreshold()) {
                    continue;
                }
                // Skip air when not underwater and full
                if ("air".equals(vital.getType()) && !data.isUnderwater() && vital.isFull()) {
                    continue;
                }
                ObjectNode vNode = objectMapper.createObjectNode();
                vNode.put("type", vital.getType());
                vNode.put("current", (int) Math.ceil(vital.getCurrent()));
                vNode.put("max", (int) Math.ceil(vital.getEffectiveMax()));
                vNode.put("regenRate", vital.getEffectiveRegenRate());
                vNode.put("degenRate", vital.getEffectiveRegenRate() < 0 ? -vital.getEffectiveRegenRate() : 0);
                vNode.put("color", vital.getColor());
                vNode.put("name", vital.getDisplayName());
                vNode.put("order", vital.getOrder());
                if (vital.getOptions() != null) {
                    vNode.put("options", vital.getOptions());
                }
                vitalsArray.add(vNode);
            }
            String currentVitalisData = objectMapper.writeValueAsString(vitalsArray);
            if (!currentVitalisData.equals(data.getLastVitalisData())) {
                data.setLastVitalisData(currentVitalisData);
            } else {
                // No changes in vitals, skip sending update
                return;
            }

            ObjectNode commandData = objectMapper.createObjectNode();
            commandData.put("cmd", "vitals");
            commandData.set("args", vitalsArray);
            commandData.put("oneway", true);

            clientService.sendCommand(session, "vitals", commandData);

            // Low health alert flash
            VitalValue health = data.getVital("health");
            if (health != null && health.getPercentage() > 0 && health.getPercentage() <= 0.25) {
                clientService.sendCommand(session, "flashImage",
                        List.of("n:textures/actions/health_alert.png", "500", "0.5"));
            }

            // Broadcast health status to other players via entity status update
            publishPlayerHealthStatus(session, data);
        } catch (Exception e) {
            log.error("Failed to send vitals update to session {}: {}", session.getSessionId(), e.getMessage());
        }
    }

    private void checkStaminaSpeed(PlayerSession session, AdventureData data) {
        VitalValue stamina = data.getVital("stamina");
        if (stamina == null) return;

        boolean depleted = stamina.getCurrent() <= 0;
        if (depleted && !data.isStaminaSlowSent()) {
            data.setStaminaSlowSent(true);
            clientService.sendCommand(session, "speed",
                    java.util.List.of(String.valueOf(STAMINA_DEPLETED_SPEED)));
            log.debug("Stamina depleted for {}, sending slow speed {}", session.getEntityId(), STAMINA_DEPLETED_SPEED);
        } else if (!depleted && data.isStaminaSlowSent()) {
            data.setStaminaSlowSent(false);
            clientService.sendCommand(session, "speed", java.util.List.of("0"));
            log.debug("Stamina recovered for {}, resetting speed override", session.getEntityId());
        }
    }

    private void publishPlayerHealthStatus(PlayerSession session, AdventureData data) {
        VitalValue health = data.getVital("health");
        if (health == null) return;
        String worldId = session.getWorldId() != null ? session.getWorldId().getId() : null;
        String entityId = session.getEntityId();
        if (worldId == null || entityId == null) return;

        entityStatusPublisher.publishStatusUpdate(worldId, entityId,
                Map.of("health", health.getCurrent(), "healthMax", health.getEffectiveMax()),
                session.getWebSocketSession().getId());
    }

    @Override
    public void onSimpleInteraction(PlayerSession session, String action, JsonNode messageData) {
        if (!(session.getGameplayData() instanceof AdventureData data)) return;

        switch (action) {
            case "movementState" -> {
                String state = messageData.has("state") ? messageData.get("state").asText() : "WALK";
                data.setMovementState(state);
                log.debug("Player {} movement state: {}", session.getEntityId(), state);
            }
            case "fall" -> handleFallDamage(session, data, messageData);
            default -> super.onSimpleInteraction(session, action, messageData);
        }
    }

    /**
     * Handle fall damage based on fall height and acrobatics skill.
     * Safe fall height = acrobatics skill level (start=2, min=2, max=100).
     * Damage = 10 per block exceeding safe height.
     */
    private void handleFallDamage(PlayerSession session, AdventureData data, JsonNode messageData) {
        double fallHeight = messageData != null && messageData.has("fallHeight")
                ? messageData.get("fallHeight").asDouble(0) : 0;
        if (fallHeight <= 0) return;

        int safeFallHeight = AdventureSkills.SURVIVAL_ACROBATICS.getValue(data.getCachedSkills());
        if (fallHeight <= safeFallHeight) {
            log.trace("Player {} fell {} blocks (safe: {}), no damage",
                    session.getEntityId(), fallHeight, safeFallHeight);
            return;
        }

        double excessBlocks = fallHeight - safeFallHeight;
        double damage = excessBlocks * FALL_DAMAGE_PER_METER;

        log.debug("Player {} fell {} blocks (safe: {}), taking {} fall damage",
                session.getEntityId(), fallHeight, safeFallHeight, damage);

        applyDamage(session, data, damage);
    }

    /**
     * Resolve the itemId from a shortcut key using cached data.
     * Handles shortcut types: 'use' → shortcutDef.itemId, hand types → wearing slot.
     * Returns null for 'interact', 'none', 'cmd' or if no item is found.
     */
    public String resolveShortcutItemId(PlayerSession session, String shortcutKey) {
        if (!(session.getGameplayData() instanceof AdventureData data)) return null;
        if (shortcutKey == null) return null;

        var shortcuts = data.getCachedShortcuts();
        if (shortcuts == null) return null;

        var shortcutDef = shortcuts.get(shortcutKey);
        if (shortcutDef == null) return null;

        String type = shortcutDef.getType();
        if (type == null) return null;

        return switch (type) {
            case "use" -> shortcutDef.getItemId();
            case "left_hand_1" -> getWearingItemId(data, WEARABLE_SLOT.LEFT_HAND_1);
            case "right_hand_1" -> getWearingItemId(data, WEARABLE_SLOT.RIGHT_HAND_1);
            case "left_hand_2" -> getWearingItemId(data, WEARABLE_SLOT.LEFT_HAND_2);
            case "right_hand_2" -> getWearingItemId(data, WEARABLE_SLOT.RIGHT_HAND_2);
            case "fist" -> BasicGameplay.FIST_ITEM_ID;
            case "block" -> BasicGameplay.BLOCK_ITEM_ID;
            case "interact" -> BasicGameplay.SHORTCUT_INTERACT_ACTION;
            default -> null; // 'none', 'cmd' → no item
        };
    }

    private String getWearingItemId(AdventureData data, WEARABLE_SLOT slot) {
        var backpack = data.getCachedBackpack();
        if (backpack == null || backpack.getWearingItemIds() == null) return null;
        return backpack.getWearingItemIds().get(slot);
    }

    /**
     * Create synthetic fist item with attack stats derived from character skills.
     * Base physical damage from combat.melee skill level.
     */
    private WItem createSyntheticFistItem(AdventureData data) {
        var skills = data.getCachedSkills();
        int melee = skills != null ? AdventureSkills.COMBAT_MELEE.getValue(skills) : 0;
        double physDmg = 2.0 + melee * 0.1;
        double physAcc = 0.6 + melee * 0.005;

        return WItem.builder()
                .itemId(BasicGameplay.FIST_ITEM_ID)
                .publicData(Item.builder()
                        .name(BasicGameplay.FIST_ITEM_ID)
                        .title("Fist")
                        .texture("n:textures/hands/fist.png")
                        .build())
                .server(Map.of(
                        "action", "attack",
                        "effects", "physical.damage:" + physDmg + ",physical.accuracy:" + physAcc
                ))
                .build();
    }

    /**
     * Create synthetic block item with defense stats derived from character skills.
     * Base physical defense from combat.defense skill level.
     */
    private WItem createSyntheticBlockItem(AdventureData data) {
        var skills = data.getCachedSkills();
        int defense = skills != null ? AdventureSkills.COMBAT_DEFENSE.getValue(skills) : 0;
        double physDef = 1.0 + defense * 0.1;
        double physEvasion = 0.1 + defense * 0.005;

        return WItem.builder()
                .itemId(BasicGameplay.BLOCK_ITEM_ID)
                .publicData(Item.builder()
                        .name(BasicGameplay.BLOCK_ITEM_ID)
                        .title("Block")
                        .texture("n:textures/hands/block.png")
                        .build())
                .server(Map.of(
                        "action", "block",
                        "effects", "physical.defense:" + physDef + ",physical.evasion:" + physEvasion
                ))
                .build();
    }

    @Override
    protected String resolveShortcutItemAction(PlayerSession session, String shortcutKey) {
        if (!(session.getGameplayData() instanceof AdventureData data)) {
            return super.resolveShortcutItemAction(session, shortcutKey);
        }

        String itemId = resolveShortcutItemId(session, shortcutKey);
        if (itemId == null) return null;
        if (itemId.equals(BasicGameplay.SHORTCUT_INTERACT_ACTION)) {
            return BasicGameplay.SHORTCUT_INTERACT_ACTION;
        }

        var cachedItems = data.getCachedItems();
        if (cachedItems != null) {
            WItem item = cachedItems.get(itemId);
            if (item != null && item.getServer() != null) {
                return item.getServer().get("action");
            }
        }

        // Fallback: item not in cache, load from DB
        WItem item = itemService.findByItemId(session.getWorldId(), itemId).orElse(null);
        if (item == null || item.getServer() == null) return null;
        return item.getServer().get("action");
    }

    @Override
    protected void sendItemUseFeedback(PlayerSession session, String shortcutKey) {
        if (shortcutKey == null) return;
        if (!(session.getGameplayData() instanceof AdventureData data)) return;

        String itemId = resolveShortcutItemId(session, shortcutKey);
        if (itemId == null) return;

        var cachedItems = data.getCachedItems();
        WItem item = cachedItems != null ? cachedItems.get(itemId) : null;
        if (item == null) {
            item = itemService.findByItemId(session.getWorldId(), itemId).orElse(null);
        }
        if (item == null || item.getPublicData() == null) return;

        String texture = item.getPublicData().getTexture();
        if (texture == null || texture.isBlank()) return;

        clientService.sendCommand(session, "flashImage", List.of(texture, "500", "0.5"));
    }

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
        String gameMode = hexGridService.findByWorldIdAndPosition(worldId, hexPos)
                .map(WHexGrid::getParameters)
                .map(params -> params.get("gameMode"))
                .orElse("");

        session.setCachedGameMode(gameMode);
        return gameMode;
    }

    /**
     * Check if an attack is allowed based on the attacker's current hex grid gameMode.
     *
     * @param session       The attacker's session
     * @param targetEntityId The target entity ID (@ prefix = player → PvP, else → PvE)
     * @return true if the attack is allowed
     */
    public boolean isAttackAllowed(PlayerSession session, String targetEntityId) {
        String gameMode = resolveGameMode(session);
        boolean targetIsPlayer = targetEntityId != null && targetEntityId.startsWith("@");
        return targetIsPlayer ? gameMode.contains("P") : gameMode.contains("E");
    }

    /**
     * Handle an incoming ATTACK broadcast on the defender side.
     * Uses cached defence values (base + PassiveStats) to resolve damage via CombatResolver.
     * Checks gameMode on the defender's hex grid before applying damage.
     *
     * @param session The defender's session
     * @param data    The defender's AdventureData
     * @param msg     The incoming attack message
     */
    public void handleIncomingAttack(PlayerSession session, AdventureData data, VitalDeltaBroadcastMessage msg) {
        log.debug("Incoming attack on player {} from {} [phys={}/{}, mag={}/{}]",
                msg.getTargetEntityId(), msg.getSourceEntityId(),
                msg.getPhysicalDamage(), msg.getPhysicalAccuracy(),
                msg.getMagicalDamage(), msg.getMagicalAccuracy());
        // Check gameMode on defender side
        if (!isAttackAllowed(session, msg.getSourceEntityId())) {
            log.debug("Incoming attack blocked by gameMode: {} -> {} (gameMode={})",
                    msg.getSourceEntityId(), msg.getTargetEntityId(), resolveGameMode(session));
            return;
        }

        // Read defender's cached defence stats, scaled by armor constitution and defense skills
        double armorCon = getConstitutionValue(data, "armor");
        double physDefSkill = AdventureSkills.COMBAT_DEFENSE.getValue(data.getCachedSkills()) / 100.0;
        double magDefSkill = AdventureSkills.COMBAT_MAGIC_DEFENSE.getValue(data.getCachedSkills()) / 100.0;
        double defPhysDef = getEffectiveStat(data, "physical.defense") * armorCon * physDefSkill;
        double defPhysEvasion = getEffectiveStat(data, "physical.evasion") * armorCon * physDefSkill;
        double defMagDef = getEffectiveStat(data, "magical.defense") * armorCon * magDefSkill;
        double defMagEvasion = getEffectiveStat(data, "magical.evasion") * armorCon * magDefSkill;

        // Resolve damage
        double damage = CombatResolver.resolve(
                msg.getPhysicalDamage(), msg.getPhysicalAccuracy(),
                msg.getMagicalDamage(), msg.getMagicalAccuracy(),
                msg.getCritChance(), msg.getCritMultiplier(),
                defPhysDef, defPhysEvasion,
                defMagDef, defMagEvasion);

        if (damage == 0) {
            log.debug("Attack from {} on {} missed (phyDef={}, phyEva={}, magDef={}, magEva={})",
                    msg.getSourceEntityId(), msg.getTargetEntityId(),
                    defPhysDef, defPhysEvasion, defMagDef, defMagEvasion);
            return;
        }

        // Adrenaline gain + combat timer reset for defender
        effectProcessor.addAdrenaline(data, 3.0);
        effectProcessor.onCombatAction(data);

        applyDamage(session, data, damage);

        // Armor constitution wear — only wear items matching the incoming damage type
        boolean physicalHit = msg.getPhysicalDamage() > 0;
        boolean magicalHit = msg.getMagicalDamage() > 0;
        double armorWear = calculateArmorWear(data, physicalHit, magicalHit);
        if (armorWear > 0) {
            applyConstitutionWear(session, data, "armor", armorWear, AdventureSkills.COMBAT_ARMOR_CARE);
        }
    }

    private double getEffectiveStat(AdventureData data, String statName) {
        CombatStat stat = data.getCombatStat(statName);
        return stat != null ? stat.getEffective() : 0;
    }

    private double getConstitutionValue(AdventureData data, String category) {
        var con = data.getCachedConstitution();
        if (con == null) return 1.0;
        return con.getOrDefault(category, 1.0);
    }

    /**
     * Apply constitution wear after attack or defense.
     * Calculates actual wear from item base wear and care skill, then reduces
     * the constitution value atomically in MongoDB and updates the local cache.
     *
     * @param session   Player session
     * @param data      Adventure data with cached constitution
     * @param category  Constitution category ("weapon" or "armor")
     * @param itemWear  Base wear from item server property (e.g. 0.01)
     * @param careSkill Skill that reduces wear (higher = less wear)
     */
    public void applyConstitutionWear(PlayerSession session, AdventureData data,
                                        String category, double itemWear, Skill careSkill) {
        if (itemWear <= 0) return;

        // Skill factor: skill 100 = 1.0x wear, skill 200 = 0.5x wear
        double skillFactor = careSkill.getValue(data.getCachedSkills()) / 100.0;
        if (skillFactor <= 0) skillFactor = 0.01;
        double actualWear = itemWear / skillFactor;

        // Update local cache directly
        var con = data.getCachedConstitution();
        if (con == null) {
            con = new java.util.HashMap<>();
            data.setCachedConstitution(con);
        }
        double current = con.getOrDefault(category, 1.0);
        double newValue = Math.max(0.0, current - actualWear);
        con.put(category, newValue);

        // Atomic DB update
        String entityId = session.getEntityId();
        if (entityId == null || session.getWorldId() == null) return;
        var playerId = de.mhus.nimbus.shared.types.PlayerId.of(entityId).orElse(null);
        if (playerId == null) return;
        String regionId = session.getWorldId().getRegionId();
        var characterOpt = characterService.getCharacter(
                playerId.getUserId(), regionId, playerId.getCharacterId());
        if (characterOpt.isEmpty()) return;

        characterService.reduceConstitution(characterOpt.get().getId(), category, actualWear);
    }

    private static final double DEFAULT_ARMOR_WEAR = 0.005;
    private static final java.util.Set<WEARABLE_SLOT> BODY_ARMOR_SLOTS = java.util.Set.of(
            WEARABLE_SLOT.HEAD, WEARABLE_SLOT.BODY, WEARABLE_SLOT.LEGS, WEARABLE_SLOT.FEET,
            WEARABLE_SLOT.NECK, WEARABLE_SLOT.ARMS, WEARABLE_SLOT.LEFT_RING, WEARABLE_SLOT.RIGHT_RING);
    private static final java.util.Set<WEARABLE_SLOT> HAND_SLOTS = java.util.Set.of(
            WEARABLE_SLOT.LEFT_HAND_1, WEARABLE_SLOT.RIGHT_HAND_1,
            WEARABLE_SLOT.LEFT_HAND_2, WEARABLE_SLOT.RIGHT_HAND_2);

    /**
     * Calculate average armor wear from equipped defense items matching the incoming damage type.
     * Body armor slots are always included. Hand slots are only included if the item type is "shield".
     * Items are filtered by their damageType property — only items matching the incoming damage are worn.
     */
    private double calculateArmorWear(AdventureData data, boolean physicalHit, boolean magicalHit) {
        var backpack = data.getCachedBackpack();
        if (backpack == null || backpack.getWearingItemIds() == null) return 0;
        var cachedItems = data.getCachedItems();

        double totalWear = 0;
        int count = 0;

        // Body armor slots
        for (var slot : BODY_ARMOR_SLOTS) {
            String itemId = backpack.getWearingItemIds().get(slot);
            if (itemId == null) continue;
            WItem item = cachedItems != null ? cachedItems.get(itemId) : null;
            if (!matchesDamageType(item, physicalHit, magicalHit)) continue;
            totalWear += getItemWear(item, DEFAULT_ARMOR_WEAR);
            count++;
        }

        // Hand slots — only shields
        for (var slot : HAND_SLOTS) {
            String itemId = backpack.getWearingItemIds().get(slot);
            if (itemId == null) continue;
            WItem item = cachedItems != null ? cachedItems.get(itemId) : null;
            if (!"shield".equals(getServerProp(item, "type"))) continue;
            if (!matchesDamageType(item, physicalHit, magicalHit)) continue;
            totalWear += getItemWear(item, DEFAULT_ARMOR_WEAR);
            count++;
        }

        return count > 0 ? totalWear / count : 0;
    }

    private boolean matchesDamageType(WItem item, boolean physicalHit, boolean magicalHit) {
        String damageType = getServerProp(item, "damageType");
        if (damageType == null || damageType.isBlank()) return physicalHit; // default: physical armor
        return (physicalHit && damageType.contains("physical"))
                || (magicalHit && damageType.contains("magical"));
    }

    private String getServerProp(WItem item, String key) {
        if (item == null || item.getServer() == null) return null;
        return item.getServer().get(key);
    }

    /**
     * Get the wear value from an item's server properties.
     * Returns defaultWear if no "wear" property is set.
     */
    public double getItemWear(WItem item, double defaultWear) {
        if (item == null || item.getServer() == null) return defaultWear;
        String val = item.getServer().get("wear");
        if (val == null || val.isBlank()) return defaultWear;
        try {
            return Double.parseDouble(val.trim());
        } catch (NumberFormatException e) {
            return defaultWear;
        }
    }

    /**
     * Central method to apply damage to a player's health.
     * Clamps health, sends vitals update, and triggers death if health reaches 0.
     *
     * @param session The player session
     * @param data    The player's adventure data
     * @param amount  Positive damage value (will be subtracted from health)
     */
    private void applyDamage(PlayerSession session, AdventureData data, double amount) {
        amount = Math.abs(amount);
        if (amount == 0) return;

        VitalValue health = data.getVital("health");
        if (health == null) return;

        health.setCurrent(health.getCurrent() - amount);
        health.clamp();

        sendVitalsUpdate(session, data);

        if (health.getCurrent() <= 0) {
            log.info("Player {} died (damage={})", session.getEntityId(), amount);
            onPlayerDeath(session, data);
        }
    }

    /**
     * Handle player death: check for 1up item first, otherwise normal death.
     */
    private void onPlayerDeath(PlayerSession session, AdventureData data) {
        // Check backpack for a 1up item
        var oneUpItems = gameplayService.findItemsByEffect(session, "1up");
        String oneUpItemId = oneUpItems.isEmpty() ? null : oneUpItems.getFirst().getItemId();
        if (oneUpItemId != null) {
            // Consume the 1up item
            gameplayService.reduceItem(session, oneUpItemId, 1);

            // Reset all vitals to their default values (full revive)
            resetVitalsToDefaults(data);

            // Remove all non-permanent effects
            data.getActiveEffects().removeIf(e -> !e.isPermanent());

            data.setMovementState("WALK");

            clientService.sendSystemNotification(session, "1Up", "You have been revived!");
            log.info("Player {} used 1Up item {} to revive", session.getEntityId(), oneUpItemId);
        } else {
            // Normal death: partial reset
            var health = data.getVital("health");
            if (health != null) {
                health.setCurrent(health.getEffectiveMax());
            }

            data.getActiveEffects().removeIf(e -> !e.isPermanent());

            var hunger = data.getVital("hunger");
            if (hunger != null) {
                hunger.setCurrent(hunger.getEffectiveMax() * 0.5);
            }
            var thirst = data.getVital("thirst");
            if (thirst != null) {
                thirst.setCurrent(thirst.getEffectiveMax() * 0.5);
            }

            var adrenaline = data.getVital("adrenaline");
            if (adrenaline != null) {
                adrenaline.setCurrent(0);
            }

            data.setMovementState("WALK");
            var air = data.getVital("air");
            if (air != null) {
                air.setCurrent(air.getEffectiveMax());
            }

            clientService.sendSystemNotification(session, "Death", "You have died and been revived.");
        }

        sendVitalsUpdate(session, data);
    }

    /**
     * Reset all vitals to their default current values (full revive).
     * Health, stamina, mana, air → max. Hunger, thirst → 0. Adrenaline → 0.
     */
    private void resetVitalsToDefaults(AdventureData data) {
        for (var vital : data.getVitals().values()) {
            switch (vital.getType()) {
                case "hunger", "thirst", "adrenaline" -> vital.setCurrent(0);
                default -> vital.setCurrent(vital.getEffectiveMax());
            }
            vital.clamp();
        }
    }

    // --- Deserialization ---

    @SuppressWarnings("unchecked")
    private void restoreData(AdventureData data, Map<String, Object> saved) {
        // Restore vitals
        Object vitalsObj = saved.get("vitals");
        if (vitalsObj instanceof Map<?, ?> vitalsMap) {
            for (var entry : ((Map<String, Object>) vitalsMap).entrySet()) {
                String key = entry.getKey();
                if (entry.getValue() instanceof Map<?, ?> vMap) {
                    VitalValue existing = data.getVitals().get(key);
                    VitalValue restored = VitalValue.fromMap((Map<String, Object>) vMap);
                    if (existing != null) {
                        // Preserve display config from defaults, restore gameplay values
                        existing.setBase(restored.getBase());
                        existing.setCurrent(restored.getCurrent());
                        existing.setBaseRegenRate(restored.getBaseRegenRate());
                        existing.resetBuffs();
                        existing.recalculate();
                    } else {
                        data.getVitals().put(key, restored);
                    }
                }
            }
        } else {
            // Legacy format: simple doubles
            restoreLegacyVitals(data, saved);
        }

        // Restore combat stats (keys stored with underscores, restore dots for runtime usage)
        Object combatObj = saved.get("combatStats");
        if (combatObj instanceof Map<?, ?> combatMap) {
            for (var entry : ((Map<String, Object>) combatMap).entrySet()) {
                String key = entry.getKey().replace('_', '.');
                if (entry.getValue() instanceof Map<?, ?> sMap) {
                    CombatStat existing = data.getCombatStats().get(key);
                    CombatStat restored = CombatStat.fromMap((Map<String, Object>) sMap);
                    if (existing != null) {
                        existing.setBase(restored.getBase());
                        existing.resetBuffs();
                        existing.recalculate();
                    } else {
                        data.getCombatStats().put(key, restored);
                    }
                }
            }
        }

        // Restore active effects
        Object effectsObj = saved.get("activeEffects");
        if (effectsObj instanceof List<?> effectsList) {
            for (Object eObj : effectsList) {
                if (eObj instanceof Map<?, ?> eMap) {
                    data.getActiveEffects().add(ActiveEffect.fromMap((Map<String, Object>) eMap));
                }
            }
        }

        data.setCombatIdleTimer(toDouble(saved.get("combatIdleTimer"), 0));
        data.setLastTickTimestamp(System.currentTimeMillis());
    }

    /**
     * Restore from legacy format (simple health/hunger/thirst/stamina doubles).
     */
    private void restoreLegacyVitals(AdventureData data, Map<String, Object> saved) {
        setVitalCurrent(data, "health",  toDouble(saved.get("health"),  100));
        setVitalCurrent(data, "hunger",  toDouble(saved.get("hunger"),  0));
        setVitalCurrent(data, "thirst",  toDouble(saved.get("thirst"),  0));
        setVitalCurrent(data, "stamina", toDouble(saved.get("stamina"), 100));

        setVitalBase(data, "health",  toDouble(saved.get("maxHealth"),  100));
        setVitalBase(data, "hunger",  toDouble(saved.get("maxHunger"),  100));
        setVitalBase(data, "thirst",  toDouble(saved.get("maxThirst"),  100));
        setVitalBase(data, "stamina", toDouble(saved.get("maxStamina"), 100));
    }

    private void setVitalCurrent(AdventureData data, String type, double value) {
        var vital = data.getVital(type);
        if (vital != null) vital.setCurrent(value);
    }

    private void setVitalBase(AdventureData data, String type, double value) {
        var vital = data.getVital(type);
        if (vital != null) {
            vital.setBase(value);
            vital.recalculate();
        }
    }

    private double toDouble(Object value, double defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number number) return number.doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
