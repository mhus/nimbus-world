package de.mhus.nimbus.shared.storage;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for StorageData chunks.
 * Provides query methods for chunk-based storage operations.
 */
@Repository
public interface StorageDataRepository extends MongoRepository<StorageData, String> {

    /**
     * Load a single chunk by UUID and index.
     * Used for streaming one chunk at a time to minimize memory usage.
     *
     * @param uuid  Logical storage identifier
     * @param index Chunk index (0-based)
     * @return StorageData chunk or null if not found
     */
    StorageData findByUuidAndIndex(String uuid, int index);

    /**
     * Get the final chunk for metadata retrieval (total size, createdAt).
     * The final chunk has isFinal=true and contains the complete file size.
     *
     * @param uuid Logical storage identifier
     * @return Final StorageData chunk or null if not found
     */
    StorageData findByUuidAndIsFinalTrue(String uuid);

    /**
     * Delete all chunks for a given UUID.
     * Called by the cleanup scheduler after the configured delay period.
     *
     * @param uuid Logical storage identifier
     */
    void deleteByUuid(String uuid);

    /**
     * Count chunks for a given UUID.
     * Used for logging and debugging purposes.
     *
     * @param uuid Logical storage identifier
     * @return Number of chunks
     */
    long countByUuid(String uuid);

    List<StorageData> findAllByUuidAndIsFinalTrue(String storageId);

    List<StorageData> findAllByUuidAndIndex(String uuid, int currentChunkIndex);

    List<StorageData> findAllByUuid(String storageId);
}
