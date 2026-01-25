package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.generated.types.HexVector2;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Fills gaps around mountain biomes with lower elevation grids.
 *
 * Algorithm:
 * - For each mountain biome grid, check all 6 hex neighbors
 * - If neighbor is empty and in direction toward mountain ridge, add lower mountain grid
 * - This simulates natural mountain slopes from high peaks down to foothills
 *
 * Height reduction:
 * - HIGH_PEAKS (150/40) → MEDIUM_PEAKS (120/30)
 * - MEDIUM_PEAKS (120/30) → LOW_PEAKS (100/20)
 * - LOW_PEAKS (100/20) → MEADOW (80/10)
 * - MEADOW (80/10) → no fill (already lowest)
 */
@Slf4j
public class MountainFiller {

    /**
     * Fills empty grids around mountain biomes with lower elevation slope biomes.
     *
     * @param composition The composition to fill
     * @param existingCoords Set of existing coordinate keys (q:r)
     * @param placementResult Placement result from BiomeComposer
     * @return Number of slope biomes added
     */
    public int fill(HexComposition composition,
                    Set<String> existingCoords,
                    BiomePlacementResult placementResult) {

        log.info("Starting MountainFiller");

        int biomesAdded = 0;

        // Find all mountain biomes
        List<PlacedBiome> mountainBiomes = placementResult.getPlacedBiomes().stream()
            .filter(pb -> pb.getBiome() instanceof MountainBiome)
            .collect(Collectors.toList());

        log.info("Found {} mountain biomes to process", mountainBiomes.size());

        // Process each mountain biome
        for (PlacedBiome placedBiome : mountainBiomes) {
            MountainBiome mountainBiome = (MountainBiome) placedBiome.getBiome();
            MountainBiome.MountainHeight currentHeight = mountainBiome.getHeight();

            // Skip MEADOW (already lowest)
            if (currentHeight == MountainBiome.MountainHeight.MEADOW) {
                log.debug("Skipping MEADOW mountain '{}' (already lowest)", mountainBiome.getName());
                continue;
            }

            // Determine next lower height
            MountainBiome.MountainHeight lowerHeight = getLowerHeight(currentHeight);
            if (lowerHeight == null) {
                continue;
            }

            log.debug("Processing mountain '{}' ({} → {})",
                mountainBiome.getName(), currentHeight, lowerHeight);

            // Collect all neighbor coordinates that need slope grids
            List<HexVector2> slopeCoords = new ArrayList<>();

            for (HexVector2 coord : placedBiome.getCoordinates()) {
                List<HexVector2> neighbors = getNeighbors(coord);

                for (HexVector2 neighbor : neighbors) {
                    String neighborKey = coordKey(neighbor);

                    // Skip if neighbor already exists
                    if (existingCoords.contains(neighborKey)) {
                        continue;
                    }

                    // Skip if we already added this neighbor
                    if (slopeCoords.stream().anyMatch(c -> coordKey(c).equals(neighborKey))) {
                        continue;
                    }

                    slopeCoords.add(neighbor);
                    existingCoords.add(neighborKey); // Mark as occupied
                }
            }

            // If we have slope coordinates, create a slope biome and PlacedBiome
            if (!slopeCoords.isEmpty()) {
                MountainBiome slopeBiome = new MountainBiome();
                slopeBiome.setName(mountainBiome.getName() + "-slopes");
                slopeBiome.setType(BiomeType.MOUNTAINS);
                slopeBiome.setHeight(lowerHeight);

                // Mark as filler
                if (slopeBiome.getParameters() == null) {
                    slopeBiome.setParameters(new HashMap<>());
                }
                slopeBiome.getParameters().put("filler", "true");
                slopeBiome.getParameters().put("fillerType", "mountain");
                slopeBiome.getParameters().put("sourceMountain", mountainBiome.getName());

                // Apply defaults
                slopeBiome.applyDefaults();

                // Configure with specific coordinates (for ridge etc)
                slopeBiome.configureHexGrids(slopeCoords);

                // Create PlacedBiome (WHexGrids will be created later in HexCompositeBuilder)
                PlacedBiome placedSlope = new PlacedBiome();
                placedSlope.setBiome(slopeBiome);
                placedSlope.setCenter(slopeCoords.get(0)); // First coord as center
                placedSlope.setCoordinates(new ArrayList<>(slopeCoords));
                placedSlope.setActualSize(slopeCoords.size());

                placementResult.getPlacedBiomes().add(placedSlope);

                biomesAdded++;

                log.debug("Created slope PlacedBiome '{}' with {} coords ({})",
                    slopeBiome.getName(), slopeCoords.size(), lowerHeight);
            }
        }

        log.info("MountainFiller added {} slope biomes", biomesAdded);

        return biomesAdded;
    }

    /**
     * Gets the next lower mountain height.
     */
    private MountainBiome.MountainHeight getLowerHeight(MountainBiome.MountainHeight current) {
        return switch (current) {
            case HIGH_PEAKS -> MountainBiome.MountainHeight.MEDIUM_PEAKS;
            case MEDIUM_PEAKS -> MountainBiome.MountainHeight.LOW_PEAKS;
            case LOW_PEAKS -> MountainBiome.MountainHeight.MEADOW;
            case MEADOW -> null; // Already lowest
        };
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

    /**
     * Creates a string key for a coordinate.
     */
    private String coordKey(HexVector2 coord) {
        return coord.getQ() + ":" + coord.getR();
    }
}
