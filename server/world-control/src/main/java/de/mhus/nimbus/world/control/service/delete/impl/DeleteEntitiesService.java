package de.mhus.nimbus.world.control.service.delete.impl;

import de.mhus.nimbus.world.control.service.delete.DeleteWorldResources;
import de.mhus.nimbus.world.shared.world.WEntityModelService;
import de.mhus.nimbus.world.shared.world.WEntityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Service to delete entities for a given world.
 * Deletes both WEntityModel templates and WEntity instances.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeleteEntitiesService implements DeleteWorldResources {

    private final WEntityService entityService;
    private final WEntityModelService entityModelService;

    @Override
    public String name() {
        return "entities";
    }

    @Override
    public void deleteWorldResources(String worldId) throws Exception {
        log.info("Deleting entities for world {}", worldId);

        // Delete entity instances
        int entityCount = entityService.deleteAllByWorldId(worldId);

        // Delete entity models
        int modelCount = entityModelService.deleteAllByWorldId(worldId);

        log.info("Deleted {} entity models and {} entity instances for world {}",
                modelCount, entityCount, worldId);
    }

    @Override
    public List<String> getKnownWorldIds() throws Exception {
        // Collect worldIds from both WEntity and WEntityModel
        Set<String> worldIds = new HashSet<>();

        worldIds.addAll(entityService.findDistinctWorldIds());
        worldIds.addAll(entityModelService.findDistinctWorldIds());

        return worldIds.stream().sorted().toList();
    }
}
