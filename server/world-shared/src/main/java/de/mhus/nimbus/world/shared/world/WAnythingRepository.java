package de.mhus.nimbus.world.shared.world;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MongoDB Repository for WAnythingEntity.
 * Provides query methods for flexible data retrieval by region, world, collection, and name.
 */
@Repository
public interface WAnythingRepository extends MongoRepository<WAnything, String> {

    Optional<WAnything> findByCollectionAndName(String collection, String name);

    Optional<WAnything> findByWorldIdAndCollectionAndName(String worldId, String collection, String name);

    Optional<WAnything> findByRegionIdAndCollectionAndName(String regionId, String collection, String name);

    Optional<WAnything> findByRegionIdAndWorldIdAndCollectionAndName(String regionId, String worldId, String collection, String name);

    List<WAnything> findByCollection(String collection);

    List<WAnything> findByWorldIdAndCollection(String worldId, String collection);

    List<WAnything> findByRegionIdAndCollection(String regionId, String collection);

    List<WAnything> findByRegionIdAndWorldIdAndCollection(String regionId, String worldId, String collection);

    List<WAnything> findByCollectionAndEnabled(String collection, boolean enabled);

    List<WAnything> findByWorldIdAndCollectionAndEnabled(String worldId, String collection, boolean enabled);

    List<WAnything> findByRegionIdAndCollectionAndEnabled(String regionId, String collection, boolean enabled);

    List<WAnything> findByRegionIdAndWorldIdAndCollectionAndEnabled(String regionId, String worldId, String collection, boolean enabled);

    List<WAnything> findByCollectionAndType(String collection, String type);

    List<WAnything> findByWorldIdAndCollectionAndType(String worldId, String collection, String type);

    List<WAnything> findByRegionIdAndCollectionAndType(String regionId, String collection, String type);

    List<WAnything> findByRegionIdAndWorldIdAndCollectionAndType(String regionId, String worldId, String collection, String type);

    boolean existsByCollectionAndName(String collection, String name);

    boolean existsByWorldIdAndCollectionAndName(String worldId, String collection, String name);

    boolean existsByRegionIdAndCollectionAndName(String regionId, String collection, String name);

    boolean existsByRegionIdAndWorldIdAndCollectionAndName(String regionId, String worldId, String collection, String name);

    void deleteByCollectionAndName(String collection, String name);

    void deleteByWorldIdAndCollectionAndName(String worldId, String collection, String name);

    void deleteByRegionIdAndCollectionAndName(String regionId, String collection, String name);

    void deleteByRegionIdAndWorldIdAndCollectionAndName(String regionId, String worldId, String collection, String name);
}
