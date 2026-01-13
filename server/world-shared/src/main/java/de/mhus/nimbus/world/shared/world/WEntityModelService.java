package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.generated.types.EntityModel;
import de.mhus.nimbus.shared.types.WorldId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Service for managing WEntityModel entities.
 * Entity models are always stored in the @region collection and shared across the entire region.
 * Branches cannot have their own entity models.
 * Entity models support storage functionality with default 'r' (region), but NOT 'w' (world).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WEntityModelService {

    private final WEntityModelRepository repository;

    /**
     * Find entity model by modelId.
     * Always looks up in the region collection (shared across entire region).
     */
    @Transactional(readOnly = true)
    public Optional<WEntityModel> findByModelId(WorldId worldId, String modelId) {
        var regionWorldId = worldId.toRegionWorldId();
        return repository.findByWorldIdAndModelId(regionWorldId.getId(), modelId);
    }

    /**
     * Find all entity models for the region.
     * Always looks up in the region collection (shared across entire region).
     */
    @Transactional(readOnly = true)
    public List<WEntityModel> findByWorldId(WorldId worldId) {
        var regionWorldId = worldId.toRegionWorldId();
        return repository.findByWorldId(regionWorldId.getId());
    }

    /**
     * Find all enabled entity models for the region.
     * Always looks up in the region collection (shared across entire region).
     */
    @Transactional(readOnly = true)
    public List<WEntityModel> findAllEnabled(WorldId worldId) {
        var regionWorldId = worldId.toRegionWorldId();
        return repository.findByWorldIdAndEnabled(regionWorldId.getId(), true);
    }

    /**
     * Save or update an entity model.
     * Always saves to region collection (shared across entire region).
     */
    @Transactional
    public WEntityModel save(WorldId worldId, String modelId, EntityModel publicData) {
        if (modelId == null) {
            throw new IllegalArgumentException("modelId required");
        }
        if (publicData == null) {
            throw new IllegalArgumentException("publicData required");
        }

        var regionWorldId = worldId.toRegionWorldId();

        WEntityModel entity = repository.findByWorldIdAndModelId(regionWorldId.getId(), modelId).orElseGet(() -> {
            WEntityModel neu = WEntityModel.builder()
                    .modelId(modelId)
                    .worldId(regionWorldId.getId())
                    .enabled(true)
                    .build();
            neu.touchCreate();
            log.debug("Creating new WEntityModel: {}", modelId);
            return neu;
        });

        entity.setPublicData(publicData);
        entity.touchUpdate();

        WEntityModel saved = repository.save(entity);
        log.debug("Saved WEntityModel: {}", modelId);
        return saved;
    }

    @Transactional
    public List<WEntityModel> saveAll(WorldId worldId, List<WEntityModel> entities) {
        entities.forEach(e -> {
            if (e.getCreatedAt() == null) {
                e.touchCreate();
            }
            e.touchUpdate();
        });
        List<WEntityModel> saved = repository.saveAll(entities);
        log.debug("Saved {} WEntityModel entities", saved.size());
        return saved;
    }

    /**
     * Update an entity model.
     * Always updates in region collection.
     */
    @Transactional
    public Optional<WEntityModel> update(WorldId worldId, String modelId, Consumer<WEntityModel> updater) {
        var regionWorldId = worldId.toRegionWorldId();
        return repository.findByWorldIdAndModelId(regionWorldId.getId(), modelId).map(entity -> {
            updater.accept(entity);
            entity.touchUpdate();
            WEntityModel saved = repository.save(entity);
            log.debug("Updated WEntityModel: {}", modelId);
            return saved;
        });
    }

    /**
     * Delete an entity model.
     * Always deletes from region collection.
     */
    @Transactional
    public boolean delete(WorldId worldId, String modelId) {
        var regionWorldId = worldId.toRegionWorldId();
        return repository.findByWorldIdAndModelId(regionWorldId.getId(), modelId).map(entity -> {
            repository.delete(entity);
            log.debug("Deleted WEntityModel: {}", modelId);
            return true;
        }).orElse(false);
    }

    @Transactional
    public boolean disable(WorldId worldId, String modelId) {
        return update(worldId, modelId, entity -> entity.setEnabled(false)).isPresent();
    }

    @Transactional
    public boolean enable(WorldId worldId, String modelId) {
        return update(worldId, modelId, entity -> entity.setEnabled(true)).isPresent();
    }

    /**
     * Find all entity models for the region with optional query filter.
     * Always looks up in the region collection (shared across entire region).
     */
    @Transactional(readOnly = true)
    public List<WEntityModel> findByWorldIdAndQuery(WorldId worldId, String query) {
        var regionWorldId = worldId.toRegionWorldId();
        List<WEntityModel> all = repository.findByWorldId(regionWorldId.getId());

        // Apply search filter if provided
        if (query != null && !query.isBlank()) {
            all = filterByQuery(all, query);
        }

        return all;
    }

    private List<WEntityModel> filterByQuery(List<WEntityModel> models, String query) {
        String lowerQuery = query.toLowerCase();
        return models.stream()
                .filter(model -> {
                    String modelId = model.getModelId();
                    return (modelId != null && modelId.toLowerCase().contains(lowerQuery));
                })
                .collect(java.util.stream.Collectors.toList());
    }

}
