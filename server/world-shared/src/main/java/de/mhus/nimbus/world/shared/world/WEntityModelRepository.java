package de.mhus.nimbus.world.shared.world;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MongoDB Repository for WEntityModel entities.
 */
@Repository
public interface WEntityModelRepository extends MongoRepository<WEntityModel, String> {

    Optional<WEntityModel> findByWorldIdAndModelId(String worldId, String modelId);

    List<WEntityModel> findByWorldId(String worldId);

    List<WEntityModel> findByWorldIdAndEnabled(String worldId, boolean enabled);

    boolean existsByWorldIdAndModelId(String worldId, String modelId);

    void deleteByWorldIdAndModelId(String worldId, String modelId);
}
