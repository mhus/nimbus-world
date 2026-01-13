/*
 * Source TS: BlockMessage.ts
 * Original TS: 'interface BlockInteractionData'
 */
package de.mhus.nimbus.evaluate.generated;

@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class BlockInteractionData {
    private double x;
    private double y;
    private double z;
    private String id;
    @com.fasterxml.jackson.annotation.JsonProperty("gId")
    private String gId;
    private String ac;
    private java.util.Map<String, Object> pa;
}
