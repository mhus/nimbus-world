package de.mhus.nimbus.world.control.config;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Jackson configuration for proper Java 8 date/time serialization.
 * Enables support for Instant, LocalDateTime, etc.
 * Configures increased limits for large JSON payloads (model imports).
 */
@Configuration
public class JacksonConfig {

    /**
     * Configures the primary ObjectMapper with Java 8 Time support
     * and increased StreamReadConstraints for large JSON payloads.
     * <p>
     * Default maxStringLength is 20MB, but large model imports can exceed this.
     * Increased to 200MB to support large model layer imports.
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        // Configure StreamReadConstraints with increased limits
        StreamReadConstraints constraints = StreamReadConstraints.builder()
                .maxStringLength(200_000_000) // 200MB (up from 20MB default)
                .build();

        // Create JsonFactory with custom constraints
        JsonFactory jsonFactory = JsonFactory.builder()
                .streamReadConstraints(constraints)
                .build();

        // Create ObjectMapper with custom factory
        ObjectMapper mapper = new ObjectMapper(jsonFactory);

        // Register JavaTimeModule for Java 8 date/time types (Instant, LocalDateTime, etc.)
        mapper.registerModule(new JavaTimeModule());

        // Write dates as ISO-8601 strings instead of timestamps
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        return mapper;
    }
}
