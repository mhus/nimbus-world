package de.mhus.nimbus.world.shared.generator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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

    private static final String COLLECTION_NAME = "w_flats";

    private final WFlatRepository wFlatRepository;
    private final MongoTemplate mongoTemplate;

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
    @Deprecated
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

    public WFlat findByWorldAndFlatId(String worldId, String flatId) {
        return wFlatRepository.findByWorldIdAndFlatId(worldId, flatId);
    }

    public boolean exists(String worldId, String flatId) {
        return wFlatRepository.existsByWorldIdAndFlatId(worldId, flatId);
    }

    public void delete(String worldId, String flatId) {
        log.debug("Deleting flat: worldId={}, flatId={}", worldId, flatId);
        wFlatRepository.deleteByWorldIdAndFlatId(worldId, flatId);
    }

    /**
     * Delete ALL flats of a world. Owner-level bulk operation so callers do not
     * touch the WFlat collection directly (data ownership). WFlat stores all data
     * (height maps, columns, extra blocks) inline in MongoDB, so there is no
     * external storage to clean up.
     *
     * @param worldId World identifier
     * @return number of deleted flats
     */
    @Transactional
    public int deleteByWorldId(String worldId) {
        log.info("Deleting flats for world {}", worldId);
        var result = mongoTemplate.remove(
                new Query(Criteria.where("worldId").is(worldId)),
                WFlat.class
        );
        long deleted = result.getDeletedCount();
        log.info("Deleted {} flats for world {}", deleted, worldId);
        return (int) deleted;
    }

    /**
     * Distinct world IDs that have flats (owner-level; avoids callers querying the
     * WFlat collection directly).
     *
     * @return list of distinct world IDs
     */
    public List<String> findDistinctWorldIds() {
        return mongoTemplate.findDistinct(new Query(), "worldId", WFlat.class, String.class);
    }

    /**
     * Duplicate ALL flats of a source world to a target world. Owner-level bulk
     * operation so callers do not touch the WFlat collection directly (data
     * ownership). Uses raw Documents to preserve byte arrays (levels/columns) and
     * nested structures (materials, groups, extraBlocks) exactly.
     *
     * @param sourceWorldId world id to copy flats from
     * @param targetWorldId world id to copy flats to (must already exist)
     * @return number of duplicated flats
     */
    @Transactional
    public int duplicateToWorld(String sourceWorldId, String targetWorldId) {
        log.info("Duplicating flats from world {} to {}", sourceWorldId, targetWorldId);

        Query query = new Query(Criteria.where("worldId").is(sourceWorldId));
        List<Document> sourceDocuments = mongoTemplate.find(query, Document.class, COLLECTION_NAME);
        log.info("Found {} flats in source world {}", sourceDocuments.size(), sourceWorldId);

        int duplicatedCount = 0;
        Instant now = Instant.now();

        for (Document source : sourceDocuments) {
            Document target = new Document(source);
            target.remove("_id");
            target.put("worldId", targetWorldId);
            target.put("createdAt", now);
            target.put("updatedAt", now);

            mongoTemplate.save(target, COLLECTION_NAME);
            duplicatedCount++;
        }

        log.info("Duplicated {} flats from world {} to {}",
                duplicatedCount, sourceWorldId, targetWorldId);
        return duplicatedCount;
    }
}
