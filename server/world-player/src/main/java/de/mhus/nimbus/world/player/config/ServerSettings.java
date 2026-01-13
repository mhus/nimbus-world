package de.mhus.nimbus.world.player.config;

import de.mhus.nimbus.shared.service.SSettingsService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ServerSettings {

    private final SSettingsService settingsService;

    @Value("${nimbus.server.websocketUrl:}")
    private String websocketUrl;
    @Value("${nimbus.server.controlsBaseUrl:}")
    private String controlsBaseUrl;

    @PostConstruct
    private void init() {
    }

    /**
     * WebSocket URL for client connection.
     * Default: ws://localhost:9042/ws
     */
    public String getWebsocketUrl() {
        return Strings.isBlank(websocketUrl) ?
                "ws://localhost:9042/player/ws"
                :
                websocketUrl;
    }

    public String getControlsBaseUrl() {
        return Strings.isBlank(controlsBaseUrl) ?
                "http://localhost:3002/controls"
                :
                controlsBaseUrl;
    }
}
