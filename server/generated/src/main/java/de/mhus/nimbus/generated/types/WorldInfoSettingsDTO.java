/*
 * Source TS: World.ts
 * Original TS: 'interface WorldInfoSettingsDTO'
 */
package de.mhus.nimbus.generated.types;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class WorldInfoSettingsDTO {
    @com.fasterxml.jackson.annotation.JsonProperty("maxPlayers")
    private int maxPlayers;
    @com.fasterxml.jackson.annotation.JsonProperty("allowGuests")
    private boolean allowGuests;
    @com.fasterxml.jackson.annotation.JsonProperty("pvpEnabled")
    private boolean pvpEnabled;
    @com.fasterxml.jackson.annotation.JsonProperty("pingInterval")
    private long pingInterval;
    @com.fasterxml.jackson.annotation.JsonProperty("allowedMovementModes")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private java.util.List<String> allowedMovementModes;
    @com.fasterxml.jackson.annotation.JsonProperty("defaultMovementMode")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String defaultMovementMode;
    @com.fasterxml.jackson.annotation.JsonProperty("deadAmbientAudio")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String deadAmbientAudio;
    @com.fasterxml.jackson.annotation.JsonProperty("swimStepAudio")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String swimStepAudio;
    @com.fasterxml.jackson.annotation.JsonProperty("clearColor")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private WorldInfoSettingsDTOClearColorDTO clearColor;
    @com.fasterxml.jackson.annotation.JsonProperty("cameraMaxZ")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private int cameraMaxZ;
    @com.fasterxml.jackson.annotation.JsonProperty("sunTexture")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String sunTexture;
    @com.fasterxml.jackson.annotation.JsonProperty("sunSize")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private java.lang.Double sunSize;
    @com.fasterxml.jackson.annotation.JsonProperty("sunAngleY")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private java.lang.Double sunAngleY;
    @com.fasterxml.jackson.annotation.JsonProperty("sunElevation")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private java.lang.Double sunElevation;
    @com.fasterxml.jackson.annotation.JsonProperty("sunColor")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private WorldInfoSettingsDTOSunColorDTO sunColor;
    @com.fasterxml.jackson.annotation.JsonProperty("sunEnabled")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private java.lang.Boolean sunEnabled;
    @com.fasterxml.jackson.annotation.JsonProperty("skyBox")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private WorldInfoSettingsDTOSkyBoxDTO skyBox;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private java.util.List<WorldInfoSettingsDTOMoonsDTO> moons;
    @com.fasterxml.jackson.annotation.JsonProperty("horizonGradient")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private WorldInfoSettingsDTOHorizonGradientDTO horizonGradient;
    @com.fasterxml.jackson.annotation.JsonProperty("environmentScripts")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private java.util.List<WorldInfoSettingsDTOEnvironmentScriptsDTO> environmentScripts;
    @com.fasterxml.jackson.annotation.JsonProperty("worldTime")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private WorldInfoSettingsDTOWorldTimeDTO worldTime;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private WorldInfoSettingsDTOShadowsDTO shadows;
}
