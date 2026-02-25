package de.mhus.nimbus.world.generator.modelbuilder;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * JSON model definition for the ModelBuilder system.
 * Contains ordered steps and named step definitions with parameter defaults.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ModelBuilderModel {

    private List<Step> steps;
    private List<StepDefinition> definitions;

    /**
     * Arbitrary metadata for the model (e.g. placement constraints).
     * Known keys:
     * <ul>
     *   <li>{@code maxHeight} (Integer) - maximum height of the model in blocks</li>
     *   <li>{@code minWater} (Integer) - minimum water depth required for placement</li>
     *   <li>{@code maxWater} (Integer) - maximum water depth allowed for placement</li>
     *   <li>{@code land} (Boolean) - plant can grow on LAND positions (default true)</li>
     *   <li>{@code water} (Boolean) - plant can grow in WATER positions (default true)</li>
     *   <li>{@code sea} (Boolean) - plant can grow in SEA positions (default true)</li>
     *   <li>{@code emerse} (Boolean) - plant can grow above water surface (default false)</li>
     * </ul>
     */
    private Map<String, Object> metadata;

    /**
     * Get an integer value from metadata, or null if not present.
     */
    public Integer getMetadataInt(String key) {
        if (metadata == null) return null;
        Object value = metadata.get(key);
        if (value instanceof Number number) return number.intValue();
        if (value instanceof String str) {
            try { return Integer.parseInt(str); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    /**
     * Get a boolean value from metadata, or the given default if not present.
     */
    public boolean getMetadataBoolean(String key, boolean defaultValue) {
        if (metadata == null) return defaultValue;
        Object value = metadata.get(key);
        if (value instanceof Boolean bool) return bool;
        if (value instanceof String str) return Boolean.parseBoolean(str);
        return defaultValue;
    }

    /**
     * A single execution step referencing a definition by name.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Step {
        /** Name/lookup key for this step - used to find the matching definition */
        private String step;
        /** Optional override for definition lookup (if different from step name) */
        private String definition;
        /** Optional parameter overrides (merged on top of definition defaults) */
        private Map<String, Object> parameters;
        /** SpEL condition expression - step is skipped when this evaluates to false */
        private String when;
    }

    /**
     * A named definition that maps to a ModelPartBuilder type with default parameters.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StepDefinition {
        /** Reference key for lookup from steps */
        private String name;
        /** ModelPartBuilder.name() to use for execution */
        private String type;
        /** Default parameters for this definition */
        private Map<String, Object> parameters;
    }
}
