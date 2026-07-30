package de.mhus.nimbus.world.player.ws;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.List;

/**
 * WebSocket configuration for the world player connection.
 *
 * <p>Allowed origins are restricted to a configured allowlist
 * ({@code world.cors.allowed-origins}) to prevent Cross-Site WebSocket
 * Hijacking. When no origins are configured, only localhost (any port) is
 * permitted for development — the previous {@code "*"} wildcard, which accepted
 * connections from any website, is no longer used.
 */
@Configuration
@EnableWebSocket
public class WorldWebSocketConfig implements WebSocketConfigurer {

    private final WorldWebSocketHandler handler;

    @Value("${world.cors.allowed-origins:}")
    private List<String> allowedOrigins;

    public WorldWebSocketConfig(WorldWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        String[] originPatterns = (allowedOrigins != null && !allowedOrigins.isEmpty())
                ? allowedOrigins.toArray(new String[0])
                : new String[] {"http://localhost:[*]", "https://localhost:[*]"};
        registry.addHandler(handler, "/player/ws/world/*")
                .setAllowedOriginPatterns(originPatterns);
    }
}
