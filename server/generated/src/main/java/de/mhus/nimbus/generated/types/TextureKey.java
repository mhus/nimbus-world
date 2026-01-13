/*
 * Source TS: BlockModifier.ts
 * Original TS: 'enum TextureKey'
 */
package de.mhus.nimbus.generated.types;

public enum TextureKey implements de.mhus.nimbus.types.TsEnum {
    ALL(0),
    TOP(1),
    BOTTOM(2),
    LEFT(3),
    RIGHT(4),
    FRONT(5),
    BACK(6),
    SIDE(7),
    DIFFUSE(8),
    DISTORTION(9),
    OPACITY(10),
    WALL(11),
    INSIDE_ALL(20),
    INSIDE_TOP(21),
    INSIDE_BOTTOM(22),
    INSIDE_LEFT(23),
    INSIDE_RIGHT(24),
    INSIDE_FRONT(25),
    INSIDE_BACK(26),
    INSIDE_SIDE(27);

    @lombok.Getter
    private final int tsIndex;
    private final String tsString;
    TextureKey(int tsIndex) { this.tsIndex = tsIndex; this.tsString = String.valueOf(tsIndex); }
    public String tsString() { return this.tsString; }
}
