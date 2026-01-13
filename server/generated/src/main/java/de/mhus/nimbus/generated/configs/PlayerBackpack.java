/*
 * Source TS: EngineConfiguration.ts
 * Original TS: 'interface PlayerBackpack'
 */
package de.mhus.nimbus.generated.configs;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@org.springframework.aot.hint.annotation.Reflective
@lombok.Data
@lombok.experimental.SuperBuilder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class PlayerBackpack {
    @com.fasterxml.jackson.annotation.JsonProperty("itemIds")
    private java.util.Map<String, String> itemIds;
    @com.fasterxml.jackson.annotation.JsonProperty("wearingItemIds")
    private java.util.Map<WEARABLE_SLOT, String> wearingItemIds;
}
