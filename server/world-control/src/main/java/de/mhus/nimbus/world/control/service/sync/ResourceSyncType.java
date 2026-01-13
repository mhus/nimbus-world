package de.mhus.nimbus.world.control.service.sync;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.dto.ExternalResourceDTO;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Interface for type-specific import/export implementations.
 * Each implementation handles one type of resource (asset, backdrop, blocktype, model, ground).
 */
public interface ResourceSyncType {

    /**
     * Type identifier.
     *
     * @return Type name: "asset", "backdrop", "blocktype", "model", or "ground"
     */
    String name();

    /**
     * Export data to filesystem.
     *
     * @param dataPath       Root export path
     * @param worldId        World to export from
     * @param force          Force export even if updatedAt hasn't changed
     * @param removeOvertaken Remove files that no longer exist in database
     * @return Export result with exported and deleted counts
     * @throws IOException if file operations fail
     */
    ExportResult export(Path dataPath, WorldId worldId, boolean force, boolean removeOvertaken) throws IOException;

    /**
     * Result of export operation for a single type.
     */
    record ExportResult(int exported, int deleted) {
        public static ExportResult of(int exported, int deleted) {
            return new ExportResult(exported, deleted);
        }
    }

    /**
     * Import data from filesystem.
     *
     * @param dataPath       Root import path
     * @param worldId        World to import into
     * @param definition     ExternalResourceDTO with prefixMapping and target worldId
     * @param force          Force import even if DB is newer
     * @param removeOvertaken Remove entities that no longer exist in filesystem
     * @return Import result with imported and deleted counts
     * @throws IOException if file operations fail
     */
    ImportResult importData(Path dataPath, WorldId worldId, ExternalResourceDTO definition, boolean force, boolean removeOvertaken) throws IOException;

    /**
     * Result of import operation for a single type.
     */
    record ImportResult(int imported, int deleted) {
        public static ImportResult of(int imported, int deleted) {
            return new ImportResult(imported, deleted);
        }
    }
}
