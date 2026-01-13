/*
 * Source TS: EffectTriggerMessage.ts
 * Original TS: 'interface EffectTriggerData'
 */
package de.mhus.nimbus.generated.network.messages;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class EffectTriggerData {
    @com.fasterxml.jackson.annotation.JsonProperty("effectId")
    private String effectId;
    @com.fasterxml.jackson.annotation.JsonProperty("entityId")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String entityId;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private java.util.List<ChunkCoordinate> chunks;
    private de.mhus.nimbus.generated.scrawl.ScriptActionDefinition effect;
}
