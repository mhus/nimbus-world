package de.mhus.nimbus.shared.settings;

import de.mhus.nimbus.shared.service.SSettingsService;
import org.apache.logging.log4j.util.Strings;

public class SettingString implements SettingValue {

    private final String defaultValue;
  private final String key;
  private final SSettingsService service;
  private final String overwriteValue;
  private long lastAccess;
  private String value;

  public SettingString(String key, SSettingsService service, String defaultValue, String overwriteValue) {
    this.key = key;
    this.service = service;
    this.defaultValue = defaultValue;
    this.overwriteValue = overwriteValue;
    get(); // touch to create
  }

  public String get() {
    if (Strings.isNotBlank(overwriteValue)) return overwriteValue;
    if (service == null || key == null) {
      return defaultValue;
    }
    if (System.currentTimeMillis() - lastAccess < CACHE_TIMEOUT) {
        return value;
    }
    value = service.getOrCreateStringValue(key, defaultValue);
    lastAccess = System.currentTimeMillis();
    return value;
  }

  public void set(String value) {
    if (service != null && key != null) {
      service.setStringValue(key, value);
    }
  }

  public String getKey() {
    return key;
  }
}
