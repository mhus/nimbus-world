/*
 * Source TS: PingMessage.ts
 * Original TS: 'interface PingData'
 */
package de.mhus.nimbus.generated.network.messages;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class PingData {
    @com.fasterxml.jackson.annotation.JsonProperty("cTs")
    private long cTs;
}
