/*
 * Source TS: AnimationMessage.ts
 * Original TS: 'interface AnimationStartData'
 */
package de.mhus.nimbus.generated.network.messages;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class AnimationStartData {
    private double x;
    private double y;
    private double z;
    private de.mhus.nimbus.generated.types.AnimationData animation;
}
