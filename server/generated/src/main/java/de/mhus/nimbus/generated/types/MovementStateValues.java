/*
 * Source TS: PlayerInfo.ts
 * Original TS: 'interface MovementStateValues'
 */
package de.mhus.nimbus.generated.types;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class MovementStateValues {
    private MovementStateDomensions dimensions;
    @com.fasterxml.jackson.annotation.JsonProperty("baseMoveSpeed")
    private double baseMoveSpeed;
    @com.fasterxml.jackson.annotation.JsonProperty("effectiveMoveSpeed")
    private double effectiveMoveSpeed;
    @com.fasterxml.jackson.annotation.JsonProperty("baseJumpSpeed")
    private double baseJumpSpeed;
    @com.fasterxml.jackson.annotation.JsonProperty("effectiveJumpSpeed")
    private double effectiveJumpSpeed;
    @com.fasterxml.jackson.annotation.JsonProperty("eyeHeight")
    private double eyeHeight;
    @com.fasterxml.jackson.annotation.JsonProperty("baseTurnSpeed")
    private double baseTurnSpeed;
    @com.fasterxml.jackson.annotation.JsonProperty("effectiveTurnSpeed")
    private double effectiveTurnSpeed;
    @com.fasterxml.jackson.annotation.JsonProperty("selectionRadius")
    private double selectionRadius;
    @com.fasterxml.jackson.annotation.JsonProperty("stealthRange")
    private double stealthRange;
    @com.fasterxml.jackson.annotation.JsonProperty("distanceNotifyReduction")
    private double distanceNotifyReduction;
}
