/*
 * Source TS: EngineConfiguration.ts
 * Original TS: 'enum WEARABLE_SLOT'
 */
package de.mhus.nimbus.generated.configs;

public enum WEARABLE_SLOT implements de.mhus.nimbus.types.TsEnum {
    HEAD(0),
    BODY(1),
    LEGS(2),
    FEET(3),
    NECK(4),
    LEFT_RING(5),
    RIGHT_RING(6),
    LEFT_HAND_1(7),
    RIGHT_HAND_1(8),
    LEFT_HAND_2(9),
    RIGHT_HAND_2(10),
    ARMS(11);

    @lombok.Getter
    private final int tsIndex;
    private final String tsString;
    WEARABLE_SLOT(int tsIndex) { this.tsIndex = tsIndex; this.tsString = String.valueOf(tsIndex); }
    public String tsString() { return this.tsString; }
}
