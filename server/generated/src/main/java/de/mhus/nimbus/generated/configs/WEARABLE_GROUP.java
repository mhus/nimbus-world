/*
 * Source TS: EngineConfiguration.ts
 * Original TS: 'enum WEARABLE_GROUP'
 */
package de.mhus.nimbus.generated.configs;

public enum WEARABLE_GROUP implements de.mhus.nimbus.types.TsEnum {
    HEAD(0),
    BODY(1),
    LEGS(2),
    FEET(3),
    NECK(4),
    RING(5),
    HAND(6),
    ARMS(7);

    @lombok.Getter
    private final int tsIndex;
    private final String tsString;
    WEARABLE_GROUP(int tsIndex) { this.tsIndex = tsIndex; this.tsString = String.valueOf(tsIndex); }
    public String tsString() { return this.tsString; }
}
