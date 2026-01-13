/*
 * Source TS: ScrawlStep.ts
 * Original TS: 'interface StepLodSwitch'
 */
package de.mhus.nimbus.generated.scrawl;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class StepLodSwitch {
    private String kind;
    private java.util.Map<String, ScrawlStep> levels;
}
