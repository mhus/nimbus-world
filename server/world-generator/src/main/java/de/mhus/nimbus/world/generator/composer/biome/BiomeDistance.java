package de.mhus.nimbus.world.generator.composer.biome;

/**
 * Distance from biome center when placing a point.
 *
 * CENTER = 0 hexes (in the center hex itself, but with local offset)
 * NEAR = 1 hex away from center
 * NORMAL = 2 hexes away from center
 * FAR = 3 hexes away from center
 * VERY_FAR = 4 hexes away from center
 */
public enum BiomeDistance {
    CENTER(0),
    NEAR(1),
    NORMAL(2),
    FAR(3),
    VERY_FAR(4);

    private final int hexes;

    BiomeDistance(int hexes) {
        this.hexes = hexes;
    }

    public int getHexes() {
        return hexes;
    }
}
