package de.mhus.nimbus.world.shared.generator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing WFlat entities.
 * Handles business logic for flat terrain data operations.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WFlatService {

    private final WFlatRepository wFlatRepository;

    /**
     * Create a new flat and persist it to database.
     * @param flat The flat to create
     * @return The persisted flat with generated ID
     */
    @Transactional
    public WFlat create(WFlat flat) {
        log.debug("Creating new flat: worldId={}, layerDataId={}, flatId={}",
                flat.getWorldId(), flat.getLayerDataId(), flat.getFlatId());

        flat.touchCreate();
        WFlat saved = wFlatRepository.save(flat);

        log.info("Created flat with id={}", saved.getId());
        return saved;
    }

    /**
     * Update an existing flat.
     * @param flat The flat to update
     * @return The updated flat
     */
    @Transactional
    public WFlat update(WFlat flat) {
        log.debug("Updating flat: id={}, worldId={}, layerDataId={}, flatId={}",
                flat.getId(), flat.getWorldId(), flat.getLayerDataId(), flat.getFlatId());

        flat.touchUpdate();
        WFlat saved = wFlatRepository.save(flat);

        log.info("Updated flat with id={}", saved.getId());
        return saved;
    }

    /**
     * Find flat by database ID.
     * @param id Database ID
     * @return Optional containing the flat if found
     */
    public Optional<WFlat> findById(String id) {
        log.debug("Finding flat by id={}", id);
        return wFlatRepository.findById(id);
    }

    /**
     * Find flat by world ID, layer data ID, and flat ID.
     * @param worldId World identifier
     * @param layerDataId Layer data identifier
     * @param flatId Flat identifier
     * @return Optional containing the flat if found
     */
    public Optional<WFlat> findByWorldIdAndLayerDataIdAndFlatId(String worldId, String layerDataId, String flatId) {
        log.debug("Finding flat: worldId={}, layerDataId={}, flatId={}", worldId, layerDataId, flatId);
        return wFlatRepository.findByWorldIdAndLayerDataIdAndFlatId(worldId, layerDataId, flatId);
    }

    /**
     * Find all flats for a specific world and layer.
     * @param worldId World identifier
     * @param layerDataId Layer data identifier
     * @return List of flats
     */
    public List<WFlat> findByWorldIdAndLayerDataId(String worldId, String layerDataId) {
        log.debug("Finding flats: worldId={}, layerDataId={}", worldId, layerDataId);
        return wFlatRepository.findByWorldIdAndLayerDataId(worldId, layerDataId);
    }

    /**
     * Find all flats for a specific world.
     * @param worldId World identifier
     * @return List of flats
     */
    public List<WFlat> findByWorldId(String worldId) {
        log.debug("Finding flats for worldId={}", worldId);
        return wFlatRepository.findByWorldId(worldId);
    }

    /**
     * Find all flats.
     * @return List of all flats
     */
    public List<WFlat> findAll() {
        log.debug("Finding all flats");
        return wFlatRepository.findAll();
    }

    /**
     * Check if flat exists by world ID, layer data ID, and flat ID.
     * @param worldId World identifier
     * @param layerDataId Layer data identifier
     * @param flatId Flat identifier
     * @return true if exists, false otherwise
     */
    public boolean exists(String worldId, String layerDataId, String flatId) {
        log.debug("Checking if flat exists: worldId={}, layerDataId={}, flatId={}", worldId, layerDataId, flatId);
        return wFlatRepository.existsByWorldIdAndLayerDataIdAndFlatId(worldId, layerDataId, flatId);
    }

    /**
     * Delete flat by database ID.
     * @param id Database ID
     */
    @Transactional
    public void deleteById(String id) {
        log.debug("Deleting flat by id={}", id);
        wFlatRepository.deleteById(id);
        log.info("Deleted flat with id={}", id);
    }

    /**
     * Delete flat by world ID, layer data ID, and flat ID.
     * @param worldId World identifier
     * @param layerDataId Layer data identifier
     * @param flatId Flat identifier
     */
    @Transactional
    public void delete(String worldId, String layerDataId, String flatId) {
        log.debug("Deleting flat: worldId={}, layerDataId={}, flatId={}", worldId, layerDataId, flatId);
        wFlatRepository.deleteByWorldIdAndLayerDataIdAndFlatId(worldId, layerDataId, flatId);
        log.info("Deleted flat: worldId={}, layerDataId={}, flatId={}", worldId, layerDataId, flatId);
    }

    /**
     * Delete all flats for a specific world and layer.
     * @param worldId World identifier
     * @param layerDataId Layer data identifier
     */
    @Transactional
    public void deleteByWorldIdAndLayerDataId(String worldId, String layerDataId) {
        log.debug("Deleting flats: worldId={}, layerDataId={}", worldId, layerDataId);
        wFlatRepository.deleteByWorldIdAndLayerDataId(worldId, layerDataId);
        log.info("Deleted flats: worldId={}, layerDataId={}", worldId, layerDataId);
    }

    /**
     * Save or update a flat. If the flat doesn't have an ID, creates it. Otherwise updates it.
     * @param flat The flat to save or update
     * @return The persisted flat
     */
    @Transactional
    public WFlat saveOrUpdate(WFlat flat) {
        if (flat.getId() == null) {
            return create(flat);
        } else {
            return update(flat);
        }
    }
}
