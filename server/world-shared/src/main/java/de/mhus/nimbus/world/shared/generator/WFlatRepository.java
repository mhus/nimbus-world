package de.mhus.nimbus.world.shared.generator;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * MongoDB Repository for WFlat entities.
 * Provides CRUD operations and custom queries for flat terrain data.
 */
@Repository
public interface WFlatRepository extends MongoRepository<WFlat, String> {

    /**
     * Find flat by world ID, layer data ID, and flat ID.
     * @param worldId World identifier
     * @param layerDataId Layer data identifier
     * @param flatId Flat identifier
     * @return Optional containing the flat if found
     */
    Optional<WFlat> findByWorldIdAndLayerDataIdAndFlatId(String worldId, String layerDataId, String flatId);

    /**
     * Find all flats for a specific world and layer.
     * @param worldId World identifier
     * @param layerDataId Layer data identifier
     * @return List of flats
     */
    List<WFlat> findByWorldIdAndLayerDataId(String worldId, String layerDataId);

    /**
     * Find all flats for a specific world.
     * @param worldId World identifier
     * @return List of flats
     */
    List<WFlat> findByWorldId(String worldId);

    /**
     * Delete flat by world ID, layer data ID, and flat ID.
     * @param worldId World identifier
     * @param layerDataId Layer data identifier
     * @param flatId Flat identifier
     */
    void deleteByWorldIdAndLayerDataIdAndFlatId(String worldId, String layerDataId, String flatId);

    /**
     * Delete all flats for a specific world and layer.
     * @param worldId World identifier
     * @param layerDataId Layer data identifier
     */
    void deleteByWorldIdAndLayerDataId(String worldId, String layerDataId);

    /**
     * Check if flat exists by world ID, layer data ID, and flat ID.
     * @param worldId World identifier
     * @param layerDataId Layer data identifier
     * @param flatId Flat identifier
     * @return true if exists, false otherwise
     */
    boolean existsByWorldIdAndLayerDataIdAndFlatId(String worldId, String layerDataId, String flatId);
}
