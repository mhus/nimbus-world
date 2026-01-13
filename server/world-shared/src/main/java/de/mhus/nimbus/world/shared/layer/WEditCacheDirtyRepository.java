package de.mhus.nimbus.world.shared.layer;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * MongoDB Repository for WEditCacheDirty entities.
 */
@Repository
public interface WEditCacheDirtyRepository extends MongoRepository<WEditCacheDirty, String> {

    /**
     * Find dirty entry by worldId and layerDataId.
     *
     * @param worldId World identifier
     * @param layerDataId Layer data identifier
     * @return Dirty entry if found
     */
    Optional<WEditCacheDirty> findByWorldIdAndLayerDataId(String worldId, String layerDataId);

    /**
     * Find all dirty entries for a specific world.
     *
     * @param worldId World identifier
     * @return List of dirty entries
     */
    List<WEditCacheDirty> findByWorldId(String worldId);

    /**
     * Find oldest dirty entries for processing.
     * Orders by creation timestamp ascending.
     *
     * @return List of dirty entries ordered by age
     */
    List<WEditCacheDirty> findAllByOrderByCreatedAtAsc();

    /**
     * Find dirty entries older than specified timestamp.
     *
     * @param timestamp Cutoff timestamp
     * @return List of dirty entries older than timestamp
     */
    List<WEditCacheDirty> findByCreatedAtBefore(Instant timestamp);

    /**
     * Delete dirty entry by worldId and layerDataId.
     *
     * @param worldId World identifier
     * @param layerDataId Layer data identifier
     */
    void deleteByWorldIdAndLayerDataId(String worldId, String layerDataId);

    /**
     * Check if dirty entry exists for worldId and layerDataId.
     *
     * @param worldId World identifier
     * @param layerDataId Layer data identifier
     * @return true if entry exists
     */
    boolean existsByWorldIdAndLayerDataId(String worldId, String layerDataId);
}
