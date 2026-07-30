package de.mhus.nimbus.shared.storage;

import de.mhus.nimbus.shared.types.SchemaVersion;

import java.io.InputStream;
import java.util.Date;
import java.util.List;

/**
 * Abstraktion eines externen Speichers (aktuell Dateisystem). Große Assets werden hier gespeichert.
 */
public abstract class StorageService {
//
//    public StorageInfo store(String worldId, String path, InputStream stream) {
//        return store(null, null, worldId, path, stream);
//    }

    /** Speichert Daten und liefert eine Storage-Id. */
    public abstract StorageInfo store(String schema, SchemaVersion schemaVersion, String worldId, String path, InputStream stream);

    /** Lädt Daten anhand der Storage-Id. */
    public abstract InputStream load(String storageId);

    /** Entfernt abgelegten Inhalt. */
    public abstract void delete(String storageId);

    public StorageInfo update(String storageId, InputStream stream) {
        return update(null, null, storageId, stream);
    }

    /**
     * Update existing stored data with a new storageId.
     * This will keep the existing data available until the new data is fully stored.
     * The old storageId will be removed later.
     *
     * @param schema if null the existing schema is used
     * @param schemaVersion if null the existing schema version is used
     * @param storageId The storageId to update
     * @param stream New data stream
     * @return New StorageInfo data
     */
    public abstract StorageInfo update(String schema, SchemaVersion schemaVersion, String storageId, InputStream stream);

    /**
     * Replace existing stored data with the same storageId.
     * This will produce a lag of downtime where the storage object is not available.
     *
     * @param schema if null the existing schema is used
     * @param schemaVersion if null the existing schema version is used
     * @param storageId The storageId to replace
     * @param stream New data stream
     * @return New StorageInfo data
     */
    public abstract StorageInfo replace(String schema, SchemaVersion schemaVersion, String storageId, InputStream stream);

    public abstract StorageInfo info(String storageId);

    /**
     * Lists metadata of the FINAL chunks of stored objects, filtered and paginated.
     *
     * The optional query is matched case-insensitively against the uuid, path,
     * schema and worldId fields. Results are ordered by creation date descending
     * (newest first) and paginated via offset/limit.
     *
     * @param query  optional search term (null or blank returns all final objects)
     * @param offset pagination offset (0-based, must be &gt;= 0)
     * @param limit  maximum number of items to return
     * @return the paged list of final storage metadata plus the total match count
     */
    public abstract StorageListResult listFinal(String query, int offset, int limit);

    /**
     * Duplicate existing stored data with a new worldId.
     * Loads the data from the source storageId and stores it with the target worldId.
     *
     * @param sourceStorageId The storageId to duplicate
     * @param targetWorldId The target worldId for the duplicated data
     * @return New StorageInfo with the new storageId
     */
    public abstract String duplicate(String sourceStorageId, String targetWorldId);

    /**
     * List distinct storage uuids of FINAL chunks for a world, created before the given date.
     * Used by repair to determine which stored objects are candidates for orphan detection.
     *
     * @param worldId   the world identifier to scope by
     * @param olderThan only entries created strictly before this date are returned
     * @return distinct storage uuids of final chunks
     */
    public abstract List<String> findFinalStorageUuids(String worldId, Date olderThan);

    /**
     * List distinct storage uuids of ALL chunks for a world, created before the given date.
     * Used by repair to detect incomplete (non-final) uploads.
     *
     * @param worldId   the world identifier to scope by
     * @param olderThan only entries created strictly before this date are returned
     * @return distinct storage uuids of all chunks
     */
    public abstract List<String> findStorageUuids(String worldId, Date olderThan);

    /**
     * Hard-delete all chunks belonging to a storage uuid immediately.
     * Used by repair to remove incomplete uploads that never received a final chunk.
     *
     * @param storageId the storage uuid whose chunks are removed
     */
    public abstract void deleteChunksByUuid(String storageId);

    /**
     * Raw schema metadata (schema name and version) as stored on a chunk, kept as
     * plain strings without {@link SchemaVersion} normalization.
     */
    public record StoredSchema(String schema, String schemaVersion) { }

    /**
     * Read the raw schema metadata from the first chunk (index 0) of a storage
     * object. Owner-level accessor so callers do not query the storage_data
     * collection directly (data ownership).
     *
     * @param storageId the storage uuid
     * @return the stored schema/version, or {@code null} if the chunk is absent
     */
    public abstract StoredSchema readStoredSchema(String storageId);

    public boolean exists(String storageId) {
        try {
            return info(storageId) != null;
        } catch (Exception e) {
            return false;
        }
    }

    public record StorageInfo(String id, long size, Date createdAt, String worldId, String path, String schema, SchemaVersion schemaVersion) { }

    /**
     * Paged result of a {@link #listFinal(String, int, int)} query.
     *
     * @param items the storage metadata of the requested page
     * @param total the total number of matching objects across all pages
     */
    public record StorageListResult(List<StorageInfo> items, long total) { }

}

