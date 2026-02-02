package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.generated.types.HexVector2;

/**
 *
 * @param q Coordinate q of the inner hexagon
 * @param r Coordinate r of the inner hexagon
 * @param divider Optional or 0 for default divider 4
 * @param size Calculated size (not radius) of the inner hexagon
 */
public record HexLocalPosition (HexVector2 position, int divider, int size) {
}
