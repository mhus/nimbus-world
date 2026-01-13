/*
 * Source TS: ChunkMessage.ts
 * Original TS: 'interface ChunkDataTransferObject'
 */
package de.mhus.nimbus.generated.network.messages;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class ChunkDataTransferObject {
    private int cx;
    private int cz;
    private java.util.List<de.mhus.nimbus.generated.types.Block> b;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private java.util.List<de.mhus.nimbus.generated.types.ItemBlockRef> i;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private int[][] h;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private java.util.List<de.mhus.nimbus.generated.types.AreaData> a;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private byte[] c;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    private ChunkDataTransferObjectBackdropDTO backdrop;
}
