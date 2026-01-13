package de.mhus.nimbus.world.control.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Jackson configuration for proper Java 8 date/time serialization.
 * Enables support for Instant, LocalDateTime, etc.
 */
@Configuration
public class JacksonConfig {

    /**
     * Configures the primary ObjectMapper with Java 8 Time support.
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Register JavaTimeModule for Java 8 date/time types (Instant, LocalDateTime, etc.)
        mapper.registerModule(new JavaTimeModule());

        // Write dates as ISO-8601 strings instead of timestamps
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        return mapper;
    }
}
