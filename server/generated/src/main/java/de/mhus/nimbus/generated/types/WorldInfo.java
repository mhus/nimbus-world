/*
 * Source TS: World.ts
 * Original TS: 'interface WorldInfo'
 */
package de.mhus.nimbus.generated.types;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class WorldInfo {
    @com.fasterxml.jackson.annotation.JsonProperty("worldId")
    private String worldId;
    private String name;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String description;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private Vector3 start;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private Vector3 stop;
    @com.fasterxml.jackson.annotation.JsonProperty("chunkSize")
    private int chunkSize;
    @com.fasterxml.jackson.annotation.JsonProperty("hexGridSize")
    private int hexGridSize;
    @com.fasterxml.jackson.annotation.JsonProperty("worldIcon")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String worldIcon;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private byte status;
    @com.fasterxml.jackson.annotation.JsonProperty("seasonStatus")
    private byte seasonStatus;
    @com.fasterxml.jackson.annotation.JsonProperty("seasonProgress")
    private double seasonProgress;
    @com.fasterxml.jackson.annotation.JsonProperty("seasonMonths")
    private java.util.List<java.lang.Double> seasonMonths;
    @com.fasterxml.jackson.annotation.JsonProperty("createdAt")
    private String createdAt;
    @com.fasterxml.jackson.annotation.JsonProperty("updatedAt")
    private String updatedAt;
    @com.fasterxml.jackson.annotation.JsonProperty("editorUrl")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String editorUrl;
    @com.fasterxml.jackson.annotation.JsonProperty("splashScreen")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String splashScreen;
    @com.fasterxml.jackson.annotation.JsonProperty("splashScreenAudio")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String splashScreenAudio;
    private WorldInfoOwnerDTO owner;
    private WorldInfoSettingsDTO settings;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private WorldInfoLicenseDTO license;
    @com.fasterxml.jackson.annotation.JsonProperty("entryPoint")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private WorldInfoEntryPointDTO entryPoint;
}
