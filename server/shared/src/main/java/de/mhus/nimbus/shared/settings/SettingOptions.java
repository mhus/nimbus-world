package de.mhus.nimbus.shared.settings;

import de.mhus.nimbus.shared.service.SSettingsService;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;

public class SettingOptions implements SettingValue {

  private final String defaultValue;
  @Getter
  private final List<String> options;
  private final String key;
  private final SSettingsService service;
  private long lastAccess;
  private String value;

  public SettingOptions(String key, SSettingsService service, String defaultValue, String... options) {
    this.key = key;
    this.service = service;
    this.defaultValue = defaultValue;
    this.options = Arrays.stream(options).toList();
    get(); // touch to create
  }

  public String get() {
    if (service == null || key == null) {
      return defaultValue;
    }
    if (value != null && System.currentTimeMillis() - lastAccess < CACHE_TIMEOUT) {
      return value;
    }
    value = service.getStringValue(key, defaultValue);

    // Validate that the value is one of the valid options
    if (value != null && !options.isEmpty() && !options.contains(value)) {
      service.setStringValue(key, defaultValue);
      value = defaultValue;
    }

    lastAccess = System.currentTimeMillis();
    return value;
  }

  public void set(String value) {
    if (service != null && key != null) {
      // Validate that the value is one of the valid options
      if (value != null && !options.isEmpty() && !options.contains(value)) {
        throw new IllegalArgumentException("Value '" + value + "' is not a valid option. Valid options: " + options);
      }
      service.setStringValue(key, value);
    }
  }

  public String getKey() {
    return key;
  }

  public boolean isValidOption(String value) {
    return options.isEmpty() || options.contains(value);
  }
}
