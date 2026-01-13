/*
 * Source TS: ServerEntitySpawnDefinition.ts
 * Original TS: 'interface BehaviorConfig'
 */
package de.mhus.nimbus.generated.types;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class BehaviorConfig {
    @com.fasterxml.jackson.annotation.JsonProperty("minStepDistance")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private java.lang.Double minStepDistance;
    @com.fasterxml.jackson.annotation.JsonProperty("maxStepDistance")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private java.lang.Double maxStepDistance;
    @com.fasterxml.jackson.annotation.JsonProperty("waypointsPerPath")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private java.lang.Double waypointsPerPath;
    @com.fasterxml.jackson.annotation.JsonProperty("minIdleDuration")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private java.lang.Double minIdleDuration;
    @com.fasterxml.jackson.annotation.JsonProperty("maxIdleDuration")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private java.lang.Double maxIdleDuration;
    @com.fasterxml.jackson.annotation.JsonProperty("pathwayInterval")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private java.lang.Double pathwayInterval;
}
