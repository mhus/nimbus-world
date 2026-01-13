/*
 * Source TS: CommandMessage.ts
 * Original TS: 'interface CommandData'
 */
package de.mhus.nimbus.evaluate.generated;

@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class CommandData {
    private String cmd;
    private java.util.List<String> args;
    private java.lang.Boolean oneway;
}
