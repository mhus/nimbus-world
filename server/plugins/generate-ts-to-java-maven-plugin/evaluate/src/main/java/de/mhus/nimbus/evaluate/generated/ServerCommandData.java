/*
 * Source TS: ServerCommandMessage.ts
 * Original TS: 'interface ServerCommandData'
 */
package de.mhus.nimbus.evaluate.generated;

@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class ServerCommandData {
    private String cmd;
    private java.util.List<String> args;
    private java.lang.Boolean oneway;
    private java.util.List<SingleServerCommandData> cmds;
    private java.lang.Boolean parallel;
}
