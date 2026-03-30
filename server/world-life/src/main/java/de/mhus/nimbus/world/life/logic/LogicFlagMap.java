package de.mhus.nimbus.world.life.logic;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Package-scoped flag storage for the Logic Machine.
 * Outer map: package name -> inner map (flag name -> value).
 *
 * SpEL navigates naturally: state.puzzle_door.flag1
 *   -> outer.get("puzzle_door") returns inner TrackingMap
 *   -> inner.get("flag1") returns the value
 *
 * Assignments: state.puzzle_door.flag1 = true
 *   -> outer.get("puzzle_door") returns inner TrackingMap
 *   -> inner.put("flag1", true) is tracked
 *
 * Changed flags are tracked as qualified names: "puzzle_door.flag1".
 */
public class LogicFlagMap extends HashMap<String, Object> {

    private final Set<String> changedKeys = new HashSet<>();

    public LogicFlagMap() {
        super();
    }

    /**
     * Load from WProgress progressData (nested map structure).
     * Each entry in source is a package -> map of flags.
     */
    @SuppressWarnings("unchecked")
    public LogicFlagMap(Map<String, Object> source) {
        super();
        if (source != null) {
            for (Map.Entry<String, Object> entry : source.entrySet()) {
                if (entry.getValue() instanceof Map) {
                    // Package entry: wrap inner map with tracking
                    Map<String, Object> inner = (Map<String, Object>) entry.getValue();
                    super.put(entry.getKey(), new TrackingMap(entry.getKey(), inner));
                } else {
                    // Non-map value (shouldn't happen, but be safe)
                    super.put(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    /**
     * When SpEL accesses a package that doesn't exist yet, auto-create it.
     * This allows "state.newPkg.flag1 = true" to work even if newPkg didn't exist.
     */
    @Override
    public Object get(Object key) {
        Object value = super.get(key);
        if (value == null && key instanceof String pkg) {
            // Auto-create package map on first access
            TrackingMap newPkg = new TrackingMap(pkg, new HashMap<>());
            super.put(pkg, newPkg);
            return newPkg;
        }
        return value;
    }

    /**
     * Returns all qualified flag names that were changed (e.g. "puzzle_door.flag1").
     */
    public Set<String> getChangedKeys() {
        return changedKeys;
    }

    public void clearChanges() {
        changedKeys.clear();
    }

    /**
     * Export as a plain nested map for WProgress persistence.
     */
    public Map<String, Object> toProgressData() {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : entrySet()) {
            if (entry.getValue() instanceof TrackingMap tm) {
                result.put(entry.getKey(), new HashMap<>(tm));
            } else {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    /**
     * Get a flag value by qualified name "pkg.flag".
     */
    public Object getQualified(String qualifiedName) {
        int dot = qualifiedName.indexOf('.');
        if (dot < 0) return null;
        String pkg = qualifiedName.substring(0, dot);
        String flag = qualifiedName.substring(dot + 1);
        Object pkgMap = super.get(pkg);
        if (pkgMap instanceof Map<?, ?> map) {
            return map.get(flag);
        }
        return null;
    }

    /**
     * Set a flag value by qualified name "pkg.flag".
     */
    public void putQualified(String qualifiedName, Object value) {
        int dot = qualifiedName.indexOf('.');
        if (dot < 0) return;
        String pkg = qualifiedName.substring(0, dot);
        String flag = qualifiedName.substring(dot + 1);
        Object pkgObj = get(pkg); // auto-creates if needed
        if (pkgObj instanceof TrackingMap tm) {
            tm.put(flag, value);
        }
    }

    /**
     * Inner map that tracks changes and reports them to the parent LogicFlagMap.
     */
    class TrackingMap extends HashMap<String, Object> {
        private final String packageName;

        TrackingMap(String packageName, Map<String, Object> source) {
            super(source);
            this.packageName = packageName;
        }

        @Override
        public Object put(String key, Object value) {
            Object previous = super.put(key, value);
            if (!Objects.equals(previous, value)) {
                changedKeys.add(packageName + "." + key);
            }
            return previous;
        }
    }
}
