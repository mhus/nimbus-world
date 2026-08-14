/*
 * Source TS: EngineConfiguration.ts
 * Original TS: 'enum WEARABLE_SLOT'
 */
package de.mhus.nimbus.generated.configs;

public enum WEARABLE_SLOT implements de.mhus.nimbus.types.TsEnum {
    HEAD("HEAD"),
    BODY("BODY"),
    LEGS("LEGS"),
    FEET("FEET"),
    NECK("NECK"),
    LEFT_RING("LEFT_RING"),
    RIGHT_RING("RIGHT_RING"),
    LEFT_HAND_1("LEFT_HAND_1"),
    RIGHT_HAND_1("RIGHT_HAND_1"),
    LEFT_HAND_2("LEFT_HAND_2"),
    RIGHT_HAND_2("RIGHT_HAND_2"),
    ARMS("ARMS");

    @lombok.Getter
    private final String tsIndex;
    WEARABLE_SLOT(String tsIndex) { this.tsIndex = tsIndex; }
    public String tsString() { return this.tsIndex; }
}
