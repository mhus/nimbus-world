package de.mhus.nimbus.shared.settings;

import de.mhus.nimbus.shared.service.SSettingsService;

public class SettingBoolean implements SettingValue {

  private final boolean defaultValue;
  private final String key;
  private final SSettingsService service;
  private long lastAccess;
  private Boolean value;

  public SettingBoolean(String key, SSettingsService service, boolean defaultValue) {
    this.key = key;
    this.service = service;
    this.defaultValue = defaultValue;
    get(); // touch to create
  }

  public boolean get() {
    if (service == null || key == null) {
      return defaultValue;
    }
    if (value != null && System.currentTimeMillis() - lastAccess < CACHE_TIMEOUT) {
      return value;
    }
    value = service.getOrCreateBooleanValue(key, defaultValue);
    lastAccess = System.currentTimeMillis();
    return value;
  }

  public void set(boolean value) {
    if (service != null && key != null) {
      service.setBooleanValue(key, value);
    }
  }

  public String getKey() {
    return key;
  }
}
