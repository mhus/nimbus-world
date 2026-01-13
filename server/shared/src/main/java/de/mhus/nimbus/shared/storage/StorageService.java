package de.mhus.nimbus.shared.storage;

import de.mhus.nimbus.shared.types.SchemaVersion;

import java.io.InputStream;
import java.util.Date;

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

    public record StorageInfo(String id, long size, Date createdAt, String worldId, String path, String schema, SchemaVersion schemaVersion) { }

}

