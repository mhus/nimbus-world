/*
 * Source TS: BlockType.ts
 * Original TS: 'enum BlockTypeType'
 */
package de.mhus.nimbus.generated.types;

public enum BlockTypeType implements de.mhus.nimbus.types.TsEnum {
    AIR(0),
    GROUND(1),
    WATER(2),
    PLANT(3),
    PLANT_PART(4),
    STRUCTURE(5),
    DECORATION(6),
    UTILITY(7),
    LAVA(8),
    WINDOW(9),
    DOOR(10),
    WALL(11),
    ROOF(12),
    PATH(13),
    FENCE(14),
    STAIRS(15),
    RAMP(16),
    BRIDGE(17),
    LIGHT(18),
    OTHER(99);

    @lombok.Getter
    private final int tsIndex;
    private final String tsString;
    BlockTypeType(int tsIndex) { this.tsIndex = tsIndex; this.tsString = String.valueOf(tsIndex); }
    public String tsString() { return this.tsString; }
}
