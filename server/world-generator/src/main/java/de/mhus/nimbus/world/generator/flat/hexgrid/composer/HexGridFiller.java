package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.generated.types.HexGrid;
import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.shared.utils.TypeUtil;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * Fills empty hex grids between biomes with land or ocean
 * Adds an ocean ring around all biomes to prevent open edges
 */
@Slf4j
public class HexGridFiller {

    /**
     * Fills the gaps between placed biomes
     *
     * @param placementResult Result from BiomeComposer
     * @param worldId World ID for generated grids
     * @param oceanRingWidth Width of ocean ring around all biomes (default: 1)
     * @return Result with all filled hex grids
     */
    public HexGridFillResult fill(BiomePlacementResult placementResult, String worldId, int oceanRingWidth) {
        log.info("Starting hex grid filling with ocean ring width: {}", oceanRingWidth);

        if (!placementResult.isSuccess()) {
            return HexGridFillResult.builder()
                .placementResult(placementResult)
                .success(false)
                .errorMessage("Cannot fill - placement was not successful")
                .build();
        }

        List<FilledHexGrid> allGrids = new ArrayList<>();

        // First, add all biome grids
        Map<String, FilledHexGrid> gridMap = new HashMap<>();
        for (PlacedBiome placed : placementResult.getPlacedBiomes()) {
            for (HexVector2 coord : placed.getCoordinates()) {
                WHexGrid hexGrid = findHexGrid(placementResult.getHexGrids(), coord);
                if (hexGrid != null) {
                    FilledHexGrid filled = FilledHexGrid.builder()
                        .coordinate(coord)
                        .hexGrid(hexGrid)
                        .isFiller(false)
                        .biome(placed)
                        .build();

                    allGrids.add(filled);
                    gridMap.put(coordKey(coord), filled);
                }
            }
        }

        log.info("Added {} biome grids", allGrids.size());

        // Calculate bounds of all biomes
        int minQ = Integer.MAX_VALUE, maxQ = Integer.MIN_VALUE;
        int minR = Integer.MAX_VALUE, maxR = Integer.MIN_VALUE;

        for (PlacedBiome placed : placementResult.getPlacedBiomes()) {
            for (HexVector2 coord : placed.getCoordinates()) {
                minQ = Math.min(minQ, coord.getQ());
                maxQ = Math.max(maxQ, coord.getQ());
                minR = Math.min(minR, coord.getR());
                maxR = Math.max(maxR, coord.getR());
            }
        }

        // Expand bounds for ocean ring
        minQ -= oceanRingWidth;
        maxQ += oceanRingWidth;
        minR -= oceanRingWidth;
        maxR += oceanRingWidth;

        log.info("Filling area: q=[{}, {}], r=[{}, {}]", minQ, maxQ, minR, maxR);

        // Fill all empty hexes in the bounding box
        int oceanCount = 0, landCount = 0, coastCount = 0;

        for (int q = minQ; q <= maxQ; q++) {
            for (int r = minR; r <= maxR; r++) {
                HexVector2 coord = HexVector2.builder().q(q).r(r).build();
                String key = coordKey(coord);

                // Skip if already filled by a biome
                if (gridMap.containsKey(key)) {
                    continue;
                }

                // Determine filler type based on neighbors and distance
                FillerType fillerType = determineFillerType(coord, gridMap, oceanRingWidth,
                    minQ, maxQ, minR, maxR);

                // Create filler hex grid
                WHexGrid fillerGrid = createFillerHexGrid(coord, fillerType, worldId);
                FilledHexGrid filled = FilledHexGrid.builder()
                    .coordinate(coord)
                    .hexGrid(fillerGrid)
                    .fillerType(fillerType)
                    .isFiller(true)
                    .build();

                allGrids.add(filled);
                gridMap.put(key, filled);

                // Count by type
                switch (fillerType) {
                    case OCEAN -> oceanCount++;
                    case LAND -> landCount++;
                    case COAST -> coastCount++;
                }
            }
        }

        log.info("Filled {} ocean, {} land, {} coast grids", oceanCount, landCount, coastCount);

        return HexGridFillResult.builder()
            .placementResult(placementResult)
            .allGrids(allGrids)
            .oceanFillCount(oceanCount)
            .landFillCount(landCount)
            .coastFillCount(coastCount)
            .totalGridCount(allGrids.size())
            .success(true)
            .build();
    }

    /**
     * Fills with default ocean ring width of 1
     */
    public HexGridFillResult fill(BiomePlacementResult placementResult, String worldId) {
        return fill(placementResult, worldId, 1);
    }

    /**
     * Determines the filler type for a coordinate based on neighbors
     */
    private FillerType determineFillerType(HexVector2 coord,
                                           Map<String, FilledHexGrid> gridMap,
                                           int oceanRingWidth,
                                           int minQ, int maxQ, int minR, int maxR) {

        // Check if on the outer edge (ocean ring)
        if (coord.getQ() == minQ || coord.getQ() == maxQ ||
            coord.getR() == minR || coord.getR() == maxR) {
            return FillerType.OCEAN;
        }

        // Check if within ocean ring distance from edge
        int distFromEdgeQ = Math.min(coord.getQ() - minQ, maxQ - coord.getQ());
        int distFromEdgeR = Math.min(coord.getR() - minR, maxR - coord.getR());
        int distFromEdge = Math.min(distFromEdgeQ, distFromEdgeR);

        if (distFromEdge < oceanRingWidth) {
            return FillerType.OCEAN;
        }

        // Check neighbors to determine if coast or land
        List<HexVector2> neighbors = getNeighbors(coord);
        boolean hasOceanNeighbor = false;
        boolean hasLandBiomeNeighbor = false;
        boolean hasOceanBiomeNeighbor = false;

        for (HexVector2 neighbor : neighbors) {
            FilledHexGrid neighborGrid = gridMap.get(coordKey(neighbor));

            if (neighborGrid != null) {
                if (neighborGrid.isFiller()) {
                    if (neighborGrid.getFillerType() == FillerType.OCEAN) {
                        hasOceanNeighbor = true;
                    }
                } else {
                    // Check biome type
                    BiomeType biomeType = neighborGrid.getBiome().getBiome().getType();
                    if (biomeType == BiomeType.OCEAN || biomeType == BiomeType.COAST) {
                        hasOceanBiomeNeighbor = true;
                        hasOceanNeighbor = true;
                    } else {
                        hasLandBiomeNeighbor = true;
                    }
                }
            }
        }

        // Decision logic:
        // - If next to ocean filler or ocean biome: COAST or OCEAN
        // - If next to land biome: LAND
        // - Coast if transitioning between ocean and land
        if (hasOceanNeighbor && hasLandBiomeNeighbor) {
            return FillerType.COAST;
        } else if (hasOceanNeighbor || hasOceanBiomeNeighbor) {
            return FillerType.OCEAN;
        } else if (hasLandBiomeNeighbor) {
            return FillerType.LAND;
        }

        // Default: LAND
        return FillerType.LAND;
    }

    /**
     * Creates a WHexGrid for a filler coordinate
     */
    private WHexGrid createFillerHexGrid(HexVector2 coord, FillerType fillerType, String worldId) {
        String name = switch (fillerType) {
            case OCEAN -> "Ocean";
            case LAND -> "Plains";
            case COAST -> "Coast";
        };

        // Map FillerType to BiomeType and use builderName for consistency
        String biomeParam = switch (fillerType) {
            case OCEAN -> BiomeType.OCEAN.getBuilderName();
            case LAND -> BiomeType.PLAINS.getBuilderName();
            case COAST -> BiomeType.COAST.getBuilderName();
        };

        // Create public HexGrid data
        HexGrid publicData = new HexGrid();
        publicData.setPosition(coord);
        publicData.setName(name + " [" + coord.getQ() + "," + coord.getR() + "]");
        publicData.setDescription("Auto-filled " + fillerType.name().toLowerCase());

        // Parameters
        Map<String, String> parameters = new HashMap<>();
        parameters.put("biome", biomeParam);
        parameters.put("filler", "true");
        parameters.put("fillerType", fillerType.name().toLowerCase());

        return WHexGrid.builder()
            .worldId(worldId)
            .position(TypeUtil.toStringHexCoord(coord.getQ(), coord.getR()))
            .publicData(publicData)
            .parameters(parameters)
            .enabled(true)
            .build();
    }

    /**
     * Gets all 6 neighbors of a hex coordinate
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
     * Finds a hex grid by coordinate
     */
    private WHexGrid findHexGrid(List<WHexGrid> hexGrids, HexVector2 coord) {
        return hexGrids.stream()
            .filter(grid -> {
                HexVector2 pos = grid.getPublicData().getPosition();
                return pos.getQ() == coord.getQ() && pos.getR() == coord.getR();
            })
            .findFirst()
            .orElse(null);
    }

    /**
     * Creates a string key for a coordinate
     */
    private String coordKey(HexVector2 coord) {
        return coord.getQ() + "," + coord.getR();
    }
}
