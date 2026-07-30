package de.mhus.nimbus.world.shared.layer;

import de.mhus.nimbus.world.shared.world.WHexGrid;
import de.mhus.nimbus.world.shared.world.WWorld;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Central service for dirty chunk management.
 * Used by all world-* modules to mark chunks for regeneration.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WDirtyChunkService {

    private final WDirtyChunkRepository dirtyChunkRepository;
    private final MongoTemplate mongoTemplate;

    /**
     * Mark a chunk as dirty (needs regeneration).
     * If already dirty, updates the timestamp and reason.
     *
     * @param worldId  World identifier
     * @param chunkKey Chunk key (format: "cx:cz")
     * @param reason   Reason for marking dirty
     */
    @Transactional
    public void markChunkDirty(String worldId, String chunkKey, String reason) {
        Optional<WDirtyChunk> existingOpt = dirtyChunkRepository
                .findByWorldIdAndChunkKey(worldId, chunkKey);

        if (existingOpt.isPresent()) {
            // Update existing entry
            WDirtyChunk existing = existingOpt.get();
            existing.touch();
            existing.setReason(reason);
            dirtyChunkRepository.save(existing);
            log.trace("Updated dirty chunk: world={} chunk={} reason={}",
                    worldId, chunkKey, reason);
        } else {
            // Create new entry
            WDirtyChunk dirtyChunk = WDirtyChunk.builder()
                    .worldId(worldId)
                    .chunkKey(chunkKey)
                    .reason(reason)
                    .build();
            dirtyChunk.touch();
            dirtyChunkRepository.save(dirtyChunk);
            log.debug("Marked chunk dirty: world={} chunk={} reason={}",
                    worldId, chunkKey, reason);
        }
    }

    /**
     * Mark multiple chunks as dirty.
     *
     * @param worldId   World identifier
     * @param chunkKeys List of chunk keys
     * @param reason    Reason for marking dirty
     */
    @Transactional
    public void markChunksDirty(String worldId, List<String> chunkKeys, String reason) {
        if (chunkKeys == null || chunkKeys.isEmpty()) {
            return;
        }

        // Single unordered bulk upsert instead of a find+save per chunk (was
        // O(2N) round-trips; callers can pass thousands of chunks).
        Instant now = Instant.now();
        BulkOperations bulk = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, WDirtyChunk.class);
        for (String chunkKey : chunkKeys) {
            Query query = new Query(Criteria.where("worldId").is(worldId).and("chunkKey").is(chunkKey));
            Update update = new Update()
                    .set("worldId", worldId)
                    .set("chunkKey", chunkKey)
                    .set("timestamp", now)
                    .set("reason", reason);
            bulk.upsert(query, update);
        }
        bulk.execute();

        log.info("Marked {} chunks dirty: world={} reason={}",
                chunkKeys.size(), worldId, reason);
    }

    /**
     * Clear a dirty chunk (after successful regeneration).
     *
     * @param worldId  World identifier
     * @param chunkKey Chunk key
     */
    @Transactional
    public void clearDirtyChunk(String worldId, String chunkKey) {
        dirtyChunkRepository.deleteByWorldIdAndChunkKey(worldId, chunkKey);
        log.debug("Cleared dirty chunk: world={} chunk={}", worldId, chunkKey);
    }

    /**
     * Check if a chunk is marked as dirty.
     *
     * @param worldId  World identifier
     * @param chunkKey Chunk key
     * @return true if chunk is dirty
     */
    @Transactional(readOnly = true)
    public boolean isDirty(String worldId, String chunkKey) {
        return dirtyChunkRepository.existsByWorldIdAndChunkKey(worldId, chunkKey);
    }

    /**
     * Get dirty chunks for a world, ordered by timestamp (oldest first).
     * Limits the result to specified count.
     *
     * @param worldId World identifier
     * @param limit   Maximum number of chunks to return
     * @return List of dirty chunks
     */
    @Transactional(readOnly = true)
    public List<WDirtyChunk> getDirtyChunks(String worldId, int limit) {
        // Limit database-side instead of loading the whole collection into memory.
        return dirtyChunkRepository.findByWorldIdOrderByTimestampAsc(worldId, PageRequest.of(0, limit));
    }

    /**
     * Get all distinct world IDs that have dirty chunks.
     *
     * @return List of world IDs with dirty chunks
     */
    @Transactional(readOnly = true)
    public List<String> getWorldIdsWithDirtyChunks() {
        return dirtyChunkRepository.findDistinctWorldIds();
    }

    /**
     * Count dirty chunks for a world (monitoring).
     *
     * @param worldId World identifier
     * @return Number of dirty chunks
     */
    @Transactional(readOnly = true)
    public long countDirtyChunks(String worldId) {
        return dirtyChunkRepository.countByWorldId(worldId);
    }

    /**
     * Delete ALL dirty chunk markers of a world. Owner-level bulk operation so
     * callers do not query the WDirtyChunk collection directly (data ownership).
     *
     * @param worldId World identifier
     * @return number of deleted dirty chunk markers
     */
    @Transactional
    public long deleteByWorldId(String worldId) {
        long deleted = mongoTemplate.remove(
                new Query(Criteria.where("worldId").is(worldId)), WDirtyChunk.class).getDeletedCount();
        log.info("Deleted {} dirty chunks for world {}", deleted, worldId);
        return deleted;
    }

    /**
     * Distinct world IDs that have dirty chunks (owner-level; avoids callers
     * querying the WDirtyChunk collection directly). Alias for
     * {@link #getWorldIdsWithDirtyChunks()} following the shared naming convention.
     *
     * @return List of world IDs with dirty chunks
     */
    @Transactional(readOnly = true)
    public List<String> findDistinctWorldIds() {
        return dirtyChunkRepository.findDistinctWorldIds();
    }

    public Set<String> markHexGridDirty(WWorld world, WHexGrid hexGrid, String reason) {
        // Get all affected chunk keys
        java.util.Set<String> affectedChunks = hexGrid.getAffectedChunkKeys(world);

        // Mark all chunks as dirty
        markChunksDirty(world.getWorldId(), new java.util.ArrayList<>(affectedChunks), reason);

        return affectedChunks;
    }
}
