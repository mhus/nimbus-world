/*
 * Source TS: Block.ts
 * Original TS: 'type Offsets'
 */
package de.mhus.nimbus.generated.types;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Offsets {
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private java.util.List<java.lang.Double> value;
}
