package de.mhus.nimbus.world.generator.flat.hexgrid.composer;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.shared.utils.TypeUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Composes structures (villages, towns, etc.) on a hex grid.
 *
 * Structures are independent composites with a fixed grid structure.
 * They are built independently and then arranged in the world.
 * Each structure consists of multiple grids connected by internal roads.
 */
@Slf4j
public class StructureComposer {

    /**
     * Composes structures from a prepared composition.
     *
     * @param composition The composition with structure definitions
     * @param placementResult The biome placement result (for anchors and adding placed structures)
     * @return Result with placed structures
     */
    public StructurePlacementResult composeStructures(HexComposition composition,
                                                       BiomePlacementResult placementResult) {
        log.info("Starting structure composition");

        List<PlacedStructure> placedStructures = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int totalStructures = 0;
        int placedCount = 0;
        int failedCount = 0;

        // Process villages
        List<Village> villages = composition.getVillages();
        totalStructures += villages.size();

        for (Village village : villages) {
            try {
                PlacedStructure placed = placeVillage(village, placementResult);
                if (placed != null) {
                    placedStructures.add(placed);
                    placedCount++;
                    log.info("Placed village '{}' at center {} with {} grids",
                        village.getName(), placed.getCenter(), placed.getGrids().size());
                } else {
                    failedCount++;
                    errors.add("Failed to place village: " + village.getName());
                }
            } catch (Exception e) {
                failedCount++;
                String error = "Error placing village '" + village.getName() + "': " + e.getMessage();
                errors.add(error);
                log.error(error, e);
            }
        }

        // TODO: Process towns and other structures

        boolean success = failedCount == 0;

        log.info("Structure composition complete: {}/{} structures placed ({} failed)",
            placedCount, totalStructures, failedCount);

        return StructurePlacementResult.builder()
            .placedStructures(placedStructures)
            .totalStructures(totalStructures)
            .placedCount(placedCount)
            .failedCount(failedCount)
            .errors(errors)
            .success(success)
            .build();
    }

    /**
     * Places a village with a fixed 3-grid structure (dummy implementation).
     *
     * The village consists of 3 grids arranged in a line:
     * - Grid 0: Center grid
     * - Grid 1: East neighbor
     * - Grid 2: West neighbor
     *
     * @param village The village to place
     * @param placementResult The biome placement result (for finding anchor position)
     * @return PlacedStructure or null if placement failed
     */
    private PlacedStructure placeVillage(Village village, BiomePlacementResult placementResult) {
        log.debug("Placing village '{}' with dummy 3-grid structure", village.getName());

        // Find anchor position from village positions
        HexVector2 center = findAnchorPosition(village, placementResult);
        if (center == null) {
            log.warn("Could not find anchor position for village '{}'", village.getName());
            return null;
        }

        // Create 3 grids in a line: West - Center - East
        List<HexVector2> grids = new ArrayList<>();

        // Center grid
        grids.add(center);

        // East neighbor (q+1, r)
        grids.add(TypeUtil.hexVector2(center.getQ() + 1, center.getR()));

        // West neighbor (q-1, r)
        grids.add(TypeUtil.hexVector2(center.getQ() - 1, center.getR()));

        log.debug("Village '{}' grids: Center={}, East={}, West={}",
            village.getName(), grids.get(0), grids.get(1), grids.get(2));

        // Create PlacedBiomes for each grid with VILLAGE biome type
        for (HexVector2 gridCoord : grids) {
            // Create a virtual "village" biome for this grid
            Biome villageBiome = Biome.builder()
                .name(village.getName() + "-grid-" + gridCoord.getQ() + "," + gridCoord.getR())
                .title(village.getTitle() + " Grid")
                .type(BiomeType.VILLAGE)
                .build();
            villageBiome.initialize();

            // Configure the hex grid for this biome
            villageBiome.configureHexGrids(List.of(gridCoord));

            // Create PlacedBiome (representing the village grid)
            PlacedBiome placedGrid = PlacedBiome.builder()
                .biome(villageBiome)
                .center(gridCoord)
                .coordinates(List.of(gridCoord))
                .actualSize(1)
                .build();

            // Add to placement result so it's included in the world
            placementResult.getPlacedBiomes().add(placedGrid);
        }

        // Create PlacedStructure result
        PlacedStructure placedStructure = PlacedStructure.builder()
            .structure(village)
            .center(center)
            .grids(grids)
            .gridCount(grids.size())
            .build();

        return placedStructure;
    }

    /**
     * Finds the anchor position for a structure based on its positions configuration.
     *
     * @param structure The structure (e.g., Village)
     * @param placementResult The biome placement result (for finding anchors)
     * @return The anchor position or null if not found
     */
    private HexVector2 findAnchorPosition(Structure structure, BiomePlacementResult placementResult) {
        // Get prepared positions from the structure
        List<PreparedPosition> positions = structure.getPreparedPositions();
        if (positions == null || positions.isEmpty()) {
            log.warn("Structure '{}' has no prepared positions", structure.getName());
            return null;
        }

        // Sort by priority and take the first one
        PreparedPosition position = positions.stream()
            .sorted((p1, p2) -> Integer.compare(p2.getPriority(), p1.getPriority()))
            .findFirst()
            .orElse(null);

        if (position == null) {
            return null;
        }

        // Find anchor biome
        String anchorName = position.getAnchor();
        PlacedBiome anchorBiome = placementResult.getPlacedBiomes().stream()
            .filter(pb -> anchorName.equals(pb.getBiome().getName()))
            .findFirst()
            .orElse(null);

        if (anchorBiome == null) {
            log.warn("Anchor biome '{}' not found for structure '{}'", anchorName, structure.getName());
            return null;
        }

        // Use the center of the anchor biome as the structure position
        HexVector2 anchorCenter = anchorBiome.getCenter();

        // Apply direction offset if specified
        HexVector2 targetPosition = anchorCenter;
        if (position.getDirection() != null) {
            int distance = position.getDistanceFrom() > 0 ? position.getDistanceFrom() : 3;
            targetPosition = applyDirectionOffset(anchorCenter, position.getDirection().name(), distance);
        }

        String directionStr = position.getDirection() != null ? position.getDirection().name() : "CENTER";
        log.debug("Found anchor position for structure '{}': {} (from anchor '{}' with direction '{}')",
            structure.getName(), targetPosition, anchorName, directionStr);

        return targetPosition;
    }

    /**
     * Applies a direction offset to a position.
     *
     * @param center The center position
     * @param direction The direction (N, NE, E, SE, S, SW, W, NW, CENTER)
     * @param distance The distance to move
     * @return The new position
     */
    private HexVector2 applyDirectionOffset(HexVector2 center, String direction, int distance) {
        int q = center.getQ();
        int r = center.getR();

        switch (direction.toUpperCase()) {
            case "N":
                r -= distance;
                break;
            case "NE":
                q += distance;
                r -= distance;
                break;
            case "E":
                q += distance;
                break;
            case "SE":
                r += distance;
                break;
            case "S":
                q -= distance;
                r += distance;
                break;
            case "SW":
                q -= distance;
                break;
            case "W":
                q -= distance;
                break;
            case "NW":
                r -= distance;
                break;
            case "CENTER":
            default:
                // No offset
                break;
        }

        return TypeUtil.hexVector2(q, r);
    }
}
