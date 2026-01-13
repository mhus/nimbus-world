/*
 * Source TS: EntityMessage.ts
 * Original TS: 'interface EntityInteractionData'
 */
package de.mhus.nimbus.evaluate.generated;

@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class EntityInteractionData {
    @com.fasterxml.jackson.annotation.JsonProperty("entityId")
    private String entityId;
    private double ts;
    private String ac;
    private java.util.Map<String, Object> pa;
}
