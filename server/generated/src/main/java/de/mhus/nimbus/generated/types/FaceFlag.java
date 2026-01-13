/*
 * Source TS: Block.ts
 * Original TS: 'enum FaceFlag'
 */
package de.mhus.nimbus.generated.types;

public enum FaceFlag implements de.mhus.nimbus.types.TsEnum {
    TOP("1 << 0"),
    BOTTOM("1 << 1"),
    LEFT("1 << 2"),
    RIGHT("1 << 3"),
    FRONT("1 << 4"),
    BACK("1 << 5"),
    FIXED("1 << 6");

    @lombok.Getter
    private final String tsIndex;
    FaceFlag(String tsIndex) { this.tsIndex = tsIndex; }
    public String tsString() { return this.tsIndex; }
}
