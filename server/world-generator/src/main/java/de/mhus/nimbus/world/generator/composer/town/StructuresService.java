package de.mhus.nimbus.world.generator.composer.town;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.layer.WLayer;
import de.mhus.nimbus.world.shared.layer.WLayerModel;
import de.mhus.nimbus.world.shared.layer.WLayerService;
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
public class StructuresService {

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
}
