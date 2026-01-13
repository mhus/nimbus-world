/*
 * Source TS: ServerCommandMessage.ts
 * Original TS: 'interface SingleServerCommandData'
 */
package de.mhus.nimbus.evaluate.generated;

@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class SingleServerCommandData {
    private String cmd;
    private java.util.List<String> args;
}
