/*
 * Source TS: HexData.ts
 * Original TS: 'interface HexGrid'
 */
package de.mhus.nimbus.generated.types;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class HexGrid {
    private HexVector2 position;
    @com.fasterxml.jackson.annotation.JsonProperty("entryPoint")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private Area entryPoint;
    private String name;
    private String description;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String icon;
    @com.fasterxml.jackson.annotation.JsonProperty("splashScreen")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String splashScreen;
    @com.fasterxml.jackson.annotation.JsonProperty("splashScreenAudio")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String splashScreenAudio;
}
