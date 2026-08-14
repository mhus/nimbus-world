/*
 * Source TS: EngineConfiguration.ts
 * Original TS: 'enum WEARABLE_GROUP'
 */
package de.mhus.nimbus.generated.configs;

public enum WEARABLE_GROUP implements de.mhus.nimbus.types.TsEnum {
    HEAD("HEAD"),
    BODY("BODY"),
    LEGS("LEGS"),
    FEET("FEET"),
    NECK("NECK"),
    RING("RING"),
    HAND("HAND"),
    ARMS("ARMS");

    @lombok.Getter
    private final String tsIndex;
    WEARABLE_GROUP(String tsIndex) { this.tsIndex = tsIndex; }
    public String tsString() { return this.tsIndex; }
}
