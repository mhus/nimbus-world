package de.mhus.nimbus.world.generator.structures;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import de.mhus.nimbus.world.generator.composer.town.BuildingDefinition;
import de.mhus.nimbus.world.generator.composer.town.StructuresIndex;
import de.mhus.nimbus.world.generator.composer.town.TownGridConfig;
import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.layer.LayerType;
import de.mhus.nimbus.world.shared.layer.WLayer;
import de.mhus.nimbus.world.shared.layer.WLayerModel;
import de.mhus.nimbus.world.shared.layer.WLayerService;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import de.mhus.nimbus.world.shared.world.WWorld;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Places structure models (buildings) into a world layer based on the
 * g_village configuration in a WHexGrid.
 *
 * For each building place with a buildingId, creates a WLayerModel in the
 * world's 'structures' layer that references the original model in the
 * region collection via referenceModelId.
 *
 * Usage:
 * <pre>
 * StructurePlacerResult result = StructurePlacer.builder()
 *     .structuresIndex(index)
 *     .world(world)
 *     .layerService(layerService)
 *     .hexGrid(hexGrid)
 *     .flat(flat)
 *     .build()
 *     .placeStructures();
 * </pre>
 */
@Slf4j
@Builder
public class StructurePlacer {

    private static final String STRUCTURES_LAYER_NAME = "structures";
    private static final String G_VILLAGE_PARAM = "g_village";

    private final StructuresIndex structuresIndex;
    private final WWorld world;
    private final WLayerService layerService;
    private final WHexGrid hexGrid;
    private final WFlat flat;

    /**
     * Places all structures defined in the g_village parameter of the hex grid.
     *
     * @return Result with statistics about placed structures
     */
    public StructurePlacerResult placeStructures() {
        List<String> placed = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int skipped = 0;

        String worldId = world.getWorldId();

        // Parse g_village parameter
        String villageJson = hexGrid.getParameters() != null
                ? hexGrid.getParameters().get(G_VILLAGE_PARAM) : null;

        if (villageJson == null || villageJson.isBlank()) {
            log.debug("No g_village parameter found in hexGrid {}", hexGrid.getPosition());
            return StructurePlacerResult.builder()
                    .placed(placed).errors(errors).skipped(0).build();
        }

        TownGridConfig config;
        try {
            ObjectMapper mapper = JsonMapper.builder()
                    .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .build();
            config = mapper.readValue(villageJson, TownGridConfig.class);
        } catch (Exception e) {
            log.error("Failed to parse g_village JSON for hexGrid {}", hexGrid.getPosition(), e);
            errors.add("Failed to parse g_village: " + e.getMessage());
            return StructurePlacerResult.builder()
                    .placed(placed).errors(errors).skipped(0).build();
        }

        if (config.getPlaces() == null || config.getPlaces().isEmpty()) {
            log.debug("No places in g_village for hexGrid {}", hexGrid.getPosition());
            return StructurePlacerResult.builder()
                    .placed(placed).errors(errors).skipped(0).build();
        }

        // Ensure 'structures' layer exists in the world
        String layerDataId = ensureStructuresLayer(worldId);
        if (layerDataId == null) {
            errors.add("Failed to create/find structures layer for worldId=" + worldId);
            return StructurePlacerResult.builder()
                    .placed(placed).errors(errors).skipped(0).build();
        }

        // Resolve region collection worldId for referenceModelId
        String regionWorldId = de.mhus.nimbus.shared.types.WorldId.of(worldId)
                .map(wid -> wid.toRegionCollection().getId())
                .orElse(null);

        if (regionWorldId == null) {
            errors.add("Cannot resolve region collection for worldId=" + worldId);
            return StructurePlacerResult.builder()
                    .placed(placed).errors(errors).skipped(0).build();
        }

        // Process each building place
        int orderCounter = 100;
        for (TownGridConfig.PlacedPlaceConfig place : config.getPlaces()) {
            if (!"building".equals(place.getType())) {
                continue;
            }
            if (place.getBuildingId() == null || place.getBuildingId().isBlank()) {
                skipped++;
                continue;
            }

            try {
                // Verify building exists in index
                String buildingId = place.getBuildingId();
                BuildingDefinition building = findBuilding(buildingId, config.getStyle(), place.getKind());
                if (building == null) {
                    log.warn("Building '{}' not found in StructuresIndex, skipping", buildingId);
                    skipped++;
                    continue;
                }

                // Calculate absolute world position from flat + local coordinates
                int worldX = flat.getMountX() + place.getLocalX();
                int worldZ = flat.getMountZ() + place.getLocalZ();
                int worldY = place.getLevel();
                if (worldY == 0 && place.getLocalX() >= 0 && place.getLocalZ() >= 0
                        && place.getLocalX() < flat.getSizeX() && place.getLocalZ() < flat.getSizeZ()) {
                    worldY = flat.getLevel(place.getLocalX(), place.getLocalZ());
                }

                // Build referenceModelId: regionWorldId/buildingId
                String referenceModelId = regionWorldId + "/" + buildingId;

                // Create unique model name for this placement
                String modelName = buildingId + "-" + hexGrid.getPosition()
                        + "-" + place.getHexQ() + "_" + place.getHexR();

                // Create WLayerModel referencing the region model
                WLayerModel model = WLayerModel.builder()
                        .worldId(worldId)
                        .name(modelName)
                        .title(building.getTitle())
                        .layerDataId(layerDataId)
                        .mountX(worldX)
                        .mountY(worldY)
                        .mountZ(worldZ)
                        .rotation(place.getRotation())
                        .referenceModelId(referenceModelId)
                        .order(orderCounter++)
                        .sizeX(0)
                        .sizeY(0)
                        .sizeZ(0)
                        .content(new ArrayList<>())
                        .build();

                model.touchCreate();
                layerService.saveModel(model);

                placed.add(modelName);
                log.debug("Placed structure '{}' at ({},{},{}) rotation={} ref={}",
                        modelName, worldX, worldY, worldZ, place.getRotation(), referenceModelId);

            } catch (Exception e) {
                String error = "Failed to place building '" + place.getBuildingId()
                        + "' at place '" + place.getName() + "': " + e.getMessage();
                errors.add(error);
                log.error(error, e);
            }
        }

        log.info("StructurePlacer for hexGrid {}: placed={}, skipped={}, errors={}",
                hexGrid.getPosition(), placed.size(), skipped, errors.size());

        return StructurePlacerResult.builder()
                .placed(placed)
                .errors(errors)
                .skipped(skipped)
                .build();
    }

    /**
     * Finds a building definition in the index by its buildingId.
     * Falls back to style+kind search if direct lookup is not available.
     */
    private BuildingDefinition findBuilding(String buildingId, String style, String kind) {
        // Search by style and kind, then match buildingId
        if (style != null && kind != null) {
            List<BuildingDefinition> candidates = structuresIndex.findBuildings(style, kind);
            for (BuildingDefinition candidate : candidates) {
                if (buildingId.equals(candidate.getBuildingId())) {
                    return candidate;
                }
            }
        }

        // Fallback: search by style only
        if (style != null) {
            List<BuildingDefinition> allForStyle = structuresIndex.findBuildingsByStyle(style);
            for (BuildingDefinition candidate : allForStyle) {
                if (buildingId.equals(candidate.getBuildingId())) {
                    return candidate;
                }
            }
        }

        return null;
    }

    /**
     * Ensures the 'structures' layer exists for the given worldId.
     * Creates it if it doesn't exist.
     *
     * @return layerDataId of the structures layer, or null if creation failed
     */
    private String ensureStructuresLayer(String worldId) {
        Optional<WLayer> existing = layerService.findByWorldIdAndName(worldId, STRUCTURES_LAYER_NAME);
        if (existing.isPresent()) {
            return existing.get().getLayerDataId();
        }

        try {
            WLayer created = layerService.createLayer(
                    worldId,
                    STRUCTURES_LAYER_NAME,
                    LayerType.MODEL,
                    200,
                    false,
                    null,
                    false
            );
            log.info("Created 'structures' layer for worldId={}: layerDataId={}", worldId, created.getLayerDataId());
            return created.getLayerDataId();
        } catch (Exception e) {
            log.error("Failed to create 'structures' layer for worldId={}", worldId, e);
            return null;
        }
    }
}
