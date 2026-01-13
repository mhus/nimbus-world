package de.mhus.nimbus.world.control.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

/**
 * CORS configuration for world-control REST endpoints.
 * Allows requests from localhost frontend during development.
 *
 * IMPORTANT: When using credentials (cookies), wildcard (*) is NOT allowed.
 * Configure specific origins via application.yml:
 *   world.cors.allowed-origins:
 *     - http://localhost:3001
 *     - http://localhost:8002
 */
@Configuration
public class CorsConfig {

    @Value("${world.cors.allowed-origins:}")
    private List<String> allowedOrigins;

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();

        // Allow credentials (required for cookies)
        config.setAllowCredentials(true);
        config.setAllowPrivateNetwork(true);

        // Allow origins (must be specific when using credentials)
        if (allowedOrigins != null && !allowedOrigins.isEmpty()) {
            allowedOrigins.forEach(config::addAllowedOriginPattern);
        } else {
            // Default: allow all localhost ports using Spring's pattern syntax
            config.addAllowedOriginPattern("http://localhost:[*]");
        }

        // Allow all headers
        config.addAllowedHeader("*");

        // Allow all HTTP methods
        config.addAllowedMethod("*");

        // Apply to all endpoints
        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }
}
