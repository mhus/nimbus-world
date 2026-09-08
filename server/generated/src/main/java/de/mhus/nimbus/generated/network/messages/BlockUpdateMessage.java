/*
 * Source TS: BlockMessage.ts
 * Original TS: 'type BlockUpdateMessage'
 */
package de.mhus.nimbus.generated.network.messages;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class BlockUpdateMessage {
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private de.mhus.nimbus.generated.network.BaseMessage<java.util.List<de.mhus.nimbus.generated.types.Block>> value;
}
