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
    @com.fasterxml.jackson.annotation.JsonProperty("thirdPersonModelId")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String thirdPersonModelId;
    @com.fasterxml.jackson.annotation.JsonProperty("thirdPersonModelModifiers")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private java.util.Map<String, String> thirdPersonModelModifiers;
}
