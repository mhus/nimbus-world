/*
 * Source TS: ItemRef.ts
 * Original TS: 'interface ItemRef'
 */
package de.mhus.nimbus.generated.types;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class ItemRef {
    @com.fasterxml.jackson.annotation.JsonProperty("itemId")
    private String itemId;
    private String texture;
    private String name;
    private int amount;
}
