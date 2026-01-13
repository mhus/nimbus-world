package de.mhus.nimbus.world.shared.client;

import de.mhus.nimbus.world.shared.access.AccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.time.Duration;

/**
 * Configuration for inter-server REST communication.
 * Automatically adds Bearer token for authentication.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class WorldClientConfig {

    private final AccessService accessService;

    @Bean
    @ConditionalOnMissingBean(name = "worldRestTemplate")
    public RestTemplate worldRestTemplate(WorldClientSettings properties, RestTemplateBuilder builder) {
        // RestTemplate with automatic Bearer token authentication
        return builder
                .setConnectTimeout(Duration.ofMillis(properties.getCommandTimeoutMs()))
                .setReadTimeout(Duration.ofMillis(properties.getCommandTimeoutMs()))
                .additionalInterceptors(new BearerTokenInterceptor(accessService))
                .build();
    }

    /**
     * Interceptor that adds Bearer token to all outgoing requests.
     */
    @RequiredArgsConstructor
    static class BearerTokenInterceptor implements ClientHttpRequestInterceptor {

        private final AccessService accessService;

        @Override
        public ClientHttpResponse intercept(
                HttpRequest request,
                byte[] body,
                ClientHttpRequestExecution execution) throws IOException {

            // Get world token from AccessService
            try {
                String token = accessService.getWorldToken();
                request.getHeaders().setBearerAuth(token);
                log.debug("Added Bearer token to request: {} {}", request.getMethod(), request.getURI());
            } catch (Exception e) {
                log.error("Failed to get world token for request: {}", e.getMessage(), e);
                // Continue without token - let the target service reject it
            }

            return execution.execute(request, body);
        }
    }
}
