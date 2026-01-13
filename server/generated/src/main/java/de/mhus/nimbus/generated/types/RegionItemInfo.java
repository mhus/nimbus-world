/*
 * Source TS: RegionItemInfo.ts
 * Original TS: 'interface RegionItemInfo'
 */
package de.mhus.nimbus.generated.types;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class RegionItemInfo {
    @com.fasterxml.jackson.annotation.JsonProperty("itemId")
    private String itemId;
    private String name;
    private String texture;
}
