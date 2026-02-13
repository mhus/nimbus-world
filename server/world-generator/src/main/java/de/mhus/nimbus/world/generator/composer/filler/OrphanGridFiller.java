package de.mhus.nimbus.world.generator.composer.filler;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.shared.utils.TypeUtil;
import de.mhus.nimbus.world.generator.composer.biome.Biome;
import de.mhus.nimbus.world.generator.composer.biome.BiomePlacementResult;
import de.mhus.nimbus.world.generator.composer.biome.BiomeType;
import de.mhus.nimbus.world.generator.composer.biome.PlacedBiome;
import de.mhus.nimbus.world.generator.composer.build.HexComposition;
import de.mhus.nimbus.world.generator.composer.feature.FeatureHexGrid;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Fills "orphan" grids that are used by features (rivers, points, villages)
 * but don't belong to any biome yet.
 *
 * These grids need a biome assignment to get proper terrain generation parameters
 * (especially g_builder).
 *
 * Algorithm:
 * 1. Collect all grids from central registry
 * 2. Find grids without g_builder parameter (orphans)
 * 3. Assign Coast (if at least one land neighbor) or Ocean (otherwise)
 */
@Slf4j
public class OrphanGridFiller {

    /**
     * Fills orphan grids with Coast or Ocean biome assignments.
     *
     * @param composition The composition with all features
     * @param placementResult Result from BiomeComposer with placed biomes
     * @return Number of orphan grids filled
     */
    public int fill(HexComposition composition, BiomePlacementResult placementResult) {
        log.debug("Starting OrphanGridFiller");

        // Build map: coord -> biome
        Map<String, Biome> gridToBiomeMap = buildGridToBiomeMap(placementResult);

        // Collect all grids from central registry
        Set<HexVector2> allFeatureGrids = collectAllFeatureGrids(composition);
        log.debug("Found {} grids in central registry", allFeatureGrids.size());

        // Find grids without g_builder parameter (orphans)
        List<HexVector2> orphanGrids = new ArrayList<>();
        for (HexVector2 coord : allFeatureGrids) {
            List<FeatureHexGrid> existingGrids = findAllFeatureHexGrids(coord, composition);
            boolean hasOrphan = false;
            for (FeatureHexGrid grid : existingGrids) {
                Map<String, String> params = grid.getParameters();
                if (params == null || !params.containsKey("g_builder")) {
                    hasOrphan = true;
                    break;
                }
            }
            if (hasOrphan) {
                orphanGrids.add(coord);
                log.trace("Grid {} has FeatureHexGrid without g_builder parameter", coordKey(coord));
            }
        }

        if (orphanGrids.isEmpty()) {
            log.debug("No orphan grids found (all grids have g_builder)");
            return 0;
        }

        log.info("Found {} orphan grids without g_builder parameter", orphanGrids.size());

        // Classify orphans: coast (has land neighbor) vs ocean (no land neighbor)
        List<HexVector2> coastOrphans = new ArrayList<>();
        List<HexVector2> oceanOrphans = new ArrayList<>();

        for (HexVector2 coord : orphanGrids) {
            if (hasLandNeighbor(coord, gridToBiomeMap)) {
                coastOrphans.add(coord);
            } else {
                oceanOrphans.add(coord);
            }
        }

        int filled = 0;

        // Create Coast filler biome for coast orphans
        if (!coastOrphans.isEmpty()) {
            filled += assignOrphansToFillerBiome(BiomeType.COAST, "orphan-coast",
                coastOrphans, composition, placementResult);
        }

        // Create Ocean filler biome for ocean orphans
        if (!oceanOrphans.isEmpty()) {
            filled += assignOrphansToFillerBiome(BiomeType.OCEAN, "orphan-ocean",
                oceanOrphans, composition, placementResult);
        }

        log.info("OrphanGridFiller: filled {} orphan grids (coast: {}, ocean: {})",
            filled, coastOrphans.size(), oceanOrphans.size());
        return filled;
    }

    /**
     * Checks if a grid has at least one land neighbor (not OCEAN, not COAST).
     */
    private boolean hasLandNeighbor(HexVector2 coord, Map<String, Biome> gridToBiomeMap) {
        for (HexVector2 neighbor : getNeighbors(coord)) {
            Biome biome = gridToBiomeMap.get(coordKey(neighbor));
            if (biome != null && biome.getType() != null
                    && biome.getType() != BiomeType.OCEAN
                    && biome.getType() != BiomeType.COAST) {
                return true;
            }
        }
        return false;
    }

    /**
     * Creates a filler biome and assigns orphan grids to it.
     * Updates existing FeatureHexGrids in the central registry with biome parameters.
     */
    private int assignOrphansToFillerBiome(BiomeType type, String name,
                                           List<HexVector2> coords,
                                           HexComposition composition,
                                           BiomePlacementResult placementResult) {
        // Create filler biome
        Biome biome = new Biome();
        biome.setName(name);
        biome.setType(type);
        if (biome.getParameters() == null) {
            biome.setParameters(new HashMap<>());
        }
        biome.getParameters().put("filler", "true");
        biome.getParameters().put("fillerType", "orphan");
        biome.applyDefaults();

        // Create PlacedBiome
        PlacedBiome placedBiome = new PlacedBiome();
        placedBiome.setBiome(biome);
        placedBiome.setCenter(coords.get(0));
        placedBiome.setCoordinates(new ArrayList<>(coords));
        placedBiome.setActualSize(coords.size());
        placementResult.getPlacedBiomes().add(placedBiome);

        // Update existing FeatureHexGrids in central registry
        int updated = 0;
        for (HexVector2 coord : coords) {
            List<FeatureHexGrid> existingGrids = findAllFeatureHexGrids(coord, composition);
            for (FeatureHexGrid grid : existingGrids) {
                Map<String, String> params = grid.getParameters();
                if (params == null || !params.containsKey("g_builder")) {
                    updateFeatureHexGridParameters(grid, biome);
                    updated++;
                }
            }
        }

        log.debug("Created {} filler biome '{}' for {} grids ({} FeatureHexGrids updated)",
            type, name, coords.size(), updated);
        return coords.size();
    }

    /**
     * Finds ALL existing FeatureHexGrids for this coordinate in central registry.
     */
    private List<FeatureHexGrid> findAllFeatureHexGrids(HexVector2 coord, HexComposition composition) {
        List<FeatureHexGrid> result = new ArrayList<>();

        Map<String, FeatureHexGrid> registry = composition.getFeatureHexGridRegistry();
        if (registry == null || registry.isEmpty()) {
            return result;
        }

        for (FeatureHexGrid hexGrid : registry.values()) {
            if (hexGrid.getCoordinate() != null &&
                hexGrid.getCoordinate().getQ() == coord.getQ() &&
                hexGrid.getCoordinate().getR() == coord.getR()) {
                result.add(hexGrid);
            }
        }

        return result;
    }

    /**
     * Updates parameters of an existing FeatureHexGrid with biome parameters.
     */
    private void updateFeatureHexGridParameters(FeatureHexGrid featureHexGrid, Biome biome) {
        Map<String, String> parameters = featureHexGrid.getParameters();
        if (parameters == null) {
            parameters = new HashMap<>();
            featureHexGrid.setParameters(parameters);
        }

        // Copy biome parameters (includes g_builder from applyDefaults)
        if (biome.getParameters() != null) {
            parameters.putAll(biome.getParameters());
        }

        // Add biome identification
        parameters.put("biome", biome.getType() != null ? biome.getType().getBuilderName() : "ocean");
        parameters.put("biomeName", biome.getName());
        parameters.put("biomeType", biome.getType() != null ? biome.getType().name() : "OCEAN");

        log.trace("Updated FeatureHexGrid parameters: {}", parameters);
    }

    /**
     * Builds a map from grid coordinate to biome.
     */
    private Map<String, Biome> buildGridToBiomeMap(BiomePlacementResult placementResult) {
        Map<String, Biome> map = new HashMap<>();

        for (PlacedBiome placed : placementResult.getPlacedBiomes()) {
            for (HexVector2 coord : placed.getCoordinates()) {
                map.put(coordKey(coord), placed.getBiome());
            }
        }

        return map;
    }

    /**
     * Collects all grids from central FeatureHexGrid registry.
     */
    private Set<HexVector2> collectAllFeatureGrids(HexComposition composition) {
        Set<HexVector2> allGrids = new HashSet<>();

        Map<String, FeatureHexGrid> registry = composition.getFeatureHexGridRegistry();
        if (registry == null || registry.isEmpty()) {
            return allGrids;
        }

        for (FeatureHexGrid hexGrid : registry.values()) {
            if (hexGrid.getCoordinate() != null) {
                allGrids.add(hexGrid.getCoordinate());
            }
        }

        return allGrids;
    }

    /**
     * Gets all 6 neighbors of a hex coordinate.
     */
    private List<HexVector2> getNeighbors(HexVector2 coord) {
        int q = coord.getQ();
        int r = coord.getR();

        return List.of(
            HexVector2.builder().q(q + 1).r(r).build(),
            HexVector2.builder().q(q - 1).r(r).build(),
            HexVector2.builder().q(q).r(r + 1).build(),
            HexVector2.builder().q(q).r(r - 1).build(),
            HexVector2.builder().q(q + 1).r(r - 1).build(),
            HexVector2.builder().q(q - 1).r(r + 1).build()
        );
    }

    /**
     * Creates a string key for a coordinate.
     */
    private String coordKey(HexVector2 coord) {
        return TypeUtil.toStringHexCoord(coord);
    }
}
