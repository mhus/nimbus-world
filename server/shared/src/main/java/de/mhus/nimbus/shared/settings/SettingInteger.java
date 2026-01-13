package de.mhus.nimbus.shared.settings;

import de.mhus.nimbus.shared.service.SSettingsService;

public class SettingInteger implements SettingValue {

  private final int defaultValue;
  private final String key;
  private final SSettingsService service;
  private long lastAccess;
  private Integer value;

  public SettingInteger(String key, SSettingsService service, int defaultValue) {
    this.key = key;
    this.service = service;
    this.defaultValue = defaultValue;
    get(); // touch to create
  }

  public int get() {
    if (service == null || key == null) {
      return defaultValue;
    }
    if (value != null && System.currentTimeMillis() - lastAccess < CACHE_TIMEOUT) {
      return value;
    }
    value = service.getOrCreateIntValue(key, defaultValue);
    lastAccess = System.currentTimeMillis();
    return value;
  }

  public void set(int value) {
    if (service != null && key != null) {
      service.setIntValue(key, value);
    }
  }

  public String getKey() {
    return key;
  }
}
