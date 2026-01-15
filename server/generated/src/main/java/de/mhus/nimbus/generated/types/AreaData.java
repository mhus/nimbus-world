/*
 * Source TS: AreaData.ts
 * Original TS: 'interface AreaData'
 */
package de.mhus.nimbus.generated.types;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class AreaData {
    private Vector3Int a;
    private Vector3Int b;
    private java.util.Map<String, String> p;
}
