package de.mhus.nimbus.world.shared.world;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MongoDB Repository for WItemPosition entities.
 * Manages item positions in world chunks.
 */
@Repository
public interface WItemPositionRepository extends MongoRepository<WItemPosition, String> {

    /**
     * Find all items in a world.
     *
     * @param worldId World identifier
     * @return List of all item positions in the world
     */
    List<WItemPosition> findByWorldId(String worldId);

    /**
     * Check if an item exists.
     *
     * @param worldId World identifier
     * @param itemId Item identifier
     * @return True if item exists
     */
    boolean existsByWorldIdAndItemId(String worldId, String itemId);

    /**
     * Delete a specific item.
     *
     * @param worldId World identifier
     * @param itemId Item identifier
     */
    void deleteByWorldIdAndItemId(String worldId, String itemId);

    /**
     * Find enabled items in a chunk.
     *
     * @param worldId World identifier
     * @param chunk Chunk key
     * @param enabled Enabled flag
     * @return List of item positions matching criteria
     */
    List<WItemPosition> findByWorldIdAndChunkAndEnabled(
            String worldId, String chunk, boolean enabled);

    Optional<WItemPosition> findByWorldIdAndItemId(String worldId, String itemId);

    /**
     * Find all items in a chunk (including disabled/tombstones).
     * Used for COW instance layer queries where tombstones must be included in merge.
     *
     * @param worldId World identifier
     * @param chunk Chunk key
     * @return List of all item positions in the chunk
     */
    List<WItemPosition> findByWorldIdAndChunk(String worldId, String chunk);

    /**
     * Delete all item positions for a world.
     * Used for instance cleanup (hard delete of all COW data).
     */
    void deleteByWorldId(String worldId);
}
