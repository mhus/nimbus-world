/*
 * Source TS: BlockModifier.ts
 * Original TS: 'enum BlockEffect'
 */
package de.mhus.nimbus.generated.types;

public enum BlockEffect implements de.mhus.nimbus.types.TsEnum {
    NONE(0),
    WIND(2);

    @lombok.Getter
    private final int tsIndex;
    private final String tsString;
    BlockEffect(int tsIndex) { this.tsIndex = tsIndex; this.tsString = String.valueOf(tsIndex); }
    public String tsString() { return this.tsString; }
}
