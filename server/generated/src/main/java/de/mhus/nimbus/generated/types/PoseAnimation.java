/*
 * Source TS: EntityData.ts
 * Original TS: 'interface PoseAnimation'
 */
package de.mhus.nimbus.generated.types;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class PoseAnimation {
    @com.fasterxml.jackson.annotation.JsonProperty("animationName")
    private String animationName;
    @com.fasterxml.jackson.annotation.JsonProperty("speedMultiplier")
    private double speedMultiplier;
    private boolean loop;
}
