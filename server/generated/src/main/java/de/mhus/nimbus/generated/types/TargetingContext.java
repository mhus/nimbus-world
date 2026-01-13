/*
 * Source TS: TargetingTypes.ts
 * Original TS: 'interface TargetingContext'
 */
package de.mhus.nimbus.generated.types;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class TargetingContext {
    private TargetingMode mode;
    private ResolvedTarget target;
}
