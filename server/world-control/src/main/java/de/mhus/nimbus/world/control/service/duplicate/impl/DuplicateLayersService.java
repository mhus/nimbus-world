package de.mhus.nimbus.world.control.service.duplicate.impl;

import de.mhus.nimbus.world.control.service.duplicate.DuplicateToWorld;
import de.mhus.nimbus.world.shared.layer.WLayerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service to duplicate layers from source world to target world.
 * Duplicates WLayer, WLayerModel, and WLayerTerrain entities.
 * Also duplicates associated storage data for terrain layers.
 *
 * Data ownership: delegates to {@link WLayerService} (owner of layer entities)
 * instead of accessing the repositories directly.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DuplicateLayersService implements DuplicateToWorld {

    private final WLayerService layerService;

    @Override
    public String name() {
        return "layers";
    }

    @Override
    public void duplicate(String sourceWorldId, String targetWorldId) throws Exception {
        layerService.duplicateToWorld(sourceWorldId, targetWorldId);
    }
}
