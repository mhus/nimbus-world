/*
 * Source TS: RegionCharacterResponse.ts
 * Original TS: 'interface RegionCharacterResponse'
 */
package de.mhus.nimbus.generated.dto;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class RegionCharacterResponse {
    private String id;
    @com.fasterxml.jackson.annotation.JsonProperty("userId")
    private String userId;
    @com.fasterxml.jackson.annotation.JsonProperty("regionId")
    private String regionId;
    private String name;
    private String display;
    private java.util.Map<String, de.mhus.nimbus.generated.types.RegionItemInfo> backpack;
    private java.util.Map<Integer, de.mhus.nimbus.generated.types.RegionItemInfo> wearing;
    private java.util.Map<String, Integer> skills;
}
