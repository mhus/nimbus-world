/*
 * Source TS: World.ts
 * Original TS: 'interface WorldInfoSettingsDTOWorldTimeDTO'
 */
package de.mhus.nimbus.generated.types;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class WorldInfoSettingsDTOWorldTimeDTO {
    @com.fasterxml.jackson.annotation.JsonProperty("minuteScaling")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private Integer minuteScaling;
    @com.fasterxml.jackson.annotation.JsonProperty("minutesPerHour")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private Integer minutesPerHour;
    @com.fasterxml.jackson.annotation.JsonProperty("hoursPerDay")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private Integer hoursPerDay;
    @com.fasterxml.jackson.annotation.JsonProperty("daysPerMonth")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private Integer daysPerMonth;
    @com.fasterxml.jackson.annotation.JsonProperty("monthsPerYear")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private Integer monthsPerYear;
    @com.fasterxml.jackson.annotation.JsonProperty("currentEra")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private Integer currentEra;
    @com.fasterxml.jackson.annotation.JsonProperty("linuxEpocheDeltaMinutes")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private Long linuxEpocheDeltaMinutes;
    @com.fasterxml.jackson.annotation.JsonProperty("daySections")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private WorldInfoSettingsDTOWorldTimeDTODaySectionsDTO daySections;
    @com.fasterxml.jackson.annotation.JsonProperty("celestialBodies")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private WorldInfoSettingsDTOWorldTimeDTOCelestialBodiesDTO celestialBodies;
}
