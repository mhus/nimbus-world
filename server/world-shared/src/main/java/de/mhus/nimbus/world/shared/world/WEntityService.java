package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.generated.types.Entity;
import de.mhus.nimbus.shared.types.WorldId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Service for managing WEntity instances in the world.
 * Entities exist separately for each world/zone/instance.
 * No storage functionality supported (always world-instance-specific).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WEntityService {

    private final WEntityRepository repository;

    /**
     * Find entity by entityId.
     * Instances always look up in their world.
     */
    @Transactional(readOnly = true)
    public Optional<WEntity> findByWorldIdAndEntityId(WorldId worldId, String entityId) {
        if (worldId.isCollection()) {
            throw new IllegalArgumentException("worldId must not be a collection id");
        }
        var lookupWorld = worldId.withoutInstance();
        return repository.findByWorldIdAndEntityId(lookupWorld.getId(), entityId);
    }

    /**
     * Find all entities for specific world.
     * Filters out instances.
     */
    @Transactional(readOnly = true)
    public List<WEntity> findByWorldId(WorldId worldId) {
        if (worldId.isCollection()) {
            throw new IllegalArgumentException("worldId must not be a collection id");
        }
        var lookupWorld = worldId.withoutInstance();
        return repository.findByWorldId(lookupWorld.getId());
    }

    /**
     * Find entities by modelId for specific world.
     * Filters out instances.
     */
    @Transactional(readOnly = true)
    public List<WEntity> findByModelId(WorldId worldId, String modelId) {
        if (worldId.isCollection()) {
            throw new IllegalArgumentException("worldId must not be a collection id");
        }
        var lookupWorld = worldId.withoutInstance();
        return repository.findByWorldIdAndModelId(lookupWorld.getId(), modelId);
    }

    /**
     * Find all enabled entities for specific world.
     * Filters out instances.
     */
    @Transactional(readOnly = true)
    public List<WEntity> findAllEnabled(WorldId worldId) {
        if (worldId.isCollection()) {
            throw new IllegalArgumentException("worldId must not be a collection id");
        }
        var lookupWorld = worldId.withoutInstance();
        return repository.findByWorldIdAndEnabled(lookupWorld.getId(), true);
    }

    /**
     * Save or update an entity.
     * Filters out instances.
     */
    @Transactional
    public WEntity save(WorldId worldId, String entityId, Entity publicData, String modelId) {
        if (worldId == null) {
            throw new IllegalArgumentException("worldId required");
        }
        if (Strings.isBlank(entityId)) {
            throw new IllegalArgumentException("entityId required");
        }
        if (publicData == null) {
            throw new IllegalArgumentException("publicData required");
        }
        if (worldId.isInstance() || worldId.isCollection()) {
            throw new IllegalArgumentException("worldId must be a world id (no instance, no collection)");
        }

        WEntity entity = repository.findByWorldIdAndEntityId(worldId.getId(), entityId).orElseGet(() -> {
            WEntity neu = WEntity.builder()
                    .worldId(worldId.getId())
                    .entityId(entityId)
                    .modelId(modelId)
                    .enabled(true)
                    .build();
            neu.touchCreate();
            log.debug("Creating new WEntity: world={}, entityId={}", worldId, entityId);
            return neu;
        });

        entity.setPublicData(publicData);
        entity.setModelId(modelId);
        entity.touchUpdate();

        WEntity saved = repository.save(entity);
        log.debug("Saved WEntity: world={}, entityId={}", worldId, entityId);
        return saved;
    }

    @Transactional
    public List<WEntity> saveAll(List<WEntity> entities) {
        entities.forEach(e -> {
            if (e.getCreatedAt() == null) {
                e.touchCreate();
            }
            e.touchUpdate();
        });
        List<WEntity> saved = repository.saveAll(entities);
        log.debug("Saved {} WEntity entities", saved.size());
        return saved;
    }

    /**
     * Update an entity.
     * Denies out instances and collections.
     */
    @Transactional
    public Optional<WEntity> update(WorldId worldId, String entityId, Consumer<WEntity> updater) {
        if (worldId.isInstance() || worldId.isCollection()) {
            throw new IllegalArgumentException("worldId must be a world id (no instance, no collection)");
        }

        return repository.findByWorldIdAndEntityId(worldId.getId(), entityId).map(entity -> {
            updater.accept(entity);
            entity.touchUpdate();
            WEntity saved = repository.save(entity);
            log.debug("Updated WEntity: world={}, entityId={}", worldId, entityId);
            return saved;
        });
    }

    /**
     * Delete an entity.
     * Denies out instances and collections.
     */
    @Transactional
    public boolean delete(WorldId worldId, String entityId) {
        if (worldId.isInstance() || worldId.isCollection()) {
            throw new IllegalArgumentException("worldId must be a world id (no instance, no collection)");
        }

        return repository.findByWorldIdAndEntityId(worldId.getId(), entityId).map(entity -> {
            repository.delete(entity);
            log.debug("Deleted WEntity: world={}, entityId={}", worldId, entityId);
            return true;
        }).orElse(false);
    }

    @Transactional
    public boolean enable(WorldId worldId, String entityId) {
        return update(worldId, entityId, entity -> entity.setEnabled(true)).isPresent();
    }

    @Transactional
    public boolean disable(WorldId worldId, String entityId) {
        return update(worldId, entityId, entity -> entity.setEnabled(false)).isPresent();
    }

    /**
     * Find all entities for specific world with optional query filter.
     * Filters out instances.
     */
    @Transactional(readOnly = true)
    public List<WEntity> findByWorldIdAndQuery(WorldId worldId, String query) {
        if (worldId.isCollection()) {
            throw new IllegalArgumentException("worldId must not be a collection id");
        }
        var lookupWorld = worldId.withoutInstance();
        List<WEntity> all = repository.findByWorldId(lookupWorld.getId());

        // Apply search filter if provided
        if (query != null && !query.isBlank()) {
            all = filterByQuery(all, query);
        }

        return all;
    }

    private List<WEntity> filterByQuery(List<WEntity> entities, String query) {
        String lowerQuery = query.toLowerCase();
        return entities.stream()
                .filter(entity -> {
                    String entityId = entity.getEntityId();
                    Entity publicData = entity.getPublicData();
                    return (entityId != null && entityId.toLowerCase().contains(lowerQuery)) ||
                            (publicData != null && publicData.getName() != null &&
                                    publicData.getName().toLowerCase().contains(lowerQuery));
                })
                .collect(java.util.stream.Collectors.toList());
    }

}
