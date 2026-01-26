package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.shared.utils.TypeUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Fills gaps around all biomes with coastal grids.
 *
 * Algorithm:
 * - For each existing grid, check all 6 hex neighbors
 * - If neighbor is empty, add a coast grid
 * - This creates a natural coastline around all land masses
 *
 * Coast grids are automatically added where terrain meets empty space,
 * simulating the transition from land to ocean.
 */
@Slf4j
public class CoastFiller {

    private final int coastRingWidth;

    /**
     * Creates a CoastFiller with default coast ring width of 1.
     */
    public CoastFiller() {
        this(1);
    }

    /**
     * Creates a CoastFiller with specified coast ring width.
     *
     * @param coastRingWidth Number of coast rings to add (typically 1-2)
     */
    public CoastFiller(int coastRingWidth) {
        this.coastRingWidth = coastRingWidth;
    }

    /**
     * Fills empty grids around all biomes with coast biomes.
     *
     * @param composition The composition to fill
     * @param existingCoords Set of existing coordinate keys (q:r)
     * @param placementResult Placement result from BiomeComposer
     * @return Number of coast biomes added
     */
    public int fill(HexComposition composition,
                    Set<String> existingCoords,
                    BiomePlacementResult placementResult) {

        log.info("Starting CoastFiller with ring width: {}", coastRingWidth);

        int biomesAdded = 0;

        // Process coast rings iteratively
        for (int ring = 0; ring < coastRingWidth; ring++) {
            List<HexVector2> coastCoords = new ArrayList<>();

            // Get all current grid coordinates (snapshot)
            Set<String> currentCoords = new HashSet<>(existingCoords);

            // For each existing grid, check neighbors
            for (String coordKey : currentCoords) {
                HexVector2 coord = TypeUtil.parseHexCoord(coordKey);
                List<HexVector2> neighbors = getNeighbors(coord);

                for (HexVector2 neighbor : neighbors) {
                    String neighborKey = TypeUtil.toStringHexCoord(neighbor);

                    // Skip if neighbor already exists
                    if (existingCoords.contains(neighborKey)) {
                        continue;
                    }

                    // Skip if we already added this neighbor in this iteration
                    if (coastCoords.stream().anyMatch(c -> TypeUtil.toStringHexCoord(c).equals(neighborKey))) {
                        continue;
                    }

                    coastCoords.add(neighbor);
                    existingCoords.add(neighborKey); // Mark as occupied
                }
            }

            // If we have coast coordinates, create a coast biome and PlacedBiome
            if (!coastCoords.isEmpty()) {
                Biome coastBiome = new Biome();
                coastBiome.setName("coast-ring-" + ring);
                coastBiome.setType(BiomeType.COAST);

                // Mark as filler
                if (coastBiome.getParameters() == null) {
                    coastBiome.setParameters(new HashMap<>());
                }
                coastBiome.getParameters().put("filler", "true");
                coastBiome.getParameters().put("fillerType", "coast");
                coastBiome.getParameters().put("coastRing", String.valueOf(ring));

                // Apply defaults
                coastBiome.applyDefaults();

                // Configure with specific coordinates
                coastBiome.configureHexGrids(coastCoords);

                // Create PlacedBiome (WHexGrids will be created later in HexCompositeBuilder)
                PlacedBiome placedCoast = new PlacedBiome();
                placedCoast.setBiome(coastBiome);
                placedCoast.setCenter(coastCoords.get(0)); // First coord as center
                placedCoast.setCoordinates(new ArrayList<>(coastCoords));
                placedCoast.setActualSize(coastCoords.size());

                placementResult.getPlacedBiomes().add(placedCoast);
                biomesAdded++;

                log.debug("Coast ring {}: created PlacedBiome with {} coords", ring, coastCoords.size());
            }
        }

        log.info("CoastFiller added {} coast biomes ({} rings)", biomesAdded, coastRingWidth);

        return biomesAdded;
    }


    /**
     * Gets all 6 neighbors of a hex coordinate.
     */
    private List<HexVector2> getNeighbors(HexVector2 coord) {
        List<HexVector2> neighbors = new ArrayList<>();

        // Hex directions (axial coordinates)
        int[][] directions = {
            {0, -1},  // N
            {1, -1},  // NE
            {1, 0},   // E
            {0, 1},   // SE
            {-1, 1},  // SW
            {-1, 0}   // W
        };

        for (int[] dir : directions) {
            neighbors.add(HexVector2.builder()
                .q(coord.getQ() + dir[0])
                .r(coord.getR() + dir[1])
                .build());
        }

        return neighbors;
    }

}
