/*
 * Source TS: PingMessage.ts
 * Original TS: 'interface PongData'
 */
package de.mhus.nimbus.generated.network.messages;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class PongData {
    @com.fasterxml.jackson.annotation.JsonProperty("cTs")
    private long cTs;
    @com.fasterxml.jackson.annotation.JsonProperty("sTs")
    private long sTs;
}
