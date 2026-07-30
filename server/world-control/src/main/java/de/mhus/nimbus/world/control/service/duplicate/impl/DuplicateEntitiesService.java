package de.mhus.nimbus.world.control.service.duplicate.impl;

import de.mhus.nimbus.world.control.service.duplicate.DuplicateToWorld;
import de.mhus.nimbus.world.shared.world.WEntityModelService;
import de.mhus.nimbus.world.shared.world.WEntityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Service to duplicate entities from source world to target world.
 * Duplicates both WEntityModel templates and WEntity instances.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DuplicateEntitiesService implements DuplicateToWorld {

    private final WEntityService entityService;
    private final WEntityModelService entityModelService;

    @Override
    public String name() {
        return "entities";
    }

    @Override
    public void duplicate(String sourceWorldId, String targetWorldId) throws Exception {
        log.info("Duplicating entities from world {} to {}", sourceWorldId, targetWorldId);

        // Duplicate entity models first
        int modelCount = entityModelService.duplicateToWorld(sourceWorldId, targetWorldId);

        // Duplicate entity instances
        int entityCount = entityService.duplicateToWorld(sourceWorldId, targetWorldId);

        log.info("Duplicated {} entity models and {} entity instances from world {} to {}",
                modelCount, entityCount, sourceWorldId, targetWorldId);
    }
}
