package de.mhus.nimbus.world.generator.composer.pathfinding;

/**
 * Type classification for hex nodes in village pathfinding graph.
 */
public enum HexNodeType {
    /**
     * Empty hex cell - walkable, no special properties
     */
    EMPTY,

    /**
     * Occupied by building or other structure - not walkable
     */
    OCCUPIED,

    /**
     * Connection point - important nodes that must be connected by streets
     */
    CONNECTION_POINT,

    /**
     * Edge node at district boundary - enables crossing between districts
     */
    EDGE
}
