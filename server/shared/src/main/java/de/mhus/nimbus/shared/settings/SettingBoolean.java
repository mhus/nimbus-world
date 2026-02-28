package de.mhus.nimbus.shared.settings;

import de.mhus.nimbus.shared.service.SSettingsService;
import lombok.Getter;

public class SettingBoolean implements SettingValue {

  private final boolean defaultValue;
  @Getter
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
    if (value != null && System.currentTimeMillis() - lastAccess < getCacheTimeout()) {
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

}
