package de.mhus.nimbus.world.player.gameplay.adventure.handler;

import de.mhus.nimbus.world.player.gameplay.AdventureData;
import de.mhus.nimbus.world.player.gameplay.AdventureGameplay;
import de.mhus.nimbus.world.player.session.PlayerSession;
import de.mhus.nimbus.world.shared.gameplay.ActiveEffect;
import de.mhus.nimbus.world.shared.gameplay.CombatStat;
import de.mhus.nimbus.world.shared.gameplay.VitalValue;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles serialization and deserialization of adventure gameplay data.
 */
@Slf4j
public class SerializationHandler {

    private final AdventureGameplay gameplay;

    public SerializationHandler(AdventureGameplay gameplay) {
        this.gameplay = gameplay;
    }

    /**
     * Serialize adventure data to a map for persistence.
     */
    public Map<String, Object> serializeData(PlayerSession session, AdventureData data) {
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

    /**
     * Restore adventure data from a saved map.
     */
    @SuppressWarnings("unchecked")
    public void restoreData(AdventureData data, Map<String, Object> saved) {
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
    public void restoreLegacyVitals(AdventureData data, Map<String, Object> saved) {
        var vitalsHandler = gameplay.getVitalsHandler();
        vitalsHandler.setVitalCurrent(data, "health",  toDouble(saved.get("health"),  100));
        vitalsHandler.setVitalCurrent(data, "hunger",  toDouble(saved.get("hunger"),  0));
        vitalsHandler.setVitalCurrent(data, "thirst",  toDouble(saved.get("thirst"),  0));
        vitalsHandler.setVitalCurrent(data, "stamina", toDouble(saved.get("stamina"), 100));

        vitalsHandler.setVitalBase(data, "health",  toDouble(saved.get("maxHealth"),  100));
        vitalsHandler.setVitalBase(data, "hunger",  toDouble(saved.get("maxHunger"),  100));
        vitalsHandler.setVitalBase(data, "thirst",  toDouble(saved.get("maxThirst"),  100));
        vitalsHandler.setVitalBase(data, "stamina", toDouble(saved.get("maxStamina"), 100));
    }

    /**
     * Convert an object to a double value with a default fallback.
     */
    public double toDouble(Object value, double defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number number) return number.doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
