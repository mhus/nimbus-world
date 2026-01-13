/*
 * Source TS: BlockModifier.ts
 * Original TS: 'enum WrapMode'
 */
package de.mhus.nimbus.generated.types;

public enum WrapMode implements de.mhus.nimbus.types.TsEnum {
    CLAMP(0),
    REPEAT(1),
    MIRROR(2);

    @lombok.Getter
    private final int tsIndex;
    private final String tsString;
    WrapMode(int tsIndex) { this.tsIndex = tsIndex; this.tsString = String.valueOf(tsIndex); }
    public String tsString() { return this.tsString; }
}
