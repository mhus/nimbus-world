/*
 * Source TS: World.ts
 * Original TS: 'enum SeasonStatus'
 */
package de.mhus.nimbus.generated.types;

public enum SeasonStatus implements de.mhus.nimbus.types.TsEnum {
    NONE(0),
    WINTER(1),
    SPRING(2),
    SUMMER(3),
    AUTUMN(4);

    @lombok.Getter
    private final int tsIndex;
    private final String tsString;
    SeasonStatus(int tsIndex) { this.tsIndex = tsIndex; this.tsString = String.valueOf(tsIndex); }
    public String tsString() { return this.tsString; }
}
