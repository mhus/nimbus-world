/*
 * Source TS: PingMessage.ts
 * Original TS: 'interface PongMessage'
 */
package de.mhus.nimbus.generated.network.messages;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class PongMessage extends de.mhus.nimbus.generated.network.BaseMessage {
    private String r;
}
