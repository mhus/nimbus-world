package de.mhus.nimbus.world.life.logic;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Effect handler that updates flags in the current context.
 * Parameters are key-value pairs (String → String) to set on the flag map.
 * Values are auto-parsed: "true"/"false" → Boolean, numeric → Number, else String.
 *
 * Example parameters: {"doorOpen": "true", "counter": "5", "label": "hello"}
 */
@Component
@Slf4j
public class LogicFlagUpdateHandler implements LogicEffectHandler {

    public static final String TYPE = "LogicFlagUpdate";

    @Override
    public Set<String> execute(Map<String, String> parameters, LogicContext context) {
        Set<String> changed = new HashSet<>();
        Map<String, Object> flags = context.getFlags();

        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            String key = entry.getKey();
            Object newValue = parseValue(entry.getValue());
            Object oldValue = flags.get(key);

            if (!Objects.equals(oldValue, newValue)) {
                flags.put(key, newValue);
                changed.add(key);
                log.debug("LogicFlagUpdate: {} = {} (was {})", key, newValue, oldValue);
            }
        }
        return changed;
    }

    /**
     * Parse a string value to a typed object.
     * "true"/"false" → Boolean, numeric strings → Number, null → null, else String.
     */
    static Object parseValue(String raw) {
        if (raw == null || "null".equals(raw)) return null;
        if ("true".equalsIgnoreCase(raw)) return true;
        if ("false".equalsIgnoreCase(raw)) return false;
        try {
            if (raw.contains(".")) return Double.parseDouble(raw);
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return raw;
        }
    }

    @Override
    public String getType() {
        return TYPE;
    }
}
