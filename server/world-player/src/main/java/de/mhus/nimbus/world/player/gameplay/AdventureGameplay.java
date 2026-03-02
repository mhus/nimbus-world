package de.mhus.nimbus.world.player.gameplay;

import de.mhus.nimbus.world.player.session.PlayerSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class AdventureGameplay extends BasicGameplay {

    @Override
    public void onSessionAuthenticated(PlayerSession session, Map<String, Object> savedGameplayData) {
        var data = new AdventureData();

        if (savedGameplayData != null && !savedGameplayData.isEmpty()) {
            data.setHealth(toDouble(savedGameplayData.get("health"), data.getHealth()));
            data.setHunger(toDouble(savedGameplayData.get("hunger"), data.getHunger()));
            data.setThirst(toDouble(savedGameplayData.get("thirst"), data.getThirst()));
            data.setStamina(toDouble(savedGameplayData.get("stamina"), data.getStamina()));
            data.setMaxHealth(toDouble(savedGameplayData.get("maxHealth"), data.getMaxHealth()));
            data.setMaxHunger(toDouble(savedGameplayData.get("maxHunger"), data.getMaxHunger()));
            data.setMaxThirst(toDouble(savedGameplayData.get("maxThirst"), data.getMaxThirst()));
            data.setMaxStamina(toDouble(savedGameplayData.get("maxStamina"), data.getMaxStamina()));
            log.info("Restored adventure data for session {}: health={}, hunger={}, thirst={}, stamina={}",
                    session.getSessionId(), data.getHealth(), data.getHunger(), data.getThirst(), data.getStamina());
        } else {
            log.info("No saved adventure data for session {}, using defaults", session.getSessionId());
        }

        session.setGameplayData(data);
    }

    @Override
    public Map<String, Object> serialize(PlayerSession session) {
        if (session.getGameplayData() instanceof AdventureData data) {
            var map = new HashMap<String, Object>();
            map.put("type", "adventure");
            map.put("health", data.getHealth());
            map.put("hunger", data.getHunger());
            map.put("thirst", data.getThirst());
            map.put("stamina", data.getStamina());
            map.put("maxHealth", data.getMaxHealth());
            map.put("maxHunger", data.getMaxHunger());
            map.put("maxThirst", data.getMaxThirst());
            map.put("maxStamina", data.getMaxStamina());
            return map;
        }
        return Map.of();
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
