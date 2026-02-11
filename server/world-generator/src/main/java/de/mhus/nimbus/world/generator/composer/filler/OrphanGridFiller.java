package de.mhus.nimbus.world.generator.composer.filler;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.shared.utils.TypeUtil;
import de.mhus.nimbus.world.generator.composer.biome.Biome;
import de.mhus.nimbus.world.generator.composer.biome.BiomePlacementResult;
import de.mhus.nimbus.world.generator.composer.biome.Continent;
import de.mhus.nimbus.world.generator.composer.biome.PlacedBiome;
import de.mhus.nimbus.world.generator.composer.build.HexComposition;
import de.mhus.nimbus.world.generator.composer.feature.Feature;
import de.mhus.nimbus.world.generator.composer.feature.FeatureHexGrid;
import de.mhus.nimbus.world.generator.composer.point.Point;
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
 * 1. Collect all grids from all features
 * 2. Find grids that are not in any biome
 * 3. Assign biome based on:
 *    - Point features with biomeId on this grid
 *    - Neighbor grids (majority vote)
 *    - Continent default as fallback
 */
@Slf4j
public class OrphanGridFiller {

    /**
     * Fills orphan grids with biome assignments
     *
     * @param composition The composition with all features
     * @param placementResult Result from BiomeComposer with placed biomes
     * @return Number of orphan grids filled
     */
    public int fill(HexComposition composition, BiomePlacementResult placementResult) {
        log.debug("Starting OrphanGridFiller");

        // Build map: coord -> biome
        Map<String, Biome> gridToBiomeMap = buildGridToBiomeMap(placementResult);

        // Collect all grids from all features
        Set<HexVector2> allFeatureGrids = collectAllFeatureGrids(composition);
        log.debug("Found {} grids in all features", allFeatureGrids.size());

        // Find grids without g_builder parameter (orphans or grids with empty parameters)
        List<HexVector2> orphanGrids = new ArrayList<>();
        for (HexVector2 coord : allFeatureGrids) {
            List<FeatureHexGrid> existingGrids = findAllFeatureHexGrids(coord, composition);
            if (!existingGrids.isEmpty()) {
                // Check if ANY grid has no g_builder parameter
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
                    log.trace("Grid {} has at least one FeatureHexGrid without g_builder parameter", coordKey(coord));
                }
            } else {
                // Grid not found in any feature - should not happen but handle it
                String key = coordKey(coord);
                if (!gridToBiomeMap.containsKey(key)) {
                    orphanGrids.add(coord);
                    log.trace("Grid {} not found in any feature or biome", key);
                }
            }
        }

        if (orphanGrids.isEmpty()) {
            log.debug("No orphan grids found (all grids have g_builder)");
            return 0;
        }

        log.info("Found {} orphan grids without g_builder parameter", orphanGrids.size());

        // Build continent map for fallback
        Map<String, Continent> continentMap = new HashMap<>();
        if (composition.getContinents() != null) {
            for (Continent continent : composition.getContinents()) {
                continentMap.put(continent.getContinentId(), continent);
            }
        }

        // Fill each orphan grid
        int filled = 0;
        for (HexVector2 coord : orphanGrids) {
            Biome assignedBiome = assignBiomeToOrphanGrid(coord, composition,
                    placementResult, gridToBiomeMap, continentMap);

            if (assignedBiome != null) {
                // Find ALL existing FeatureHexGrids and update their parameters
                List<FeatureHexGrid> existingGrids = findAllFeatureHexGrids(coord, composition);
                if (!existingGrids.isEmpty()) {
                    // Update parameters of ALL existing grids
                    int updated = 0;
                    for (FeatureHexGrid existingGrid : existingGrids) {
                        Map<String, String> params = existingGrid.getParameters();
                        if (params == null || !params.containsKey("g_builder")) {
                            updateFeatureHexGridParameters(existingGrid, assignedBiome);
                            updated++;
                        }
                    }
                    log.debug("Updated parameters for {} FeatureHexGrid(s) at {} from biome '{}'",
                            updated, coordKey(coord), assignedBiome.getName());
                } else {
                    // No existing grid found - add to central registry
                    addGridToCentralRegistry(coord, assignedBiome, placementResult, composition);
                    log.debug("Created new FeatureHexGrid for orphan grid {} in biome '{}'",
                            coordKey(coord), assignedBiome.getName());
                }

                gridToBiomeMap.put(coordKey(coord), assignedBiome);
                filled++;
            } else {
                log.warn("Could not assign biome to orphan grid: {}", coordKey(coord));
            }
        }

        log.info("OrphanGridFiller: filled {} orphan grids", filled);
        return filled;
    }

    /**
     * Assigns a biome to an orphan grid
     */
    private Biome assignBiomeToOrphanGrid(HexVector2 coord,
                                          HexComposition composition,
                                          BiomePlacementResult placementResult,
                                          Map<String, Biome> gridToBiomeMap,
                                          Map<String, Continent> continentMap) {

        // Strategy 1: Check if there's a Point feature with biomeId on this grid
        Biome biomeFromPoint = findBiomeFromPointFeature(coord, composition, placementResult);
        if (biomeFromPoint != null) {
            log.debug("Grid {} assigned from point feature: {}",
                    coordKey(coord), biomeFromPoint.getName());
            return biomeFromPoint;
        }

        // Strategy 2: Check neighbor grids (majority vote)
        Biome biomeFromNeighbors = findBiomeFromNeighbors(coord, gridToBiomeMap, placementResult);
        if (biomeFromNeighbors != null) {
            log.debug("Grid {} assigned from neighbors: {}",
                    coordKey(coord), biomeFromNeighbors.getName());
            return biomeFromNeighbors;
        }

        // Strategy 3: Use continent default
        Biome biomeFromContinent = findBiomeFromContinent(composition, continentMap, placementResult);
        if (biomeFromContinent != null) {
            log.debug("Grid {} assigned from continent default: {}",
                    coordKey(coord), biomeFromContinent.getName());
            return biomeFromContinent;
        }

        return null;
    }

    /**
     * Finds biome from a Point feature with biomeId on this grid
     */
    private Biome findBiomeFromPointFeature(HexVector2 coord,
                                            HexComposition composition,
                                            BiomePlacementResult placementResult) {
        if (composition.getFeatures() == null) {
            return null;
        }

        for (Feature feature : composition.getFeatures()) {
            if (feature instanceof Point) {
                Point point = (Point) feature;

                // Check if point is placed on this grid
                if (point.getGridCoordinate() != null &&
                    point.getGridCoordinate().getQ() == coord.getQ() &&
                    point.getGridCoordinate().getR() == coord.getR()) {

                    // Point has biomeId - find that biome
                    if (point.getBiomeId() != null) {
                        return findBiomeByName(point.getBiomeId(), placementResult);
                    }
                }
            }
        }

        return null;
    }

    /**
     * Finds biome from neighbor grids (majority vote)
     */
    private Biome findBiomeFromNeighbors(HexVector2 coord,
                                         Map<String, Biome> gridToBiomeMap,
                                         BiomePlacementResult placementResult) {
        List<HexVector2> neighbors = getNeighbors(coord);

        // Count biomes of neighbors
        Map<String, Integer> biomeCount = new HashMap<>();
        for (HexVector2 neighbor : neighbors) {
            Biome neighborBiome = gridToBiomeMap.get(coordKey(neighbor));
            if (neighborBiome != null) {
                String biomeName = neighborBiome.getName();
                biomeCount.put(biomeName, biomeCount.getOrDefault(biomeName, 0) + 1);
            }
        }

        // Find biome with most neighbors
        String mostCommonBiomeName = null;
        int maxCount = 0;
        for (Map.Entry<String, Integer> entry : biomeCount.entrySet()) {
            if (entry.getValue() > maxCount) {
                maxCount = entry.getValue();
                mostCommonBiomeName = entry.getKey();
            }
        }

        if (mostCommonBiomeName != null) {
            return findBiomeByName(mostCommonBiomeName, placementResult);
        }

        return null;
    }

    /**
     * Finds biome from continent default
     */
    private Biome findBiomeFromContinent(HexComposition composition,
                                         Map<String, Continent> continentMap,
                                         BiomePlacementResult placementResult) {
        // Use first continent as default
        if (continentMap.isEmpty()) {
            return null;
        }

        Continent continent = continentMap.values().iterator().next();

        // Find or create a continent filler biome
        for (PlacedBiome placed : placementResult.getPlacedBiomes()) {
            Biome biome = placed.getBiome();
            if (biome.getContinentId() != null &&
                biome.getContinentId().equals(continent.getContinentId()) &&
                biome.getName().startsWith("continent-filler-")) {
                return biome;
            }
        }

        // If no continent filler exists, use any biome from this continent
        for (PlacedBiome placed : placementResult.getPlacedBiomes()) {
            Biome biome = placed.getBiome();
            if (biome.getContinentId() != null &&
                biome.getContinentId().equals(continent.getContinentId())) {
                return biome;
            }
        }

        return null;
    }

    /**
     * Finds a biome by name in placement result
     */
    private Biome findBiomeByName(String biomeName, BiomePlacementResult placementResult) {
        for (PlacedBiome placed : placementResult.getPlacedBiomes()) {
            if (biomeName.equals(placed.getBiome().getName())) {
                return placed.getBiome();
            }
        }
        return null;
    }

    /**
     * Finds ALL existing FeatureHexGrids for this coordinate in central registry
     */
    private List<FeatureHexGrid> findAllFeatureHexGrids(HexVector2 coord, HexComposition composition) {
        List<FeatureHexGrid> result = new ArrayList<>();

        // Use central FeatureHexGrid registry (Single Source of Truth)
        Map<String, FeatureHexGrid> registry = composition.getFeatureHexGridRegistry();
        if (registry == null || registry.isEmpty()) {
            return result;
        }

        // Find all grids at this coordinate
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
     * Updates parameters of an existing FeatureHexGrid with biome parameters
     */
    private void updateFeatureHexGridParameters(FeatureHexGrid featureHexGrid, Biome biome) {
        // Get or create parameters map
        Map<String, String> parameters = featureHexGrid.getParameters();
        if (parameters == null) {
            parameters = new HashMap<>();
            featureHexGrid.setParameters(parameters);
        }

        // Copy biome parameters
        if (biome.getParameters() != null) {
            parameters.putAll(biome.getParameters());
        }

        // Add biome identification
        parameters.put("biome", biome.getType() != null ? biome.getType().getBuilderName() : "mountain");
        parameters.put("biomeName", biome.getName());

        log.trace("Updated FeatureHexGrid parameters: {}", parameters);
    }

    /**
     * Adds a grid to central registry and placed biome with biome parameters
     */
    private void addGridToCentralRegistry(HexVector2 coord, Biome biome,
                                          BiomePlacementResult placementResult,
                                          HexComposition composition) {
        // Find the PlacedBiome for this biome
        PlacedBiome targetPlaced = null;
        for (PlacedBiome placed : placementResult.getPlacedBiomes()) {
            if (placed.getBiome() == biome) {
                targetPlaced = placed;
                break;
            }
        }

        if (targetPlaced == null) {
            log.warn("Could not find PlacedBiome for biome: {}", biome.getName());
            return;
        }

        // Add coordinate to placed biome
        if (!targetPlaced.getCoordinates().contains(coord)) {
            targetPlaced.getCoordinates().add(coord);
            targetPlaced.setActualSize(targetPlaced.getCoordinates().size());
        }

        // Create FeatureHexGrid with biome parameters
        FeatureHexGrid featureHexGrid = new FeatureHexGrid();
        featureHexGrid.setCoordinate(coord);
        featureHexGrid.setName(biome.getName() + " [" + coord.getQ() + "," + coord.getR() + "]");
        featureHexGrid.setDescription("Orphan grid assigned to " + biome.getName());

        // Copy biome parameters
        Map<String, String> parameters = new HashMap<>();
        if (biome.getParameters() != null) {
            parameters.putAll(biome.getParameters());
        }

        // Add biome name and biomeName
        parameters.put("biome", biome.getType() != null ? biome.getType().getBuilderName() : "mountain");
        parameters.put("biomeName", biome.getName());

        featureHexGrid.setParameters(parameters);

        // Add to central FeatureHexGrid registry (Single Source of Truth)
        String positionKey = featureHexGrid.getPositionKey();
        if (positionKey != null) {
            if (composition.getFeatureHexGridRegistry() == null) {
                composition.setFeatureHexGridRegistry(new HashMap<>());
            }

            // Check if grid already exists in central registry
            if (!composition.getFeatureHexGridRegistry().containsKey(positionKey)) {
                composition.getFeatureHexGridRegistry().put(positionKey, featureHexGrid);
                log.debug("Created FeatureHexGrid for orphan grid {} in central registry with parameters: {}",
                         coordKey(coord), parameters);
            }
        }
    }

    /**
     * Builds a map from grid coordinate to biome
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
     * Collects all grids from central FeatureHexGrid registry
     */
    private Set<HexVector2> collectAllFeatureGrids(HexComposition composition) {
        Set<HexVector2> allGrids = new HashSet<>();

        // Use central FeatureHexGrid registry (Single Source of Truth)
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
     * Gets all 6 neighbors of a hex coordinate
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
     * Creates a string key for a coordinate
     */
    private String coordKey(HexVector2 coord) {
        return TypeUtil.toStringHexCoord(coord);
    }
}
