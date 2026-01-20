package de.mhus.nimbus.shared.utils;

public class CastUtil {

    public static int toint(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
