package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.generated.types.HexVector2;

/**
 * Flat hexagon local position inside the hexagon grid (top-pointy).
 * @param q Coordinate q of the inner hexagon
 * @param r Coordinate r of the inner hexagon
 * @param divider Optional or 0 for default divider 4
 * @param size Calculated size (not radius) of the inner hexagon
 */
public record HexLocalPosition (HexVector2 position, int divider, int size) {

    @SuppressWarnings("NullableProblems")
    @Override
    public String toString() {
        return "<" + position.getQ() + ";" + position.getR() + "/" + divider + "#" + size + ">";
    }
}
