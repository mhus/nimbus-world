/*
 * Source TS: EntityData.ts
 * Original TS: 'interface OverlayMovementConfig'
 */
package de.mhus.nimbus.generated.types;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class OverlayMovementConfig {
    @com.fasterxml.jackson.annotation.JsonProperty("movementMode")
    private String movementMode;
    private float speed;
    @com.fasterxml.jackson.annotation.JsonProperty("jumpSpeed")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private float jumpSpeed;
    @com.fasterxml.jackson.annotation.JsonProperty("eyeHeight")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private float eyeHeight;
    @com.fasterxml.jackson.annotation.JsonProperty("turnSpeed")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private float turnSpeed;
}
