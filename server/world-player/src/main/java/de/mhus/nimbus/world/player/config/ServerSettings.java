package de.mhus.nimbus.world.player.config;

import de.mhus.nimbus.shared.service.SSettingsService;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
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
     * If NIMBUS_WEBSOCKET_URL is set, uses that value.
     * Otherwise derives from the request Host header (works behind proxy/ingress).
     * Falls back to ws://localhost:9042/player/ws for local dev.
     */
    public String getWebsocketUrl(HttpServletRequest request) {
        if (Strings.isNotBlank(websocketUrl)) {
            return websocketUrl;
        }
        if (request != null) {
            String host = request.getHeader("Host");
            if (Strings.isNotBlank(host)) {
                String scheme = "ws";
                String forwardedProto = request.getHeader("X-Forwarded-Proto");
                if ("https".equalsIgnoreCase(forwardedProto) || request.isSecure()) {
                    scheme = "wss";
                }
                return scheme + "://" + host + "/player/ws";
            }
        }
        return "ws://localhost:9042/player/ws";
    }

    /**
     * WebSocket URL without request context (local dev fallback).
     */
    public String getWebsocketUrl() {
        return getWebsocketUrl(null);
    }

    public String getControlsBaseUrl() {
        return Strings.isBlank(controlsBaseUrl) ?
                "http://localhost:3002/controls"
                :
                controlsBaseUrl;
    }
}
