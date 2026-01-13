package de.mhus.nimbus.shared.settings;

import de.mhus.nimbus.shared.service.SSettingsService;

public class SettingDouble implements SettingValue {

  private final double defaultValue;
  private final String key;
  private final SSettingsService service;
  private long lastAccess;
  private Double value;

  public SettingDouble(String key, SSettingsService service, double defaultValue) {
    this.key = key;
    this.service = service;
    this.defaultValue = defaultValue;
    get(); // touch to create
  }

  public double get() {
    if (service == null || key == null) {
      return defaultValue;
    }
    if (value != null && System.currentTimeMillis() - lastAccess < CACHE_TIMEOUT) {
      return value;
    }
    value = service.getOrCreateDoubleValue(key, defaultValue);
    lastAccess = System.currentTimeMillis();
    return value;
  }

  public void set(double value) {
    if (service != null && key != null) {
      service.setDoubleValue(key, value);
    }
  }

  public String getKey() {
    return key;
  }
}
