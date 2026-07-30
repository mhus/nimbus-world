package de.mhus.nimbus.world.control.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.dataformat.yaml.YAMLMapper;
import tools.jackson.dataformat.yaml.YAMLWriteFeature;

/**
 * Configuration for YAML serialization used in import/export.
 * Creates a deterministic, diff-friendly YAML mapper.
 */
@Configuration
public class YamlMapperConfig {

    /**
     * Creates a YAMLMapper configured for deterministic output.
     * - Java 8 Time support (built-in in Jackson 3)
     * - Dates as ISO-8601 strings (not timestamps)
     * - Map entries sorted by keys (for consistent diffs)
     * - No document start marker (---)
     * - Minimal quotes (cleaner output)
     * - Array indents with indicators (better readability)
     */
    @Bean("syncYamlMapper")
    public YAMLMapper syncYamlMapper() {
        // Jackson 3: mapper is immutable, configure through the builder.
        // java.time support (jsr310) is built-in and registered automatically.
        return YAMLMapper.builder()
                .configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .configure(YAMLWriteFeature.WRITE_DOC_START_MARKER, false)
                .configure(YAMLWriteFeature.MINIMIZE_QUOTES, true)
                .configure(YAMLWriteFeature.INDENT_ARRAYS_WITH_INDICATOR, true)
                .build();
    }
}
