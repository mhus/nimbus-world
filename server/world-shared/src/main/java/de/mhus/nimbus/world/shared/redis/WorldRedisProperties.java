package de.mhus.nimbus.world.shared.redis;

import lombok.Data;
import org.springframework.aot.hint.annotation.Reflective;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;

import jakarta.annotation.PostConstruct;
import java.net.URI;

@Data
@ConfigurationProperties(prefix = "world.redis")
@Reflective
public class WorldRedisProperties {
    private String host = "localhost";
    private int port = 6379;
    private int database = 0;
    private String password; // optional
    private boolean ssl = false;

    @Value("${world.client.redis-url:}")
    private String redisUrl;

    @PostConstruct
    public void parseRedisUrl() {
        if (redisUrl != null && !redisUrl.isBlank()) {
            try {
                URI uri = URI.create(redisUrl);
                if (uri.getHost() != null) host = uri.getHost();
                if (uri.getPort() > 0) port = uri.getPort();
                if (uri.getUserInfo() != null) {
                    String[] parts = uri.getUserInfo().split(":", 2);
                    if (parts.length == 2) password = parts[1];
                }
                if (uri.getPath() != null && uri.getPath().length() > 1) {
                    try {
                        database = Integer.parseInt(uri.getPath().substring(1));
                    } catch (Exception ignored) {
                    }
                }
                ssl = uri.getScheme() != null && uri.getScheme().equalsIgnoreCase("rediss");
            } catch (Exception ignored) {
            }
        }
    }
}
