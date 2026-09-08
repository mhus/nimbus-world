package de.mhus.nimbus.shared.settings;

import de.mhus.nimbus.shared.utils.StaticApplicationProvider;

public interface SettingValue {

    static long getCacheTimeout() {
        return StaticApplicationProvider.getProperty("nimbus.settings.cacheTimeout", 1000 * 60); // 1 minute
    }
}
