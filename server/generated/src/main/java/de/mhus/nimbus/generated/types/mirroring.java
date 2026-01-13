/*
 * Source TS: RegionItemInfo.ts
 * Original TS: 'interface mirroring'
 */
package de.mhus.nimbus.generated.types;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class mirroring {
    @com.fasterxml.jackson.annotation.JsonProperty("itemId")
    private String itemId;
    private String name;
    private String texture;
}
