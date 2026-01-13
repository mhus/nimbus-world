/*
 * Source TS: UserMessage.ts
 * Original TS: 'interface PlayerTeleportData'
 */
package de.mhus.nimbus.generated.network.messages;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class PlayerTeleportData {
    private de.mhus.nimbus.generated.types.Rotation r;
    private PlayerTeleportDataPDTO p;
}
