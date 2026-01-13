package de.mhus.nimbus.world.player.ws;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WorldWebSocketConfig implements WebSocketConfigurer {

    private final WorldWebSocketHandler handler;

    public WorldWebSocketConfig(WorldWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/player/ws/world/*").setAllowedOrigins("*");
    }
}
