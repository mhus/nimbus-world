/*
 * Source TS: PlayerInfo.ts
 * Original TS: 'interface PlayerInfo'
 */
package de.mhus.nimbus.generated.types;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class PlayerInfo {
    @com.fasterxml.jackson.annotation.JsonProperty("playerId")
    private String playerId;
    private String title;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private java.util.Map<String, ShortcutDefinition> shortcuts;
    @com.fasterxml.jackson.annotation.JsonProperty("editorShortcuts")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private java.util.Map<String, ShortcutDefinition> editorShortcuts;
    @com.fasterxml.jackson.annotation.JsonProperty("stateValues")
    private java.util.Map<String, MovementStateValues> stateValues;
    @com.fasterxml.jackson.annotation.JsonProperty("baseWalkSpeed")
    private double baseWalkSpeed;
    @com.fasterxml.jackson.annotation.JsonProperty("baseRunSpeed")
    private double baseRunSpeed;
    @com.fasterxml.jackson.annotation.JsonProperty("baseUnderwaterSpeed")
    private double baseUnderwaterSpeed;
    @com.fasterxml.jackson.annotation.JsonProperty("baseCrawlSpeed")
    private double baseCrawlSpeed;
    @com.fasterxml.jackson.annotation.JsonProperty("baseRidingSpeed")
    private double baseRidingSpeed;
    @com.fasterxml.jackson.annotation.JsonProperty("baseJumpSpeed")
    private double baseJumpSpeed;
    @com.fasterxml.jackson.annotation.JsonProperty("effectiveWalkSpeed")
    private double effectiveWalkSpeed;
    @com.fasterxml.jackson.annotation.JsonProperty("effectiveRunSpeed")
    private double effectiveRunSpeed;
    @com.fasterxml.jackson.annotation.JsonProperty("effectiveUnderwaterSpeed")
    private double effectiveUnderwaterSpeed;
    @com.fasterxml.jackson.annotation.JsonProperty("effectiveCrawlSpeed")
    private double effectiveCrawlSpeed;
    @com.fasterxml.jackson.annotation.JsonProperty("effectiveRidingSpeed")
    private double effectiveRidingSpeed;
    @com.fasterxml.jackson.annotation.JsonProperty("effectiveJumpSpeed")
    private double effectiveJumpSpeed;
    @com.fasterxml.jackson.annotation.JsonProperty("eyeHeight")
    private double eyeHeight;
    @com.fasterxml.jackson.annotation.JsonProperty("stealthRange")
    private double stealthRange;
    @com.fasterxml.jackson.annotation.JsonProperty("distanceNotifyReductionWalk")
    private double distanceNotifyReductionWalk;
    @com.fasterxml.jackson.annotation.JsonProperty("distanceNotifyReductionCrouch")
    private double distanceNotifyReductionCrouch;
    @com.fasterxml.jackson.annotation.JsonProperty("selectionRadius")
    private double selectionRadius;
    @com.fasterxml.jackson.annotation.JsonProperty("baseTurnSpeed")
    private double baseTurnSpeed;
    @com.fasterxml.jackson.annotation.JsonProperty("effectiveTurnSpeed")
    private double effectiveTurnSpeed;
    @com.fasterxml.jackson.annotation.JsonProperty("baseUnderwaterTurnSpeed")
    private double baseUnderwaterTurnSpeed;
    @com.fasterxml.jackson.annotation.JsonProperty("effectiveUnderwaterTurnSpeed")
    private double effectiveUnderwaterTurnSpeed;
    @com.fasterxml.jackson.annotation.JsonProperty("thirdPersonModelId")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String thirdPersonModelId;
    @com.fasterxml.jackson.annotation.JsonProperty("thirdPersonModelModifiers")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private java.util.Map<String, String> thirdPersonModelModifiers;
}
