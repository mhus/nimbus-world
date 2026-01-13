package de.mhus.nimbus.world.shared.world;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WWorldRepository extends MongoRepository<WWorld, String> {
    Optional<WWorld> findByWorldId(String worldId);
    boolean existsByWorldId(String worldId);
    List<WWorld> findByRegionId(String regionId);

    /**
     * Find worlds with pagination (no filter).
     */
    Page<WWorld> findAllBy(Pageable pageable);

    /**
     * Find worlds with search filter (searches in worldId, name, and description) and pagination.
     * Uses case-insensitive regex search across multiple fields.
     */
    @Query("{ $or: [ " +
            "{ 'worldId': { $regex: ?0, $options: 'i' } }, " +
            "{ 'name': { $regex: ?0, $options: 'i' } }, " +
            "{ 'description': { $regex: ?0, $options: 'i' } } " +
            "] }")
    Page<WWorld> findBySearchQuery(String searchPattern, Pageable pageable);
}

