package de.mhus.nimbus.world.shared.world;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MongoDB Repository for WEntity entities (instances in the world).
 */
@Repository
public interface WEntityRepository extends MongoRepository<WEntity, String> {

    Optional<WEntity> findByWorldIdAndEntityId(String worldId, String entityId);

    List<WEntity> findByWorldId(String worldId);

    List<WEntity> findByWorldIdAndModelId(String worldId, String modelId);

    List<WEntity> findByWorldIdAndEnabled(String worldId, boolean enabled);

    boolean existsByWorldIdAndEntityId(String worldId, String entityId);

    void deleteByWorldIdAndEntityId(String worldId, String entityId);
}
