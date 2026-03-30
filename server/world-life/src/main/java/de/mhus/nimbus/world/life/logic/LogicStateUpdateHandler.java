package de.mhus.nimbus.world.life.logic;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Effect handler that updates flags in the current context.
 * Parameters are key-value pairs (String -> String) to set on the state map.
 * Values are auto-parsed: "true"/"false" -> Boolean, numeric -> Number, else String.
 *
 * Keys follow package scoping:
 *   "flag1"           -> resolved to "{rulePackage}.flag1" (shorthand)
 *   "otherPkg.flag2"  -> fully qualified cross-package access
 *
 * Example parameters: {"doorOpen": "true", "quest.completed": "true"}
 */
@Component
@Slf4j
public class LogicStateUpdateHandler implements LogicEffectHandler {

    public static final String TYPE = "state_update";

    @Override
    public Set<String> execute(Map<String, String> parameters, LogicContext context) {
        Set<String> changed = new HashSet<>();
        LogicStateMap flags = context.getFlags();
        String rulePackage = context.getRulePackage() != null ? context.getRulePackage() : "default";

        for (Map.Entry<String, String> entry : parameters.entrySet()) {
            String key = entry.getKey();
            Object newValue = parseValue(entry.getValue());

            // Resolve package: "flag1" -> "rulePackage.flag1", "pkg.flag" stays as-is
            String qualifiedKey = key.contains(".") ? key : rulePackage + "." + key;

            Object oldValue = flags.getQualified(qualifiedKey);

            if (!Objects.equals(oldValue, newValue)) {
                flags.putQualified(qualifiedKey, newValue);
                changed.add(qualifiedKey);
                log.debug("state_update: {} = {} (was {})", qualifiedKey, newValue, oldValue);
            }
        }
        return changed;
    }

    /**
     * Parse a string value to a typed object.
     * "true"/"false" -> Boolean, numeric strings -> Number, null -> null, else String.
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
