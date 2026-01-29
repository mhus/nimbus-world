package de.mhus.nimbus.shared.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public class CastUtil {

    private static ObjectMapper mapper = new ObjectMapper();
    static {
        mapper.findAndRegisterModules();
    }


    public static int toint(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static long tolong(String value, long defaultValue) {
        try {
            return Long.parseLong(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static double todouble(String value, double defaultValue) {
        try {
            return Double.parseDouble(value);
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
