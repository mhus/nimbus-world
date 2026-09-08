/*
 * Source TS: CommandMessage.ts
 * Original TS: 'type CommandResultMessage'
 */
package de.mhus.nimbus.evaluate.generated;

@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class CommandResultMessage {
    private BaseMessage<CommandResultData> value;
}
