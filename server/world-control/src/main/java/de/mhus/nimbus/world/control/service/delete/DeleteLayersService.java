package de.mhus.nimbus.world.control.service.delete;

import de.mhus.nimbus.shared.storage.StorageService;
import de.mhus.nimbus.world.shared.layer.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service to delete layers for a given world.
 * Deletes WLayer, WLayerModel, and WLayerTerrain entities.
 * Also deletes associated storage data for terrain layers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteLayersService implements DeleteWorldResources {

    private final WLayerRepository layerRepository;
    private final WLayerModelRepository layerModelRepository;
    private final WLayerTerrainRepository layerTerrainRepository;
    private final StorageService storageService;
    private final MongoTemplate mongoTemplate;

    @Override
    public String name() {
        return "layers";
    }

    @Override
    public void deleteWorldResources(String worldId) throws Exception {
        log.info("Deleting layers for world {}", worldId);

        List<WLayer> layers = layerRepository.findByWorldIdOrderByOrderAsc(worldId);
        log.info("Found {} layers in world {}", layers.size(), worldId);

        int layerCount = 0;
        int modelCount = 0;
        int terrainCount = 0;
        int storageCount = 0;

        for (WLayer layer : layers) {
            String layerDataId = layer.getLayerDataId();

            // Delete layer-specific data based on type
            if (layer.getLayerType() == LayerType.MODEL) {
                // Delete WLayerModel entities
                List<WLayerModel> models = layerModelRepository.findByLayerDataIdOrderByOrder(layerDataId);
                log.debug("Deleting {} model entities for layer {}", models.size(), layer.getName());

                layerModelRepository.deleteAll(models);
                modelCount += models.size();

            } else if (layer.getLayerType() == LayerType.GROUND) {
                // Delete WLayerTerrain entities
                List<WLayerTerrain> terrains = layerTerrainRepository.findByLayerDataId(layerDataId);
                log.debug("Deleting {} terrain chunks for layer {}", terrains.size(), layer.getName());

                for (WLayerTerrain terrain : terrains) {
                    // Delete storage data if present
                    if (terrain.getStorageId() != null) {
                        try {
                            storageService.delete(terrain.getStorageId());
                            storageCount++;
                        } catch (Exception e) {
                            log.warn("Failed to delete storage {} for terrain chunk {}: {}",
                                    terrain.getStorageId(), terrain.getChunkKey(), e.getMessage());
                        }
                    }
                }

                layerTerrainRepository.deleteAll(terrains);
                terrainCount += terrains.size();
            }

            // Delete the layer itself
            layerRepository.delete(layer);
            layerCount++;
        }

        log.info("Deleted {} layers ({} models, {} terrain chunks, {} storage items) for world {}",
                layerCount, modelCount, terrainCount, storageCount, worldId);
    }

    @Override
    public List<String> getKnownWorldIds() throws Exception {
        return mongoTemplate.findDistinct(
                new Query(),
                "worldId",
                WLayer.class,
                String.class
        );
    }
}
