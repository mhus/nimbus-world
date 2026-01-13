/*
 * Source TS: PlayerMovementState.ts
 * Original TS: 'interface PlayerMovementStateChangedEvent'
 */
package de.mhus.nimbus.generated.types;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class PlayerMovementStateChangedEvent {
    @com.fasterxml.jackson.annotation.JsonProperty("playerId")
    private String playerId;
    @com.fasterxml.jackson.annotation.JsonProperty("oldState")
    private PlayerMovementState oldState;
    @com.fasterxml.jackson.annotation.JsonProperty("newState")
    private PlayerMovementState newState;
}
