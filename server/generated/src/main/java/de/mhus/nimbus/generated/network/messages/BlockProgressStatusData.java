/*
 * Source TS: BlockMessage.ts
 * Original TS: 'interface BlockProgressStatusData'
 */
package de.mhus.nimbus.generated.network.messages;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class BlockProgressStatusData {
    private int cx;
    private int cz;
    private java.util.Map<String, String> s;
}
