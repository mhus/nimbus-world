/*
 * Source TS: WorldMessage.ts
 * Original TS: 'type WorldStatusUpdateMessage'
 */
package de.mhus.nimbus.evaluate.generated;

@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class WorldStatusUpdateMessage {
    private BaseMessage<WorldStatusUpdateData> value;
}
