package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.generated.types.Entity;
import de.mhus.nimbus.generated.types.Vector3;
import de.mhus.nimbus.shared.types.WorldId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
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
    private final WWorldService worldService;

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
        computeAffectedChunks(entity);
        entity.touchUpdate();

        WEntity saved = repository.save(entity);
        log.debug("Saved WEntity: world={}, entityId={}", worldId, entityId);
        return saved;
    }

    @Transactional
    public List<WEntity> saveAll(List<WEntity> entities) {
        entities.forEach(e -> {
            computeAffectedChunks(e);
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
            computeAffectedChunks(entity);
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
     * Delete all entities whose entityId starts with the given prefix.
     * Used to clean up generated entities (e.g., fauna) before regeneration.
     *
     * @param worldId the world identifier (must not be instance or collection)
     * @param prefix  the entityId prefix to match
     * @return number of deleted entities
     */
    @Transactional
    public int deleteByEntityIdPrefix(WorldId worldId, String prefix) {
        if (worldId.isInstance() || worldId.isCollection()) {
            throw new IllegalArgumentException("worldId must be a world id (no instance, no collection)");
        }
        var lookupWorld = worldId.withoutInstance();
        List<WEntity> entities = repository.findByWorldIdAndEntityIdStartingWith(
                lookupWorld.getId(), prefix);
        if (!entities.isEmpty()) {
            repository.deleteAll(entities);
            log.debug("Deleted {} WEntities with prefix '{}' in world {}", entities.size(), prefix, worldId);
        }
        return entities.size();
    }

    /**
     * Delete all entities with a given source that have any of the specified affected chunks.
     * Used to clean up generated entities in specific hex grid areas before regeneration.
     *
     * @param worldId   the world identifier (must not be instance or collection)
     * @param source    the source identifier (e.g., "fauna-generator")
     * @param chunkKeys collection of chunk keys to match against affectedChunks
     * @return number of deleted entities
     */
    @Transactional
    public int deleteBySourceAndAffectedChunks(WorldId worldId, String source, Collection<String> chunkKeys) {
        if (worldId.isInstance() || worldId.isCollection()) {
            throw new IllegalArgumentException("worldId must be a world id (no instance, no collection)");
        }
        var lookupWorld = worldId.withoutInstance();
        List<WEntity> entities = repository.findByWorldIdAndSourceAndAffectedChunksIn(
                lookupWorld.getId(), source, chunkKeys);
        if (!entities.isEmpty()) {
            repository.deleteAll(entities);
            log.debug("Deleted {} WEntities with source '{}' in {} chunks in world {}",
                    entities.size(), source, chunkKeys.size(), worldId);
        }
        return entities.size();
    }

    /**
     * Find all enabled entities that have any of the specified affected chunks.
     * Used for chunk-based entity loading in world-life.
     *
     * @param worldId   the world identifier (must not be instance or collection)
     * @param chunkKeys collection of chunk keys to match against affectedChunks
     * @return list of enabled entities in the specified chunks
     */
    @Transactional(readOnly = true)
    public List<WEntity> findEnabledByChunks(WorldId worldId, Collection<String> chunkKeys) {
        if (worldId.isCollection()) {
            throw new IllegalArgumentException("worldId must not be a collection id");
        }
        var lookupWorld = worldId.withoutInstance();
        return repository.findByWorldIdAndEnabledAndAffectedChunksIn(lookupWorld.getId(), true, chunkKeys);
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

    /**
     * Compute affected chunks for an entity based on its position and the world's chunk size.
     * Includes the chunk at the entity's position and all direct neighbors (3x3 grid).
     */
    private void computeAffectedChunks(WEntity entity) {
        Vector3 pos = entity.getPosition();
        if (pos == null || entity.getWorldId() == null) {
            entity.setAffectedChunks(new ArrayList<>());
            return;
        }
        int chunkSize = getChunkSize(entity.getWorldId());
        if (chunkSize <= 0) {
            entity.setAffectedChunks(new ArrayList<>());
            return;
        }
        int cx = Math.floorDiv((int) pos.getX(), chunkSize);
        int cz = Math.floorDiv((int) pos.getZ(), chunkSize);

        List<String> chunks = new ArrayList<>(9);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                chunks.add((cx + dx) + ":" + (cz + dz));
            }
        }
        entity.setAffectedChunks(chunks);
    }

    private int getChunkSize(String worldId) {
        return worldService.getByWorldId(worldId)
                .filter(w -> w.getPublicData() != null)
                .map(w -> w.getPublicData().getChunkSize())
                .orElse(0);
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
