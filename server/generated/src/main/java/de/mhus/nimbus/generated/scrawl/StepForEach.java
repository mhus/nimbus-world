/*
 * Source TS: ScrawlStep.ts
 * Original TS: 'interface StepForEach'
 */
package de.mhus.nimbus.generated.scrawl;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class StepForEach {
    private String kind;
    private String collection;
    @com.fasterxml.jackson.annotation.JsonProperty("itemVar")
    private String itemVar;
    private ScrawlStep step;
}
