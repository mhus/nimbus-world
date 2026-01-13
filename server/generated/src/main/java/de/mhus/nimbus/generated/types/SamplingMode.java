/*
 * Source TS: BlockModifier.ts
 * Original TS: 'enum SamplingMode'
 */
package de.mhus.nimbus.generated.types;

public enum SamplingMode implements de.mhus.nimbus.types.TsEnum {
    NEAREST(0),
    LINEAR(1),
    MIPMAP(2);

    @lombok.Getter
    private final int tsIndex;
    private final String tsString;
    SamplingMode(int tsIndex) { this.tsIndex = tsIndex; this.tsString = String.valueOf(tsIndex); }
    public String tsString() { return this.tsString; }
}
