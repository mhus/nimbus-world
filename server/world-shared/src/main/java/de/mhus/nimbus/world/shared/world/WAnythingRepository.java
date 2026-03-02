package de.mhus.nimbus.world.shared.world;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MongoDB Repository for WAnythingEntity.
 * All queries are scoped by worldId.
 */
@Repository
public interface WAnythingRepository extends MongoRepository<WAnything, String> {

    Optional<WAnything> findByWorldIdAndCollectionAndName(String worldId, String collection, String name);

    List<WAnything> findByWorldIdAndCollection(String worldId, String collection);

    List<WAnything> findByWorldIdAndCollectionAndEnabled(String worldId, String collection, boolean enabled);

    List<WAnything> findByWorldIdAndCollectionAndType(String worldId, String collection, String type);

    boolean existsByWorldIdAndCollectionAndName(String worldId, String collection, String name);

    void deleteByWorldIdAndCollectionAndName(String worldId, String collection, String name);
}
