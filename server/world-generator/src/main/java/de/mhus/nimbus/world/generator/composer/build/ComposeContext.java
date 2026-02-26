package de.mhus.nimbus.world.generator.composer.build;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.world.generator.composer.biome.PlacedBiome;
import de.mhus.nimbus.world.generator.composer.point.Point;
import de.mhus.nimbus.world.generator.composer.town.StructuresIndex;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import de.mhus.nimbus.world.shared.world.WWorld;
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
     * The world this composition belongs to.
     */
    private WWorld world;

    /**
     * Index of available building/structure definitions.
     * Loaded from the region collection's 'structures' layer.
     */
    private StructuresIndex structuresIndex;

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

    public int getHexGridSize() {
        return world.getPublicData().getHexGridSize();
    }
}
