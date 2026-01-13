package de.mhus.nimbus.world.control.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for YAML serialization used in import/export.
 * Creates a deterministic, diff-friendly YAML mapper.
 */
@Configuration
public class YamlMapperConfig {

    /**
     * Creates a YAMLMapper configured for deterministic output.
     * - Java 8 Time support (Instant, LocalDateTime, etc.)
     * - Dates as ISO-8601 strings (not timestamps)
     * - Map entries sorted by keys (for consistent diffs)
     * - No document start marker (---)
     * - Minimal quotes (cleaner output)
     * - Array indents with indicators (better readability)
     */
    @Bean("syncYamlMapper")
    public YAMLMapper syncYamlMapper() {
        YAMLMapper mapper = YAMLMapper.builder()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .configure(YAMLGenerator.Feature.WRITE_DOC_START_MARKER, false)
                .configure(YAMLGenerator.Feature.MINIMIZE_QUOTES, true)
                .configure(YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR, true)
                .build();

        // Register JavaTimeModule for Java 8 date/time types
        mapper.registerModule(new JavaTimeModule());

        return mapper;
    }
}
