package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Context for composing features (Biomes, Points, Flows).
 * Holds all necessary data for composition phase.
 */
@Data
@Builder
public class ComposeContext {

    /**
     * The composition being processed.
     */
    private HexComposition composition;

    /**
     * All placed biomes with their coordinates.
     */
    private List<PlacedBiome> placedBiomes;

    /**
     * Map: biome name -> PlacedBiome
     */
    private Map<String, PlacedBiome> biomeMap;

    /**
     * Map: biome name -> center coordinate
     */
    private Map<String, HexVector2> biomeCenterMap;

    /**
     * Map: coordinate -> biome name
     */
    private Map<String, String> coordinateToBiomeMap;

    /**
     * All generated hex grids.
     */
    private List<WHexGrid> hexGrids;

    /**
     * Map: coordinate string -> WHexGrid
     */
    private Map<String, WHexGrid> hexGridMap;

    /**
     * All points from all biomes and composition.
     */
    private List<Point> allPoints;

    /**
     * Map: point featureId -> Point
     */
    private Map<String, Point> pointMap;

    /**
     * Hex grid size (default 512).
     */
    private int hexGridSize;
}
