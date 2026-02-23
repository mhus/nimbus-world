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
