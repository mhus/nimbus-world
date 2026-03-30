package de.mhus.nimbus.world.life.logic;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Effect handler that updates flags in the current context.
 * Parameters are key-value pairs to set on the flag map.
 *
 * Example parameters: {"doorOpen": true, "counter": 5}
 */
@Component
@Slf4j
public class LogicFlagUpdateHandler implements LogicEffectHandler {

    public static final String TYPE = "LogicFlagUpdate";

    @Override
    public Set<String> execute(Map<String, Object> parameters, LogicContext context) {
        Set<String> changed = new HashSet<>();
        Map<String, Object> flags = context.getFlags();

        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            String key = entry.getKey();
            Object newValue = entry.getValue();
            Object oldValue = flags.get(key);

            if (!Objects.equals(oldValue, newValue)) {
                flags.put(key, newValue);
                changed.add(key);
                log.debug("LogicFlagUpdate: {} = {} (was {})", key, newValue, oldValue);
            }
        }
        return changed;
    }

    @Override
    public String getType() {
        return TYPE;
    }
}
