/*
 * Source TS: EntityMessage.ts
 * Original TS: 'interface EntityPositionUpdateData'
 */
package de.mhus.nimbus.evaluate.generated;

@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class EntityPositionUpdateData {
    private String pl;
    private Vector3 p;
    private Rotation r;
    private Vector3 v;
    private java.lang.Double po;
    private double ts;
    private EntityPositionUpdateDataTaDTO ta;
}
