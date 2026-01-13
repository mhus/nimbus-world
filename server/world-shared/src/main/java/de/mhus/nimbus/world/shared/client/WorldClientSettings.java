package de.mhus.nimbus.world.shared.client;

import de.mhus.nimbus.shared.service.SSettingsService;
import de.mhus.nimbus.shared.settings.SettingInteger;
import de.mhus.nimbus.shared.settings.SettingString;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for inter-server command communication.
 * Loaded from SSettingsService at startup.
 */
@Component
@RequiredArgsConstructor
public class WorldClientSettings {

    private final SSettingsService settingsService;


    @Value("${nimbus.pod.playerBaseUrl:}")
    private String playerBaseUrl;
    @Value("${nimbus.pod.lifeBaseUrl:}")
    private String lifeBaseUrl;
    @Value("${nimbus.pod.controlBaseUrl:}")
    private String controlBaseUrl;
    @Value("${nimbus.pod.generatorBaseUrl:}")
    private String generatorBaseUrl;

    private SettingInteger commandTimeoutMs;

    @PostConstruct
    private void init() {
        commandTimeoutMs = settingsService.getInteger(
                "client.commandTimeoutMs",
                5000
        );
    }

    /**
     * Base URL for world-player server.
     * Example: http://world-player:9042
     * Default: http://localhost:9042
     */
    public String getPlayerBaseUrl() {
        return Strings.isBlank(playerBaseUrl) ?
                "http://localhost:9042"
                :
                playerBaseUrl;
    }

    /**
     * Base URL for world-life server.
     * Example: http://world-life:9044
     * Default: http://localhost:9044
     */
    public String getLifeBaseUrl() {
        return Strings.isBlank(lifeBaseUrl) ?
                "http://localhost:9044"
                :
                lifeBaseUrl;
    }

    /**
     * Base URL for world-control server.
     * Example: http://world-control:9043
     * Default: http://localhost:9043
     */
    public String getControlBaseUrl() {
        return Strings.isBlank(controlBaseUrl) ?
                "http://localhost:9043"
                :
                controlBaseUrl;
    }

    /**
     * Base URL for world-control server.
     * Example: http://world-generator:9045
     * Default: http://localhost:9045
     */
    public String getGeneratorBaseUrl() {
        return Strings.isBlank(generatorBaseUrl) ?
                "http://localhost:9045"
                :
                generatorBaseUrl;
    }

    /**
     * Command timeout in milliseconds.
     * Default: 5000ms (5 seconds)
     */
    public long getCommandTimeoutMs() {
        return commandTimeoutMs.get();
    }
}
