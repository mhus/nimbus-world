package de.mhus.nimbus.world.control.service.duplicate;

import de.mhus.nimbus.world.shared.world.WEntity;
import de.mhus.nimbus.world.shared.world.WEntityModel;
import de.mhus.nimbus.world.shared.world.WEntityModelRepository;
import de.mhus.nimbus.world.shared.world.WEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service to duplicate entities from source world to target world.
 * Duplicates both WEntityModel templates and WEntity instances.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DuplicateEntitiesService implements DuplicateToWorld {

    private final WEntityRepository entityRepository;
    private final WEntityModelRepository entityModelRepository;

    @Override
    public String name() {
        return "entities";
    }

    @Override
    public void duplicate(String sourceWorldId, String targetWorldId) throws Exception {
        log.info("Duplicating entities from world {} to {}", sourceWorldId, targetWorldId);

        // Duplicate entity models first
        List<WEntityModel> sourceModels = entityModelRepository.findByWorldId(sourceWorldId);
        log.info("Found {} entity models in source world {}", sourceModels.size(), sourceWorldId);

        int modelCount = 0;
        for (WEntityModel sourceModel : sourceModels) {
            WEntityModel targetModel = WEntityModel.builder()
                    .modelId(sourceModel.getModelId())
                    .publicData(sourceModel.getPublicData())
                    .worldId(targetWorldId)
                    .enabled(sourceModel.isEnabled())
                    .build();

            targetModel.touchCreate();
            entityModelRepository.save(targetModel);
            modelCount++;
        }

        // Duplicate entity instances
        List<WEntity> sourceEntities = entityRepository.findByWorldId(sourceWorldId);
        log.info("Found {} entity instances in source world {}", sourceEntities.size(), sourceWorldId);

        int entityCount = 0;
        for (WEntity sourceEntity : sourceEntities) {
            WEntity targetEntity = WEntity.builder()
                    .worldId(targetWorldId)
                    .entityId(sourceEntity.getEntityId())
                    .publicData(sourceEntity.getPublicData())
                    .chunks(sourceEntity.getChunks())
                    .modelId(sourceEntity.getModelId())
                    .position(sourceEntity.getPosition())
                    .rotation(sourceEntity.getRotation())
                    .middlePoint(sourceEntity.getMiddlePoint())
                    .radius(sourceEntity.getRadius())
                    .speed(sourceEntity.getSpeed())
                    .behaviorModel(sourceEntity.getBehaviorModel())
                    .behaviorConfig(sourceEntity.getBehaviorConfig())
                    .enabled(sourceEntity.isEnabled())
                    .build();

            targetEntity.touchCreate();
            entityRepository.save(targetEntity);
            entityCount++;
        }

        log.info("Duplicated {} entity models and {} entity instances from world {} to {}",
                modelCount, entityCount, sourceWorldId, targetWorldId);
    }
}
