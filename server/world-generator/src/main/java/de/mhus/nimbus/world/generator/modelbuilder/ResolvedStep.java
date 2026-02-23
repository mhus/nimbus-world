package de.mhus.nimbus.world.generator.modelbuilder;

import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A fully resolved step with merged parameters ready for execution.
 */
@Data
@Builder
public class ResolvedStep {

    private String name;
    private String type;
    private Map<String, Object> parameters;

    /**
     * Get a parameter as String.
     */
    public String getString(String key) {
        if (parameters == null) return null;
        Object value = parameters.get(key);
        return value != null ? String.valueOf(value) : null;
    }

    /**
     * Get a parameter as String with default.
     */
    public String getString(String key, String defaultValue) {
        String value = getString(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Get a parameter as int with default.
     */
    public int getInt(String key, int defaultValue) {
        if (parameters == null) return defaultValue;
        Object value = parameters.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Get a parameter as double with default.
     */
    public double getDouble(String key, double defaultValue) {
        if (parameters == null) return defaultValue;
        Object value = parameters.get(key);
        if (value == null) return defaultValue;
        if (value instanceof Number number) return number.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Get a parameter as list of strings.
     */
    @SuppressWarnings("unchecked")
    public List<String> getStringList(String key) {
        if (parameters == null) return Collections.emptyList();
        Object value = parameters.get(key);
        if (value == null) return Collections.emptyList();
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of(String.valueOf(value));
    }
}
