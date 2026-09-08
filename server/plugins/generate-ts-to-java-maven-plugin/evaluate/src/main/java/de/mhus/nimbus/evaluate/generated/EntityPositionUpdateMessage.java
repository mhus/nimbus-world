/*
 * Source TS: EntityMessage.ts
 * Original TS: 'type EntityPositionUpdateMessage'
 */
package de.mhus.nimbus.evaluate.generated;

@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class EntityPositionUpdateMessage {
    private BaseMessage<java.util.List<EntityPositionUpdateData>> value;
}
