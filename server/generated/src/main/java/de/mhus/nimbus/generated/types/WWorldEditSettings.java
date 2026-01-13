/*
 * Source TS: EditSettings.ts
 * Original TS: 'interface WWorldEditSettings'
 */
package de.mhus.nimbus.generated.types;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class WWorldEditSettings {
    @com.fasterxml.jackson.annotation.JsonProperty("worldId")
    private String worldId;
    @com.fasterxml.jackson.annotation.JsonProperty("userId")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private String userId;
    private java.util.List<PaletteBlockDefinition> palette;
    @com.fasterxml.jackson.annotation.JsonProperty("lastModified")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private java.lang.Double lastModified;
}
