package de.mhus.nimbus.world.control.service.delete;

import de.mhus.nimbus.world.shared.world.WEntity;
import de.mhus.nimbus.world.shared.world.WEntityModel;
import de.mhus.nimbus.world.shared.world.WEntityModelRepository;
import de.mhus.nimbus.world.shared.world.WEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
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

    private final WEntityRepository entityRepository;
    private final WEntityModelRepository entityModelRepository;
    private final MongoTemplate mongoTemplate;

    @Override
    public String name() {
        return "entities";
    }

    @Override
    public void deleteWorldResources(String worldId) throws Exception {
        log.info("Deleting entities for world {}", worldId);

        // Delete entity instances
        List<WEntity> entities = entityRepository.findByWorldId(worldId);
        log.info("Found {} entity instances in world {}", entities.size(), worldId);

        entityRepository.deleteAll(entities);
        int entityCount = entities.size();

        // Delete entity models
        List<WEntityModel> models = entityModelRepository.findByWorldId(worldId);
        log.info("Found {} entity models in world {}", models.size(), worldId);

        entityModelRepository.deleteAll(models);
        int modelCount = models.size();

        log.info("Deleted {} entity models and {} entity instances for world {}",
                modelCount, entityCount, worldId);
    }

    @Override
    public List<String> getKnownWorldIds() throws Exception {
        // Collect worldIds from both WEntity and WEntityModel
        Set<String> worldIds = new HashSet<>();

        worldIds.addAll(mongoTemplate.findDistinct(
                new Query(),
                "worldId",
                WEntity.class,
                String.class
        ));

        worldIds.addAll(mongoTemplate.findDistinct(
                new Query(),
                "worldId",
                WEntityModel.class,
                String.class
        ));

        return worldIds.stream().sorted().toList();
    }
}
