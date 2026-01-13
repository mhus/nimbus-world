package de.mhus.nimbus.world.shared.layer;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for WLayerModel entities.
 *
 * Do not get a full list of WLayerModel, because it can be heavy memory load.
 *
 * Get single parameters or a list of ids and load every model step by step with the id.
 *
 */
@Repository
public interface WLayerModelRepository extends MongoRepository<WLayerModel, String> {

    /**
     * Find all model IDs by layerDataId (1:N relationship), sorted by order.
     * NEW CONCEPT: Multiple WLayerModel documents can share the same layerDataId.
     * Returns only IDs to avoid heavy memory load - load full models step by step using findById.
     * Results are sorted by order field for correct overlay sequence.
     *
     * NOTE: Using standard query method instead of @Query with projection,
     * because projection to List<String> can be problematic in some MongoDB driver versions.
     */
    List<WLayerModel> findByLayerDataIdOrderByOrder(String layerDataId);

    /**
     * Count models by layerDataId.
     */
    long countByLayerDataId(String layerDataId);

    /**
     * Find first model by layerDataId (1:1 relationship - deprecated).
     * @deprecated Use findIdsByLayerDataId and load step by step for new concept (1:N relationship)
     */
    @Deprecated
    Optional<WLayerModel> findFirstByLayerDataId(String layerDataId);

    /**
     * Delete model by layerDataId.
     */
    void deleteByLayerDataId(String layerDataId);

    /**
     * Find models by worldId and name.
     * Used for resolving referenceModelId in format "worldId/name".
     * If multiple models match, the first one will be used.
     */
    List<WLayerModel> findByWorldIdAndName(String worldId, String name);
}
