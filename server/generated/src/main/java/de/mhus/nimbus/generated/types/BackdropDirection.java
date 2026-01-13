/*
 * Source TS: Backdrop.ts
 * Original TS: 'enum BackdropDirection'
 */
package de.mhus.nimbus.generated.types;

public enum BackdropDirection implements de.mhus.nimbus.types.TsEnum {
    NORTH("north"),
    EAST("east"),
    SOUTH("south"),
    WEST("west");

    @lombok.Getter
    private final String tsIndex;
    BackdropDirection(String tsIndex) { this.tsIndex = tsIndex; }
    public String tsString() { return this.tsIndex; }
}
