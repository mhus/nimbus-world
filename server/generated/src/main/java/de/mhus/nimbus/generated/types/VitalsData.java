/*
 * Source TS: VitalsData.ts
 * Original TS: 'interface VitalsData'
 */
package de.mhus.nimbus.generated.types;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class VitalsData {
    private String type;
    private int current;
    private int max;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private int extended;
    @com.fasterxml.jackson.annotation.JsonProperty("extendExpiry")
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private long extendExpiry;
    @com.fasterxml.jackson.annotation.JsonProperty("regenRate")
    private double regenRate;
    @com.fasterxml.jackson.annotation.JsonProperty("degenRate")
    private double degenRate;
    private String color;
    private String name;
    private int order;
}
