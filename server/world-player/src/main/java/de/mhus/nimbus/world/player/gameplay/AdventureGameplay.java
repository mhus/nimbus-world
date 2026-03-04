package de.mhus.nimbus.world.player.gameplay;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.mhus.nimbus.generated.configs.PlayerBackpack;
import de.mhus.nimbus.generated.types.PlayerInfo;
import de.mhus.nimbus.generated.types.ShortcutDefinition;
import de.mhus.nimbus.shared.types.PlayerId;
import de.mhus.nimbus.world.player.service.ClientService;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.shared.redis.VitalDeltaBroadcastMessage;
import de.mhus.nimbus.world.shared.redis.VitalDeltaPublisher;
import de.mhus.nimbus.world.shared.world.WItem;
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
    private VitalDeltaPublisher vitalDeltaPublisher;

    private final EffectProcessor effectProcessor = new EffectProcessor();

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

            log.debug("Refreshed skills cache for player {}: skills={}",
                    entityId, data.getCachedSkills().size());
        } catch (Exception e) {
            log.error("Failed to refresh skills cache for session {}: {}",
                    session.getSessionId(), e.getMessage(), e);
        }
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
     * Handle player death: reset vitals, notify client.
     */
    private void onPlayerDeath(PlayerSession session, AdventureData data) {
        // Reset health to max, remove debuffs
        var health = data.getVital("health");
        if (health != null) {
            health.setCurrent(health.getEffectiveMax());
        }

        // Remove all non-permanent effects (clear debuffs)
        data.getActiveEffects().removeIf(e -> !e.isPermanent());

        // Reset hunger/thirst to half
        var hunger = data.getVital("hunger");
        if (hunger != null) {
            hunger.setCurrent(hunger.getEffectiveMax() * 0.5);
        }
        var thirst = data.getVital("thirst");
        if (thirst != null) {
            thirst.setCurrent(thirst.getEffectiveMax() * 0.5);
        }

        // Reset adrenaline
        var adrenaline = data.getVital("adrenaline");
        if (adrenaline != null) {
            adrenaline.setCurrent(0);
        }

        // Reset air and surface
        data.setUnderwater(false);
        var air = data.getVital("air");
        if (air != null) {
            air.setCurrent(air.getEffectiveMax());
        }

        // Notify client
        clientService.sendSystemNotification(session, "Death", "You have died and been revived.");

        // Send updated vitals
        sendVitalsUpdate(session, data);
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
