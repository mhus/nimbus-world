package de.mhus.nimbus.shared.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public class CastUtil {

    private static ObjectMapper mapper = new ObjectMapper();
    static {
        mapper.findAndRegisterModules();
    }


    public static int toint(Object value, int defaultValue) {
        try {
            return Integer.parseInt(toString(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private static String toString(Object value) {
        if (value == null) return null;
        if (value instanceof String) return (String) value;
        return value.toString();
    }

    public static long tolong(Object value, long defaultValue) {
        try {
            return Long.parseLong(toString(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static double todouble(Object value, double defaultValue) {
        try {
            return Double.parseDouble(toString(value));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static Map<Integer, String> mapOf(Object ... keyValues) {
        Map<Integer, String> map = new java.util.HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            Integer key = (Integer) keyValues[i];
            String value = (String) keyValues[i + 1];
            map.put(key, value);
        }
        return map;
    }

    public static String mapToString(Map<String, Object> resultData) {
        try {
            return mapper.writeValueAsString(resultData);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize resultData map to JSON", e);
        }
    }

    public static Map<String, Object> stringToMap(String json) {
        try {
            return mapper.readValue(json, mapper.getTypeFactory().constructMapType(Map.class, String.class, String.class));
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize JSON to Map<String, String>", e);
        }
    }

}
