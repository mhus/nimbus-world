/*
 * Source TS: ServerCommandMessage.ts
 * Original TS: 'type ServerCommandResultMessage'
 */
package de.mhus.nimbus.evaluate.generated;

@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class ServerCommandResultMessage {
    private ResponseMessage<ServerCommandResultData> value;
}
