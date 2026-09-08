/*
 * Source TS: CommandMessage.ts
 * Original TS: 'type CommandMessage'
 */
package de.mhus.nimbus.evaluate.generated;

@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class CommandMessage {
    private BaseMessage<CommandData> value;
}
