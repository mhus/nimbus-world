/*
 * Source TS: BlockMessage.ts
 * Original TS: 'type BlockStatusUpdateMessage'
 */
package de.mhus.nimbus.generated.network.messages;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
public class BlockStatusUpdateMessage {
    private String value;
}
