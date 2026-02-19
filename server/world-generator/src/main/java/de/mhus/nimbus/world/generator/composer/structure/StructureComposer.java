package de.mhus.nimbus.world.generator.composer.structure;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.shared.utils.TypeUtil;
import de.mhus.nimbus.world.generator.composer.build.HexComposition;
import de.mhus.nimbus.world.generator.composer.town.PlacedStructure;
import de.mhus.nimbus.world.generator.composer.biome.Biome;
import de.mhus.nimbus.world.generator.composer.biome.BiomePlacementResult;
import de.mhus.nimbus.world.generator.composer.biome.BiomeType;
import de.mhus.nimbus.world.generator.composer.biome.PlacedBiome;
import de.mhus.nimbus.world.generator.composer.build.ComposeContext;
import de.mhus.nimbus.world.generator.composer.feature.FeatureHexGrid;
import de.mhus.nimbus.world.generator.composer.town.Town;
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
     * @param context The compose context with world, composition, and placement result
     * @param placementResult The biome placement result (for anchors and adding placed structures)
     * @return Result with placed structures
     */
    public StructurePlacementResult composeStructures(ComposeContext context,
                                                      BiomePlacementResult placementResult) {
        log.debug("Starting structure composition");

        HexComposition composition = context.getComposition();

        List<PlacedStructure> placedStructures = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int totalStructures = 0;
        int placedCount = 0;
        int failedCount = 0;

        // Process villages
        List<Town> villages = composition.getVillages();
        totalStructures += villages.size();

        for (Town village : villages) {
            try {
                PlacedStructure placed = placeVillage(village, placementResult, context);
                if (placed != null) {
                    placedStructures.add(placed);
                    placedCount++;
                    log.debug("Placed village '{}' at center {} with {} grids",
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

        log.debug("Structure composition complete: {}/{} structures placed ({} failed)",
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
     * Places a village using the new district-based design system.
     *
     * The village consists of districts, each positioned as a separate grid.
     * Districts contain places (buildings, roads, free spaces, etc.) that are
     * arranged according to the district's slot configuration.
     *
     * @param village The village to place
     * @param placementResult The biome placement result (for finding anchor position)
     * @param context The compose context (for accessing world/hexGridSize)
     * @return PlacedStructure or null if placement failed
     */
    private PlacedStructure placeVillage(Town village, BiomePlacementResult placementResult, ComposeContext context) {
        log.debug("Placing village '{}' with district-based design", village.getName());

        // Get hexGridSize from world
        int hexGridSize = context.getWorld() != null ?
            context.getWorld().getPublicData().getHexGridSize() : 512;

        // Find anchor position from village positions
        HexVector2 center = findAnchorPosition(village, placementResult);
        if (center == null) {
            log.warn("Could not find anchor position for village '{}'", village.getName());
            return null;
        }

        log.debug("Village '{}' anchor position: [{},{}]", village.getName(), center.getQ(), center.getR());

        log.debug("Village '{}' has {} districts from config",
            village.getName(), village.getDistricts() != null ? village.getDistricts().size() : 0);

        // IMPORTANT: Configure the village's HexGrids
        // This will run VillageDesigner and set g_village parameters
        // Pass empty list as coordinates are determined by districts
        log.debug("Calling village.configureHexGrids() for '{}' with hexGridSize: {}", village.getName(), hexGridSize);
        village.configureHexGrids(new ArrayList<>(), hexGridSize);

        log.debug("Village '{}' configured {} HexGrids", village.getName(),
            village.getHexGrids() != null ? village.getHexGrids().size() : 0);

        if (village.getHexGrids() == null || village.getHexGrids().isEmpty()) {
            log.error("Village '{}' has no HexGrids after configuration!", village.getName());
            return null;
        }

        // Create PlacedBiomes for each configured grid
        // Translate relative coordinates from FeatureHexGrids to absolute world coordinates
        List<HexVector2> grids = new ArrayList<>();

        for (FeatureHexGrid featureHexGrid : village.getHexGrids()) {
            HexVector2 relativePos = featureHexGrid.getCoordinate();

            // Convert relative district position to absolute world position
            HexVector2 absolutePos = TypeUtil.hexVector2(
                center.getQ() + relativePos.getQ(),
                center.getR() + relativePos.getR()
            );

            // Update the FeatureHexGrid with absolute coordinates
            featureHexGrid.setCoordinate(absolutePos);
            grids.add(absolutePos);

            // Create a virtual "village" biome for this grid
            Biome villageBiome = Biome.builder()
                .name(village.getName() + "-grid-" + absolutePos.getQ() + ";" + absolutePos.getR())
                .title(village.getTitle() + " Grid")
                .type(BiomeType.TOWN)
                .build();
            villageBiome.initialize();

            // Note: Biomes no longer have local hexGrids - they use central registry
            // The featureHexGrid will be registered in central registry by BiomeComposer
            // after PlacedBiome is created

            // Create PlacedBiome (representing the village grid)
            PlacedBiome placedGrid = PlacedBiome.builder()
                .biome(villageBiome)
                .center(absolutePos)
                .coordinates(List.of(absolutePos))
                .actualSize(1)
                .build();

            // Add to placement result so it's included in the world
            placementResult.getPlacedBiomes().add(placedGrid);

            log.debug("Created PlacedBiome for district at absolute position [{};{}]",
                absolutePos.getQ(), absolutePos.getR());
        }

        log.debug("Village '{}' created {} PlacedBiomes", village.getName(), grids.size());

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

        // North = r+ = Z+ in 3D world. No exceptions.
        switch (direction.toUpperCase()) {
            case "N":
                r += distance;
                break;
            case "NE":
                q += distance;
                r += distance;
                break;
            case "E":
                q += distance;
                break;
            case "SE":
                r -= distance;
                break;
            case "S":
                q -= distance;
                r -= distance;
                break;
            case "SW":
                q -= distance;
                break;
            case "W":
                q -= distance;
                break;
            case "NW":
                r += distance;
                break;
            case "CENTER":
            default:
                // No offset
                break;
        }

        return TypeUtil.hexVector2(q, r);
    }
}
