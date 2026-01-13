/*
 * Source TS: World.ts
 * Original TS: 'interface WorldInfoSettingsDTOWorldTimeDTOCelestialBodiesDTO'
 */
package de.mhus.nimbus.generated.types;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class WorldInfoSettingsDTOWorldTimeDTOCelestialBodiesDTO {
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private java.lang.Boolean enabled;
    @com.fasterxml.jackson.annotation.JsonProperty("updateIntervalSeconds")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private Integer updateIntervalSeconds;
    @com.fasterxml.jackson.annotation.JsonProperty("activeMoons")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private Byte activeMoons;
    @com.fasterxml.jackson.annotation.JsonProperty("sunRotationHours")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private Integer sunRotationHours;
    @com.fasterxml.jackson.annotation.JsonProperty("moon0RotationHours")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private Integer moon0RotationHours;
    @com.fasterxml.jackson.annotation.JsonProperty("moon1RotationHours")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private Integer moon1RotationHours;
    @com.fasterxml.jackson.annotation.JsonProperty("moon2RotationHours")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private Integer moon2RotationHours;
}
