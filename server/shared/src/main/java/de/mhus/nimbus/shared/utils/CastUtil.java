package de.mhus.nimbus.shared.utils;

import java.util.Map;

public class CastUtil {

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
}
