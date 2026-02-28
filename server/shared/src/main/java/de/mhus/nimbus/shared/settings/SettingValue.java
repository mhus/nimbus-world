package de.mhus.nimbus.shared.settings;

import de.mhus.nimbus.shared.utils.StaticApplicationProvider;
import org.springframework.context.support.StaticApplicationContext;

public interface SettingValue {

    default long getCacheTimeout() {
        return StaticApplicationProvider.getProperty("nimbus.settings.cacheTimeout", 1000 * 60); // 1 minute
    }

}
