package de.mhus.nimbus.world.shared.world;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SAssetRepository extends MongoRepository<SAsset, String> {

    List<SAsset> findByWorldId(String worldId);

    Optional<SAsset> findByWorldIdAndPath(String worldId, String path);

    void deleteByWorldIdAndPath(String worldId, String path);

    /**
     * Find assets by worldId with pagination (no filter).
     */
    Page<SAsset> findByWorldId(String worldId, Pageable pageable);

    /**
     * Find assets by worldId with path filter (case-insensitive) and pagination.
     */
    @Query("{ 'worldId': ?0, 'path': { $regex: ?1, $options: 'i' } }")
    Page<SAsset> findByWorldIdAndPathContaining(String worldId, String pathPattern, Pageable pageable);

    /**
     * Count assets by worldId.
     */
    long countByWorldId(String worldId);

    /**
     * Count assets by worldId with path filter.
     */
    @Query(value = "{ 'worldId': ?0, 'path': { $regex: ?1, $options: 'i' } }", count = true)
    long countByWorldIdAndPathContaining(String worldId, String pathPattern);

}

