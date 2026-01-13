/*
 * Source TS: Shape.ts
 * Original TS: 'enum Shape'
 */
package de.mhus.nimbus.generated.types;

public enum Shape implements de.mhus.nimbus.types.TsEnum {
    INVISIBLE(0),
    CUBE(1),
    CROSS(2),
    HASH(3),
    MODEL(4),
    GLASS(5),
    GLASS_FLAT(6),
    FLAT(7),
    SPHERE(8),
    CYLINDER(9),
    ROUND_CUBE(10),
    STEPS(11),
    STAIR(12),
    BILLBOARD(13),
    SPRITE(14),
    FLAME(15),
    OCEAN(16),
    OCEAN_COAST(17),
    OCEAN_MAELSTROM(18),
    RIVER(19),
    RIVER_WATERFALL(20),
    RIVER_WATERFALL_WHIRLPOOL(21),
    WATER(22),
    LAVA(23),
    FOG(24),
    THIN_INSTANCES(25),
    WALL(26),
    FLIPBOX(27),
    ITEM(28),
    BUSH(29);

    @lombok.Getter
    private final int tsIndex;
    private final String tsString;
    Shape(int tsIndex) { this.tsIndex = tsIndex; this.tsString = String.valueOf(tsIndex); }
    public String tsString() { return this.tsString; }
}
