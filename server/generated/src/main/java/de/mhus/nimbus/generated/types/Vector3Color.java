/*
 * Source TS: Vector3Color.ts
 * Original TS: 'interface Vector3Color'
 */
package de.mhus.nimbus.generated.types;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Vector3Color {
    private double x;
    private double y;
    private double z;
    private String color;
}
