/*
 * Source TS: TargetingTypes.ts
 * Original TS: 'interface SerializableTargetingContext'
 */
package de.mhus.nimbus.generated.types;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class SerializableTargetingContext {
    private TargetingMode mode;
    @com.fasterxml.jackson.annotation.JsonProperty("targetType")
    private String targetType;
    @com.fasterxml.jackson.annotation.JsonProperty("entityId")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String entityId;
    @com.fasterxml.jackson.annotation.JsonProperty("blockPosition")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private SerializableTargetingContextBlockPositionDTO blockPosition;
    private SerializableTargetingContextPositionDTO position;
}
