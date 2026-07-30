package de.mhus.nimbus.world.control.service.delete.impl;

import de.mhus.nimbus.world.control.service.delete.DeleteWorldResources;
import de.mhus.nimbus.world.shared.layer.WLayerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service to delete layers for a given world.
 * Deletes WLayer, WLayerModel, and WLayerTerrain entities.
 * Also deletes associated storage data for terrain layers.
 *
 * Data ownership: delegates to {@link WLayerService} (owner of layer entities)
 * instead of accessing the repositories / MongoTemplate directly.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteLayersService implements DeleteWorldResources {

    private final WLayerService layerService;

    @Override
    public String name() {
        return "layers";
    }

    @Override
    public void deleteWorldResources(String worldId) throws Exception {
        layerService.deleteByWorldId(worldId);
    }

    @Override
    public List<String> getKnownWorldIds() throws Exception {
        return layerService.findDistinctWorldIds();
    }
}
