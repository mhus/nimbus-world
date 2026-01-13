/*
 * Source TS: ScrawlStep.ts
 * Original TS: 'interface StepIf'
 */
package de.mhus.nimbus.generated.scrawl;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class StepIf {
    private String kind;
    private ScrawlCondition cond;
    private ScrawlStep then;
}
