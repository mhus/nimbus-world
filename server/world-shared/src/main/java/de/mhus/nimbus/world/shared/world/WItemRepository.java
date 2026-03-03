package de.mhus.nimbus.world.shared.world;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MongoDB Repository for WItem entities.
 */
@Repository
public interface WItemRepository extends MongoRepository<WItem, String> {

    /**
     * Find all items for a specific world.
     */
    List<WItem> findByWorldId(String worldId);

    /**
     * Find all enabled items for a specific world.
     */
    List<WItem> findByWorldIdAndEnabled(String worldId, boolean enabled);

    /**
     * Find item by worldId and itemId.
     */
    Optional<WItem> findByWorldIdAndItemId(String worldId, String itemId);

    /**
     * Delete item by worldId and itemId.
     */
    void deleteByWorldIdAndItemId(String worldId, String itemId);

    /**
     * Find all items for a world with a specific itemType.
     */
    @org.springframework.data.mongodb.repository.Query("{ 'worldId': ?0, 'publicData.itemType': ?1 }")
    List<WItem> findByWorldIdAndItemType(String worldId, String itemType);
}
