package de.mhus.nimbus.world.player.gameplay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.mhus.nimbus.generated.configs.PlayerBackpack;
import de.mhus.nimbus.generated.configs.WEARABLE_SLOT;
import de.mhus.nimbus.generated.types.PlayerInfo;
import de.mhus.nimbus.generated.types.ShortcutDefinition;
import de.mhus.nimbus.shared.types.PlayerId;
import de.mhus.nimbus.world.player.gameplay.adventure.AttackAction;
import de.mhus.nimbus.world.player.gameplay.adventure.CollectAction;
import de.mhus.nimbus.world.player.gameplay.adventure.EffectAction;
import de.mhus.nimbus.world.player.service.ClientService;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.shared.gameplay.CombatResolver;
import de.mhus.nimbus.world.shared.redis.VitalDeltaBroadcastMessage;
import de.mhus.nimbus.world.shared.redis.VitalDeltaPublisher;
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

    @Autowired
    private ClientService clientService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Getter
    private VitalDeltaPublisher vitalDeltaPublisher;

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

    @SuppressWarnings("unchecked")
    @Override
    public boolean useEffect(PlayerSession session, Map<String, Object> parameters, String targetEntityId) {
        if (!(session.getGameplayData() instanceof AdventureData data)) return false;
        if (parameters == null) return false;

        // Extract effects list from parameters
        Object effectsObj = parameters.get("effects");
        if (effectsObj == null) return false;

        List<String> effectDefs;
        if (effectsObj instanceof List<?> list) {
            effectDefs = list.stream()
                    .filter(e -> e instanceof String)
                    .map(e -> (String) e)
                    .toList();
        } else if (effectsObj instanceof String s) {
            effectDefs = List.of(s);
        } else {
            return false;
        }

        if (effectDefs.isEmpty()) return false;

        String source = "consumable:" + parameters.getOrDefault("name", "unknown");

        for (String def : effectDefs) {
            try {
                ActiveEffect effect = ActiveEffect.parse(def, source);
                if (targetEntityId != null) {
                    effect.setTargetEntityId(targetEntityId);
                }
                data.addEffect(effect);
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
        } catch (Exception e) {
            log.error("Failed to send vitals update to session {}: {}", session.getSessionId(), e.getMessage());
        }
    }

    @Override
    public void onSimpleInteraction(PlayerSession session, String action, String shortcutKey) {
        if (!(session.getGameplayData() instanceof AdventureData data)) return;

        switch (action) {
            case "underwater" -> {
                data.setUnderwater(true);
                log.debug("Player {} is now underwater", session.getEntityId());
            }
            case "abovewater" -> {
                data.setUnderwater(false);
                log.debug("Player {} surfaced, air regenerating", session.getEntityId());
            }
            default -> super.onSimpleInteraction(session, action, shortcutKey);
        }
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
            default -> null; // 'none', 'cmd', 'interact' → no item
        };
    }

    private String getWearingItemId(AdventureData data, WEARABLE_SLOT slot) {
        var backpack = data.getCachedBackpack();
        if (backpack == null || backpack.getWearingItemIds() == null) return null;
        return backpack.getWearingItemIds().get(slot);
    }

    @Override
    protected String resolveShortcutItemAction(PlayerSession session, String shortcutKey) {
        if (!(session.getGameplayData() instanceof AdventureData data)) {
            return super.resolveShortcutItemAction(session, shortcutKey);
        }

        String itemId = resolveShortcutItemId(session, shortcutKey);
        if (itemId == null) return null;

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

    /**
     * Handle an incoming ATTACK broadcast on the defender side.
     * Uses cached defence values (base + PassiveStats) to resolve damage via CombatResolver.
     *
     * @param data The defender's AdventureData
     * @param msg  The incoming attack message
     */
    public void handleIncomingAttack(AdventureData data, VitalDeltaBroadcastMessage msg) {
        // Read defender's cached defence stats (effective values from last tick recalculation)
        double defPhysDef = getEffectiveStat(data, "physical.defense");
        double defPhysEvasion = getEffectiveStat(data, "physical.evasion");
        double defMagDef = getEffectiveStat(data, "magical.defense");
        double defMagEvasion = getEffectiveStat(data, "magical.evasion");

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

        // Apply damage to health
        VitalValue health = data.getVital("health");
        if (health == null) return;

        health.setCurrent(health.getCurrent() + damage);
        health.clamp();

        // Adrenaline gain + combat timer reset for defender
        effectProcessor.addAdrenaline(data, 3.0);
        effectProcessor.onCombatAction(data);

        log.debug("Attack from {} hit {} for {} damage (health now {})",
                msg.getSourceEntityId(), msg.getTargetEntityId(),
                damage, health.getCurrent());
    }

    private double getEffectiveStat(AdventureData data, String statName) {
        CombatStat stat = data.getCombatStat(statName);
        return stat != null ? stat.getEffective() : 0;
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

            data.setUnderwater(false);

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

            data.setUnderwater(false);
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
