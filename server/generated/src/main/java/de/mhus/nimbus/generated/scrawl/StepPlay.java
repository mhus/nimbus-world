/*
 * Source TS: ScrawlStep.ts
 * Original TS: 'interface StepPlay'
 */
package de.mhus.nimbus.generated.scrawl;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class StepPlay {
    private String kind;
    @com.fasterxml.jackson.annotation.JsonProperty("effectId")
    private String effectId;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private java.util.Map<String, Object> ctx;
    @com.fasterxml.jackson.annotation.JsonProperty("receivePlayerDirection")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private java.lang.Boolean receivePlayerDirection;
}
