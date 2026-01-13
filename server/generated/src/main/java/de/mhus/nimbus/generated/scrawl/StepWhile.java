/*
 * Source TS: ScrawlStep.ts
 * Original TS: 'interface StepWhile'
 */
package de.mhus.nimbus.generated.scrawl;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class StepWhile {
    private String kind;
    @com.fasterxml.jackson.annotation.JsonProperty("taskId")
    private String taskId;
    private ScrawlStep step;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private long timeout;
}
