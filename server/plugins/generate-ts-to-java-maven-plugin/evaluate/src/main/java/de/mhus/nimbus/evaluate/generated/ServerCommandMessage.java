/*
 * Source TS: ServerCommandMessage.ts
 * Original TS: 'type ServerCommandMessage'
 */
package de.mhus.nimbus.evaluate.generated;

@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class ServerCommandMessage {
    private RequestMessage<ServerCommandData> value;
}
