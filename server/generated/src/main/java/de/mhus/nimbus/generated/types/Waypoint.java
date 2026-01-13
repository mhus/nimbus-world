/*
 * Source TS: EntityData.ts
 * Original TS: 'interface Waypoint'
 */
package de.mhus.nimbus.generated.types;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class Waypoint {
    private long timestamp;
    private Vector3 target;
    private Rotation rotation;
    private ENTITY_POSES pose;
}
