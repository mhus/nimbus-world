package de.mhus.nimbus.world.generator.composer.town;

import de.mhus.nimbus.generated.types.HexVector2;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.layer.WLayer;
import de.mhus.nimbus.world.shared.layer.WLayerModel;
import de.mhus.nimbus.world.shared.layer.WLayerService;
import de.mhus.nimbus.world.shared.util.HexMathUtil;
import de.mhus.nimbus.world.shared.world.WWorld;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for loading structure/building definitions from the region collection.
 *
 * Structures are stored as WLayerModel entities in a 'structures' WLayer
 * within the region collection. Each model has 'kind' and 'style' metadata
 * in its parameters map.
 *
 * The service loads model summaries (without block content) and builds
 * a StructuresIndex for fast lookup by style and kind.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StructuresGeneratorService {

    private static final String STRUCTURES_LAYER_NAME = "structures";

    private final WLayerService layerService;

    /**
     * Finds and indexes all structures available for the given worldId.
     *
     * Resolves the region collection from the worldId, looks up the 'structures' layer,
     * and loads all WLayerModel summaries. Each model's 'kind' and 'style' parameters
     * are used to build a StructuresIndex.
     *
     * @param worldId World ID (regular or collection)
     * @return StructuresIndex populated with available building definitions
     */
    public StructuresIndex findStructuresForWorldId(String worldId) {
        StructuresIndex index = new StructuresIndex();

        WorldId wid = WorldId.of(worldId)
                .orElse(null);
        if (wid == null) {
            log.warn("Invalid worldId: {}", worldId);
            return index;
        }

        // Resolve region collection worldId
        WorldId regionWorldId = wid.toRegionCollection();
        String regionId = regionWorldId.getId();

        log.debug("Loading structures for worldId={} from region collection={}", worldId, regionId);

        // Find the 'structures' layer in the region collection
        var layerOpt = layerService.findByWorldIdAndName(regionId, STRUCTURES_LAYER_NAME);
        if (layerOpt.isEmpty()) {
            log.info("No '{}' layer found in region collection {} for worldId={}", STRUCTURES_LAYER_NAME, regionId, worldId);
            return index;
        }

        WLayer layer = layerOpt.get();
        String layerDataId = layer.getLayerDataId();

        // Load model summaries (metadata only, no block content)
        List<WLayerModel> modelSummaries = layerService.listModelSummaries(layerDataId);

        log.debug("Found {} model summaries in structures layer (layerDataId={})", modelSummaries.size(), layerDataId);

        int loaded = 0;
        int skipped = 0;

        for (WLayerModel model : modelSummaries) {
            String kind = model.getParameters() != null ? model.getParameters().get("kind") : null;
            String style = model.getParameters() != null ? model.getParameters().get("style") : null;

            if (Strings.isBlank(kind) || Strings.isBlank(style)) {
                log.debug("Skipping model '{}' - missing kind or style (kind={}, style={})", model.getName(), kind, style);
                skipped++;
                continue;
            }

            BuildingDefinition building = BuildingDefinition.builder()
                    .buildingId(model.getName())
                    .title(model.getTitle() != null ? model.getTitle() : model.getName())
                    .style(style)
                    .kind(kind)
                    .dimensions(de.mhus.nimbus.generated.types.Vector3Int.builder()
                            .x(model.getSizeX()).y(model.getSizeY()).z(model.getSizeZ()).build())
                    .build();

            index.addBuilding(building);
            loaded++;
        }

        log.info("StructuresIndex for worldId={}: loaded {} buildings, skipped {} (total models: {})",
                worldId, loaded, skipped, modelSummaries.size());

        return index;
    }

    /**
     * Removes all structure models from the given layer whose mount point
     * lies within the specified hex grid cell.
     *
     * Uses {@link HexMathUtil#isPointInHex} to determine whether a model's
     * mountX/mountZ falls inside the hexagon defined by the coordinate and
     * the world's hexGridSize.
     *
     * @param coordinate Hex axial coordinate (q, r) identifying the hex grid cell
     * @param layer      The world's 'structures' layer (MODEL type)
     * @param world      The world (needed for hexGridSize)
     * @return Number of deleted models
     */
    public int clearStructuresInHexGrid(HexVector2 coordinate, WLayer layer, WWorld world) {
        String layerDataId = layer.getLayerDataId();
        int gridSize = world.getPublicData().getHexGridSize();

        int[] center = HexMathUtil.hexToCartesian(coordinate, gridSize);
        double hexCenterX = center[0];
        double hexCenterZ = center[1];

        List<WLayerModel> models = layerService.listModelSummaries(layerDataId);

        int deleted = 0;
        for (WLayerModel model : models) {
            if (HexMathUtil.isPointInHex(model.getMountX(), model.getMountZ(), hexCenterX, hexCenterZ, gridSize)) {
                layerService.deleteModelById(model.getId());
                deleted++;
                log.debug("Deleted structure model '{}' (mount={},{}) from hex {}",
                        model.getName(), model.getMountX(), model.getMountZ(), coordinate);
            }
        }

        log.info("Cleared {} structure models in hex ({},{}) for layer {}",
                deleted, coordinate.getQ(), coordinate.getR(), layer.getName());
        return deleted;
    }

    /**
     * Syncs all models in the given layer to WLayerTerrain chunks.
     * Uses {@link WLayerService#recreateModelBasedLayer} to project model blocks
     * into terrain storage without triggering dirty chunk markers.
     *
     * @param world      The world
     * @param layer      The structures layer (MODEL type)
     * @param coordinate Hex coordinate (for logging context)
     */
    public void syncLayerModelsToTerrain(WWorld world, WLayer layer, HexVector2 coordinate) {
        String worldId = world.getWorldId();
        String layerDataId = layer.getLayerDataId();

        log.info("Syncing structure models to terrain for hex ({},{}) in layer {}",
                coordinate.getQ(), coordinate.getR(), layer.getName());

        int chunks = layerService.recreateModelBasedLayer(worldId, layerDataId, false);

        log.info("Synced structure models to terrain: {} chunks recreated for layer {} in hex ({},{})",
                chunks, layer.getName(), coordinate.getQ(), coordinate.getR());
    }
}
