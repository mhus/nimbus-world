package de.mhus.nimbus.world.shared.layer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Central service for dirty chunk management.
 * Used by all world-* modules to mark chunks for regeneration.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WDirtyChunkService {

    private final WDirtyChunkRepository dirtyChunkRepository;

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

        for (String chunkKey : chunkKeys) {
            markChunkDirty(worldId, chunkKey, reason);
        }

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
        return dirtyChunkRepository.findByWorldIdOrderByTimestampAsc(worldId)
                .stream()
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Get all distinct world IDs that have dirty chunks.
     *
     * @return List of world IDs with dirty chunks
     */
    @Transactional(readOnly = true)
    public List<String> getWorldIdsWithDirtyChunks() {
        return dirtyChunkRepository.findAllBy().stream()
                .map(WDirtyChunk::getWorldId)
                .distinct()
                .collect(Collectors.toList());
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
}
