package de.mhus.nimbus.world.life.logic;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A Map wrapper that tracks which keys were changed.
 * Used as the SpEL root object so that assignments like
 * "flags.flag1 = true" are automatically tracked.
 */
public class LogicFlagMap extends HashMap<String, Object> {

    private final Set<String> changedKeys = new HashSet<>();

    public LogicFlagMap() {
        super();
    }

    public LogicFlagMap(Map<String, Object> source) {
        super(source != null ? source : Map.of());
    }

    @Override
    public Object put(String key, Object value) {
        Object previous = super.put(key, value);
        if (!Objects.equals(previous, value)) {
            changedKeys.add(key);
        }
        return previous;
    }

    /**
     * Returns the set of keys that were changed since creation.
     */
    public Set<String> getChangedKeys() {
        return changedKeys;
    }

    /**
     * Clear the change tracker (e.g. after persisting).
     */
    public void clearChanges() {
        changedKeys.clear();
    }
}
