/*
 * Source TS: World.ts
 * Original TS: 'interface WorldInfoSettingsDTOWorldTimeDTODaySectionsDTO'
 */
package de.mhus.nimbus.generated.types;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class WorldInfoSettingsDTOWorldTimeDTODaySectionsDTO {
    @com.fasterxml.jackson.annotation.JsonProperty("morningStart")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private Integer morningStart;
    @com.fasterxml.jackson.annotation.JsonProperty("dayStart")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private Integer dayStart;
    @com.fasterxml.jackson.annotation.JsonProperty("eveningStart")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private Integer eveningStart;
    @com.fasterxml.jackson.annotation.JsonProperty("nightStart")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private Integer nightStart;
}
