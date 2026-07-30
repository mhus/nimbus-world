package de.mhus.nimbus.shared.utils;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class StaticApplicationProvider {

    @Getter
    private static StaticApplicationProvider instance;

    private final ApplicationContext context;

    private final Map<String, Long> propertiesCacheLong = new ConcurrentHashMap<>();

    @PostConstruct
    private void init() {
        instance = this;
        log.info("StaticPropertiesProvider initialized");
    }

    private long getApplicationProperty(String key, long defaultValue) {
        if (context == null) {
            log.warn("ApplicationContext not available, returning default value for key '{}'", key);
            return defaultValue;
        }
        return propertiesCacheLong.computeIfAbsent(key, k -> CastUtil.tolong(context.getEnvironment().getProperty(k, String.valueOf(defaultValue)), defaultValue));
    }

    public static long getProperty(String key, long defaultValue) {
        StaticApplicationProvider provider = getInstance();
        if (provider != null) {
            return provider.getApplicationProperty(key, defaultValue);
        } else {
            log.warn("StaticApplicationProvider instance not available, returning default value for key '{}'", key);
            return defaultValue;
        }
    }

}
