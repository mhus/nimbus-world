package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.world.shared.util.HexLocalUtil;

/**
 *
 * @param side Side of the hexagon
 * @param numerator Numerator of the position along the side
 * @param denominator Denominator of the position along the side, default is 4
 */
public record HexLocalEdgeVector(WHexGrid.EDGE side, int numerator, int denominator) {
    public HexLocalEdgeVector {
        if (denominator == 0) denominator = HexLocalUtil.DEFAULT_SIDE_DIVIDER;
        if (numerator < 0 || numerator > denominator) {
            throw new IllegalArgumentException("Numerator must be between 0 and denominator");
        }
    }
}
