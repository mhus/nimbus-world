/*
 * Source TS: EffectParameterUpdateMessage.ts
 * Original TS: 'interface EffectParameterUpdateData'
 */
package de.mhus.nimbus.generated.network.messages;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class EffectParameterUpdateData {
    @com.fasterxml.jackson.annotation.JsonProperty("effectId")
    private String effectId;
    @com.fasterxml.jackson.annotation.JsonProperty("paramName")
    private String paramName;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private Object value;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private java.util.List<ChunkCoordinate> chunks;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private de.mhus.nimbus.generated.types.SerializableTargetingContext targeting;
}
