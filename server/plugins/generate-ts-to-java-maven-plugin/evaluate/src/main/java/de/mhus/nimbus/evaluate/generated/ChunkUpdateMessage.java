/*
 * Source TS: ChunkMessage.ts
 * Original TS: 'type ChunkUpdateMessage'
 */
package de.mhus.nimbus.evaluate.generated;

@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class ChunkUpdateMessage {
    private BaseMessage<java.util.List<ChunkDataTransferObject>> value;
}
