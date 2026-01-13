/*
 * Source TS: AnimationData.ts
 * Original TS: 'interface AnimationInstance'
 */
package de.mhus.nimbus.generated.types;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class AnimationInstance {
    @com.fasterxml.jackson.annotation.JsonProperty("templateId")
    private String templateId;
    private AnimationData animation;
    @com.fasterxml.jackson.annotation.JsonProperty("createdAt")
    private long createdAt;
    @com.fasterxml.jackson.annotation.JsonProperty("triggeredBy")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String triggeredBy;
}
