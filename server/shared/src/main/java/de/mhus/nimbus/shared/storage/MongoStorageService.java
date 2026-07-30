package de.mhus.nimbus.shared.storage;

import de.mhus.nimbus.shared.types.SchemaVersion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * MongoDB-based storage service with automatic chunking support.
 *
 * Implementation strategy:
 * - Files are split into configurable chunks (default 512KB)
 * - Each chunk is stored as a separate MongoDB document
 * - UUID identifies file version across all chunks
 * - Soft-delete with 5-minute delay for safe cleanup
 * - Stream-based API for memory-efficient large file handling
 *
 * Memory usage: O(chunk-size) regardless of file size
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MongoStorageService extends StorageService {

    private final StorageDataRepository storageDataRepository;
    private final StorageDeleteRepository storageDeleteRepository;
    private final MongoTemplate mongoTemplate;

    @Value("${nimbus.storage.chunk-size:524288}")
    private int chunkSize; // 512KB default

    @Override
    public StorageInfo store(String schema, SchemaVersion schemaVersion, String worldId, String path, InputStream stream) {
        String uuid = UUID.randomUUID().toString();
        return store(uuid, schema, schemaVersion, worldId, path, stream);
    }

    @Transactional
    protected StorageInfo store(String storageId, String schema, SchemaVersion schemaVersion, String worldId, String path, InputStream stream) {
        if (stream == null) {
            log.error("Cannot store null stream for path: {}", path);
            return null;
        }

        Date createdAt = new Date();

        try (ChunkedOutputStream outputStream = new ChunkedOutputStream(
                storageDataRepository, storageId, schema, schemaVersion.toString(), worldId, path, chunkSize, createdAt)) {

            // Copy from input stream to chunked output stream
            // ChunkedOutputStream automatically splits into chunks and saves to MongoDB
            stream.transferTo(outputStream);

            long totalSize = outputStream.getTotalBytesWritten();

            log.debug("Stored file: uuid={} path={} size={}", storageId, path, totalSize);

            return new StorageInfo(storageId, totalSize, createdAt, worldId, path, schema, schemaVersion);

        } catch (IOException e) {
            log.error("Error storing file: path={}", path, e);
            throw new RuntimeException("Failed to store file", e);
        }
    }

    @Override
    public InputStream load(String storageId) {
        if (storageId == null || storageId.isBlank()) {
            log.error("Cannot load with null/empty storageId");
            return null;
        }

        try {
            // ChunkedInputStream automatically loads chunks one at a time on-demand
            return new ChunkedInputStream(storageDataRepository, storageId);

        } catch (Exception e) {
            log.error("Error loading storageId: {}", storageId, e);
            return null;
        }
    }

    @Override
    @Transactional
    public void delete(String storageId) {
        if (storageId == null || storageId.isBlank()) {
            log.error("Cannot delete with null/empty storageId");
            return;
        }

        // Schedule deletion in 5 minutes (soft-delete)
        // This allows ongoing read operations to complete safely
        Date deletedAt = new Date(System.currentTimeMillis() + 5 * 60 * 1000);

        StorageDelete deleteEntry = StorageDelete.builder()
                .storageId(storageId)
                .deletedAt(deletedAt)
                .build();

        storageDeleteRepository.save(deleteEntry);

        log.debug("Scheduled deletion: storageId={} at={}", storageId, deletedAt);
    }

    @Override
    @Transactional
    public StorageInfo update(String schema, SchemaVersion schemaVersion, String storageId, InputStream stream) {
        if (storageId == null || storageId.isBlank()) {
            log.error("Cannot update with null/empty storageId");
            return null;
        }

        if (stream == null) {
            log.error("Cannot update with null stream for storageId: {}", storageId);
            return null;
        }

        // Get old metadata for path reference
        StorageData oldFinalChunk = storageDataRepository.findByUuidAndIsFinalTrue(storageId);
        if (oldFinalChunk == null) {
            log.warn("No existing storage found for update: storageId={}", storageId);
            throw new IllegalArgumentException("Storage ID not found: " + storageId);
        }
        String path = oldFinalChunk.getPath();
        String worldId = oldFinalChunk.getWorldId();
        if (schema == null) schema = oldFinalChunk.getSchema();
        if (schemaVersion == null) schemaVersion = SchemaVersion.create(oldFinalChunk.getSchemaVersion());

        // Store new version with new UUID
        StorageInfo newInfo = store(schema, schemaVersion, worldId, path, stream);

        // Schedule old version for deletion
        if (newInfo != null) {
            delete(storageId);
            log.debug("Updated storage: oldId={} newId={}", storageId, newInfo.id());
        }

        return newInfo;
    }

    @Override
    @Transactional
    public StorageInfo replace(String schema, SchemaVersion schemaVersion, String storageId, InputStream stream) {
        if (storageId == null || storageId.isBlank()) {
            log.error("Cannot update with null/empty storageId");
            return null;
        }

        if (stream == null) {
            log.error("Cannot update with null stream for storageId: {}", storageId);
            return null;
        }

        // Get old metadata for path reference
        StorageData oldChunk = storageDataRepository.findByUuidAndIsFinalTrue(storageId);
        if (oldChunk == null) {
            log.warn("No existing storage found for update: storageId={}", storageId);
            throw new IllegalArgumentException("Storage ID not found: " + storageId);
        }
        String path = oldChunk.getPath();
        String worldId = oldChunk.getWorldId();
        storageId = oldChunk.getUuid(); // Keep same UUID but be sure to use existing one
        if (schema == null) schema = oldChunk.getSchema();
        if (schemaVersion == null) schemaVersion = SchemaVersion.create(oldChunk.getSchemaVersion());

        // delete the old data immediately
        List<StorageData> deleteMe = storageDataRepository.findAllByUuid(storageId);
        deleteMe.forEach(chunk -> {
            storageDataRepository.delete(chunk);
        });

        // Store new version with same UUID
        StorageInfo newInfo = store(storageId, schema, schemaVersion, worldId, path, stream);

        return newInfo;
    }

    @Override
    public StorageInfo info(String storageId) {
        if (storageId == null || storageId.isBlank()) {
            log.error("Cannot get info with null/empty storageId");
            return null;
        }
        try {
            StorageData finalChunk = storageDataRepository.findByUuidAndIsFinalTrue(storageId);

            if (finalChunk == null) {
                log.warn("No final chunk found for storageId: {}", storageId);
                return null;
            }

            return new StorageInfo(
                    storageId,
                    finalChunk.getSize(),
                    finalChunk.getCreatedAt(),
                    finalChunk.getWorldId(),
                    finalChunk.getPath(),
                    finalChunk.getSchema(),
                    SchemaVersion.create(finalChunk.getSchemaVersion())
            );
        } catch (IncorrectResultSizeDataAccessException e) {
            log.error("Multiple final chunks found for storageId: {}", storageId, e);
            // get all
            List<StorageData> finalChunks = storageDataRepository.findAllByUuidAndIsFinalTrue(storageId);
            // sort by created
            finalChunks.sort((o1, o2) -> o2.getCreatedAt().compareTo(o1.getCreatedAt()));
            // get youngest and delete the rest
            var finalChunk = finalChunks.removeLast();
            finalChunks.forEach(chunk -> {
                log.info("Deleting duplicate final chunk id={} createdAt={}", chunk.getId(), chunk.getCreatedAt());
                storageDataRepository.delete(chunk);
            });
            return new StorageInfo(
                    storageId,
                    finalChunk.getSize(),
                    finalChunk.getCreatedAt(),
                    finalChunk.getWorldId(),
                    finalChunk.getPath(),
                    finalChunk.getSchema(),
                    SchemaVersion.create(finalChunk.getSchemaVersion())
            );
        }
    }

    @Override
    public StorageListResult listFinal(String query, int offset, int limit) {
        // Build MongoDB query restricted to final chunks (one per stored object).
        Query mongoQuery = new Query();
        mongoQuery.addCriteria(Criteria.where("isFinal").is(true));

        // Add search criteria if a query is provided.
        if (query != null && !query.trim().isEmpty()) {
            // Escape the user input to a literal pattern so regex metacharacters
            // cannot inject a catastrophic/backtracking expression (ReDoS) or
            // a ".*" match-all against the shared MongoDB instance.
            String searchTerm = Pattern.quote(query.trim());
            Criteria searchCriteria = new Criteria().orOperator(
                    Criteria.where("uuid").regex(searchTerm, "i"),
                    Criteria.where("path").regex(searchTerm, "i"),
                    Criteria.where("schema").regex(searchTerm, "i"),
                    Criteria.where("worldId").regex(searchTerm, "i")
            );
            mongoQuery.addCriteria(searchCriteria);
        }

        // Sort by createdAt descending (newest first).
        mongoQuery.with(Sort.by(Sort.Direction.DESC, "createdAt"));

        // Count total matching documents before pagination is applied.
        long total = mongoTemplate.count(mongoQuery, StorageData.class);

        // Apply pagination.
        mongoQuery.skip(offset).limit(limit);

        // Execute query and map to metadata records.
        List<StorageData> storageList = mongoTemplate.find(mongoQuery, StorageData.class);
        List<StorageInfo> items = new ArrayList<>(storageList.size());
        for (StorageData storage : storageList) {
            items.add(new StorageInfo(
                    storage.getUuid(),
                    storage.getSize(),
                    storage.getCreatedAt(),
                    storage.getWorldId(),
                    storage.getPath(),
                    storage.getSchema(),
                    SchemaVersion.create(storage.getSchemaVersion())
            ));
        }

        return new StorageListResult(items, total);
    }

    @Override
    public List<String> findFinalStorageUuids(String worldId, Date olderThan) {
        Query query = new Query(
                Criteria.where("worldId").is(worldId)
                        .and("isFinal").is(true)
                        .and("createdAt").lt(olderThan)
        );
        return mongoTemplate.findDistinct(query, "uuid", StorageData.class, String.class);
    }

    @Override
    public List<String> findStorageUuids(String worldId, Date olderThan) {
        Query query = new Query(
                Criteria.where("worldId").is(worldId)
                        .and("createdAt").lt(olderThan)
        );
        return mongoTemplate.findDistinct(query, "uuid", StorageData.class, String.class);
    }

    @Override
    @Transactional
    public void deleteChunksByUuid(String storageId) {
        storageDataRepository.deleteByUuid(storageId);
    }

    @Override
    @Transactional
    public String duplicate(String sourceStorageId, String targetWorldId) {
        if (sourceStorageId == null || sourceStorageId.isBlank()) {
            log.error("Cannot duplicate with null/empty sourceStorageId");
            return null;
        }

        if (targetWorldId == null || targetWorldId.isBlank()) {
            log.error("Cannot duplicate with null/empty targetWorldId");
            return null;
        }

        // Get source storage info
        StorageInfo sourceInfo = info(sourceStorageId);
        if (sourceInfo == null) {
            log.error("Source storage not found: {}", sourceStorageId);
            return null;
        }

        // Load source data
        InputStream sourceStream = load(sourceStorageId);
        if (sourceStream == null) {
            log.error("Cannot load source storage data: {}", sourceStorageId);
            return null;
        }

        try {
            // Store with new worldId
            StorageInfo newInfo = store(
                    sourceInfo.schema(),
                    sourceInfo.schemaVersion(),
                    targetWorldId,
                    sourceInfo.path(),
                    sourceStream
            );

            if (newInfo == null) {
                log.error("Failed to store duplicated storage data");
                return null;
            }

            log.debug("Duplicated storage: sourceId={} targetId={} targetWorldId={}",
                    sourceStorageId, newInfo.id(), targetWorldId);

            return newInfo.id();

        } finally {
            try {
                sourceStream.close();
            } catch (IOException e) {
                log.warn("Error closing source stream", e);
            }
        }
    }
}
