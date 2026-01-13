/*
 * Source TS: PlayerInfo.ts
 * Original TS: 'interface MovementStateDomensions'
 */
package de.mhus.nimbus.generated.types;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class MovementStateDomensions {
    private double height;
    private double width;
    private double footprint;
}
