package de.mhus.nimbus.world.life.util;

import de.mhus.nimbus.world.shared.world.WEntity;

import java.util.Map;

/**
 * Helper for reading typed values from an entity's server-side property map
 * ({@link WEntity#getServer()}). Centralizes the null/blank/parse handling that
 * was previously duplicated across several behaviors and services.
 */
public final class EntityServerData {

    private EntityServerData() {
    }

    /**
     * Read a double value from the entity's server property map.
     *
     * @param entity       the entity (may have a null server map)
     * @param key          property key
     * @param defaultValue value returned when the key is missing, blank or unparseable
     * @return parsed double value or the default
     */
    public static double getDouble(WEntity entity, String key, double defaultValue) {
        if (entity == null) return defaultValue;
        return getDouble(entity.getServer(), key, defaultValue);
    }

    /**
     * Read a double value from a server property map.
     */
    public static double getDouble(Map<String, String> server, String key, double defaultValue) {
        if (server == null) return defaultValue;
        String val = server.get(key);
        if (val == null || val.isBlank()) return defaultValue;
        try {
            return Double.parseDouble(val.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Read a long value from the entity's server property map.
     *
     * @param entity       the entity (may have a null server map)
     * @param key          property key
     * @param defaultValue value returned when the key is missing, blank or unparseable
     * @return parsed long value or the default
     */
    public static long getLong(WEntity entity, String key, long defaultValue) {
        if (entity == null) return defaultValue;
        Map<String, String> server = entity.getServer();
        if (server == null) return defaultValue;
        String val = server.get(key);
        if (val == null || val.isBlank()) return defaultValue;
        try {
            return Long.parseLong(val.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
