package de.mhus.nimbus.world.control.service.duplicate;

import de.mhus.nimbus.shared.storage.StorageService;
import de.mhus.nimbus.world.shared.layer.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service to duplicate layers from source world to target world.
 * Duplicates WLayer, WLayerModel, and WLayerTerrain entities.
 * Also duplicates associated storage data for terrain layers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DuplicateLayersService implements DuplicateToWorld {

    private final WLayerRepository layerRepository;
    private final WLayerModelRepository layerModelRepository;
    private final WLayerTerrainRepository layerTerrainRepository;
    private final StorageService storageService;

    @Override
    public String name() {
        return "layers";
    }

    @Override
    public void duplicate(String sourceWorldId, String targetWorldId) throws Exception {
        log.info("Duplicating layers from world {} to {}", sourceWorldId, targetWorldId);

        List<WLayer> sourceLayers = layerRepository.findByWorldIdOrderByOrderAsc(sourceWorldId);
        log.info("Found {} layers in source world {}", sourceLayers.size(), sourceWorldId);

        // Track old layerDataId -> new layerDataId mapping
        Map<String, String> layerDataIdMapping = new HashMap<>();

        int layerCount = 0;
        int modelCount = 0;
        int terrainCount = 0;
        int storageCount = 0;

        for (WLayer sourceLayer : sourceLayers) {
            String oldLayerDataId = sourceLayer.getLayerDataId();
            String newLayerDataId = null;

            // Duplicate layer-specific data based on type
            if (sourceLayer.getLayerType() == LayerType.MODEL) {
                // Duplicate WLayerModel entities
                List<WLayerModel> sourceModels = layerModelRepository.findByLayerDataIdOrderByOrder(oldLayerDataId);
                log.debug("Duplicating {} model entities for layer {}", sourceModels.size(), sourceLayer.getName());

                for (WLayerModel sourceModel : sourceModels) {
                    WLayerModel targetModel = WLayerModel.builder()
                            .worldId(targetWorldId)
                            .name(sourceModel.getName())
                            .title(sourceModel.getTitle())
                            .layerDataId(oldLayerDataId) // Keep same for now
                            .mountX(sourceModel.getMountX())
                            .mountY(sourceModel.getMountY())
                            .mountZ(sourceModel.getMountZ())
                            .rotation(sourceModel.getRotation())
                            .referenceModelId(sourceModel.getReferenceModelId())
                            .order(sourceModel.getOrder())
                            .content(sourceModel.getContent())
                            .groups(sourceModel.getGroups())
                            .build();

                    targetModel.touchCreate();
                    layerModelRepository.save(targetModel);

                    // Use the new MongoDB id as the new layerDataId
                    if (newLayerDataId == null) {
                        newLayerDataId = targetModel.getId();
                        layerDataIdMapping.put(oldLayerDataId, newLayerDataId);
                    }

                    modelCount++;
                }

            } else if (sourceLayer.getLayerType() == LayerType.GROUND) {
                // Duplicate WLayerTerrain entities
                List<WLayerTerrain> sourceTerrains = layerTerrainRepository.findByWorldIdAndLayerDataId(sourceWorldId, oldLayerDataId);
                log.debug("Duplicating {} terrain chunks for layer {}", sourceTerrains.size(), sourceLayer.getName());

                for (WLayerTerrain sourceTerrain : sourceTerrains) {
                    WLayerTerrain targetTerrain = WLayerTerrain.builder()
                            .worldId(targetWorldId)
                            .layerDataId(oldLayerDataId) // Keep same for now
                            .chunkKey(sourceTerrain.getChunkKey())
                            .compressed(sourceTerrain.isCompressed())
                            .build();

                    // Duplicate storage data if present
                    if (sourceTerrain.getStorageId() != null) {
                        String newStorageId = storageService.duplicate(sourceTerrain.getStorageId(), targetWorldId);
                        targetTerrain.setStorageId(newStorageId);
                        storageCount++;
                    }

                    targetTerrain.touchCreate();
                    layerTerrainRepository.save(targetTerrain);

                    // Use the new MongoDB id as the new layerDataId
                    if (newLayerDataId == null) {
                        newLayerDataId = targetTerrain.getId();
                        layerDataIdMapping.put(oldLayerDataId, newLayerDataId);
                    }

                    terrainCount++;
                }
            }

            // Duplicate WLayer entity
            WLayer targetLayer = WLayer.builder()
                    .worldId(targetWorldId)
                    .name(sourceLayer.getName())
                    .title(sourceLayer.getTitle())
                    .layerType(sourceLayer.getLayerType())
                    .layerDataId(newLayerDataId != null ? newLayerDataId : oldLayerDataId)
                    .allChunks(sourceLayer.isAllChunks())
                    .affectedChunks(sourceLayer.getAffectedChunks())
                    .order(sourceLayer.getOrder())
                    .enabled(sourceLayer.isEnabled())
                    .groups(sourceLayer.getGroups())
                    .baseGround(sourceLayer.isBaseGround())
                    .build();

            targetLayer.touchCreate();
            layerRepository.save(targetLayer);
            layerCount++;
        }

        // Update layerDataId references in duplicated models/terrains
        updateLayerDataIdReferences(targetWorldId, layerDataIdMapping);

        log.info("Duplicated {} layers ({} models, {} terrains, {} storage items) from world {} to {}",
                layerCount, modelCount, terrainCount, storageCount, sourceWorldId, targetWorldId);
    }

    /**
     * Update layerDataId references in duplicated WLayerModel and WLayerTerrain entities
     * to use the new layerDataId values.
     */
    private void updateLayerDataIdReferences(String targetWorldId, Map<String, String> layerDataIdMapping) {
        // Update WLayerModel layerDataId
        // Note: WLayerModelRepository doesn't have findByWorldId(), so we need to iterate through all layers
        List<WLayer> targetLayers = layerRepository.findByWorldIdOrderByOrderAsc(targetWorldId);
        for (WLayer layer : targetLayers) {
            if (layer.getLayerType() != LayerType.MODEL) continue;
            List<WLayerModel> targetModels = layerModelRepository.findByLayerDataIdOrderByOrder(layer.getLayerDataId());
            for (WLayerModel model : targetModels) {
                String newLayerDataId = layerDataIdMapping.get(model.getLayerDataId());
                if (newLayerDataId != null) {
                    model.setLayerDataId(newLayerDataId);
                    model.touchUpdate();
                    layerModelRepository.save(model);
                }
            }
        }

        // Update WLayerTerrain layerDataId
        List<WLayerTerrain> targetTerrains = layerTerrainRepository.findByWorldId(targetWorldId);
        for (WLayerTerrain terrain : targetTerrains) {
            String newLayerDataId = layerDataIdMapping.get(terrain.getLayerDataId());
            if (newLayerDataId != null) {
                terrain.setLayerDataId(newLayerDataId);
                terrain.touchUpdate();
                layerTerrainRepository.save(terrain);
            }
        }
    }
}
