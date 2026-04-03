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

    Optional<WEntityModel> findByWorldIdAndName(String worldId, String name);

    List<WEntityModel> findByWorldId(String worldId);

    List<WEntityModel> findByWorldIdAndEnabled(String worldId, boolean enabled);

    boolean existsByWorldIdAndName(String worldId, String name);

    void deleteByWorldIdAndName(String worldId, String name);
}
