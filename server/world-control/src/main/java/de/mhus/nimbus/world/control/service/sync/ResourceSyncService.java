package de.mhus.nimbus.world.control.service.sync;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.dto.ExternalResourceDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrator service for import/export operations.
 * Coordinates ResourceSyncType implementations and handles Git integration.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResourceSyncService {

    private static final List<String> ALL_TYPES = Arrays.asList(
            "asset", "backdrop", "blocktype", "item", "itemtype", "itemposition",
            "entity", "entitymodel", "model", "ground", "anything", "hexgrid"
    );

    private final List<ResourceSyncType> syncTypes;
    private final GitHelper gitHelper;

    @Qualifier("syncYamlMapper")
    private final YAMLMapper yamlMapper;

    /**
     * Export world data to filesystem.
     *
     * @param worldId        World identifier
     * @param definition     Export configuration
     * @param force          Force export even if not changed
     * @param removeOvertaken Remove files that no longer exist in database
     * @return Export result
     */
    public ExportResult export(WorldId worldId, ExternalResourceDTO definition, boolean force, boolean removeOvertaken) {
        log.info("Starting export for world {} to path: {} force={} remove={}",
                worldId, definition.getLocalPath(), force, removeOvertaken);

        try {
            Path dataPath = Paths.get(definition.getLocalPath());

            // Initialize or clone repository if needed
            if (definition.isAutoGit()) {
                try {
                    gitHelper.initOrClone(definition);
                } catch (Exception e) {
                    log.warn("Git init/clone failed: {}", e.getMessage());
                }
            }

            // Git pull if enabled
            if (definition.isAutoGit()) {
                try {
                    gitHelper.pull(definition);
                } catch (Exception e) {
                    log.warn("Git pull failed, continuing with export: {}", e.getMessage());
                }
            }

            // Resolve types (empty list = all types)
            List<String> types = definition.getTypes() == null || definition.getTypes().isEmpty()
                    ? ALL_TYPES
                    : definition.getTypes();

            // Export each type
            int totalExported = 0;
            int totalDeleted = 0;
            Map<String, Integer> exportedByType = new HashMap<>();
            Map<String, Integer> deletedByType = new HashMap<>();

            for (String typeName : types) {
                ResourceSyncType syncType = findSyncType(typeName);
                if (syncType == null) {
                    log.warn("No sync type found for: {}", typeName);
                    continue;
                }

                try {
                    log.info("Exporting type: {}", typeName);
                    ResourceSyncType.ExportResult typeResult = syncType.export(dataPath, worldId, force, removeOvertaken);
                    exportedByType.put(typeName, typeResult.exported());
                    deletedByType.put(typeName, typeResult.deleted());
                    totalExported += typeResult.exported();
                    totalDeleted += typeResult.deleted();
                    log.info("Exported {} {} entities, deleted {} files",
                            typeResult.exported(), typeName, typeResult.deleted());
                } catch (Exception e) {
                    log.error("Failed to export type: " + typeName, e);
                    return ExportResult.failure("Failed to export " + typeName + ": " + e.getMessage());
                }
            }

            // Git commit and push if enabled
            if (definition.isAutoGit()) {
                try {
                    String message = String.format("Export world %s (%d exported, %d deleted)",
                            worldId.getId(), totalExported, totalDeleted);
                    gitHelper.commitAndPush(definition, message);
                } catch (Exception e) {
                    log.warn("Git commit/push failed: {}", e.getMessage());
                }
            }

            log.info("Export completed successfully: {} exported, {} deleted", totalExported, totalDeleted);
            return ExportResult.success(totalExported, totalDeleted, exportedByType, deletedByType);

        } catch (Exception e) {
            log.error("Export failed for world " + worldId, e);
            return ExportResult.failure("Export failed: " + e.getMessage());
        }
    }

    /**
     * Import world data from filesystem.
     *
     * @param worldId        World identifier
     * @param definition     Import configuration
     * @param force          Force import even if DB is newer
     * @param removeOvertaken Remove entities that no longer exist in filesystem
     * @return Import result
     */
    public ImportResult importData(WorldId worldId, ExternalResourceDTO definition, boolean force, boolean removeOvertaken) {
        log.info("Starting import for world {} from path: {} force={} remove={}",
                worldId, definition.getLocalPath(), force, removeOvertaken);

        try {
            Path dataPath = Paths.get(definition.getLocalPath());

            // Initialize or clone repository if needed
            if (definition.isAutoGit()) {
                try {
                    gitHelper.initOrClone(definition);
                } catch (Exception e) {
                    log.warn("Git init/clone failed: {}", e.getMessage());
                }
            }

            // Git pull if enabled
            if (definition.isAutoGit()) {
                try {
                    gitHelper.pull(definition);
                } catch (Exception e) {
                    log.warn("Git pull failed, continuing with import: {}", e.getMessage());
                }
            }

            // Resolve types (empty list = all types)
            List<String> types = definition.getTypes() == null || definition.getTypes().isEmpty()
                    ? ALL_TYPES
                    : definition.getTypes();

            // Import each type
            int totalImported = 0;
            int totalDeleted = 0;
            Map<String, Integer> importedByType = new HashMap<>();
            Map<String, Integer> deletedByType = new HashMap<>();

            for (String typeName : types) {
                ResourceSyncType syncType = findSyncType(typeName);
                if (syncType == null) {
                    log.warn("No sync type found for: {}", typeName);
                    continue;
                }

                try {
                    log.info("Importing type: {}", typeName);
                    ResourceSyncType.ImportResult typeResult = syncType.importData(dataPath, worldId, definition, force, removeOvertaken);
                    importedByType.put(typeName, typeResult.imported());
                    deletedByType.put(typeName, typeResult.deleted());
                    totalImported += typeResult.imported();
                    totalDeleted += typeResult.deleted();
                    log.info("Imported {} {} entities, deleted {} entities",
                            typeResult.imported(), typeName, typeResult.deleted());

                } catch (Exception e) {
                    log.error("Failed to import type: " + typeName, e);
                    return ImportResult.failure("Failed to import " + typeName + ": " + e.getMessage());
                }
            }

            log.info("Import completed successfully: {} imported, {} deleted", totalImported, totalDeleted);
            return ImportResult.success(totalImported, totalDeleted, importedByType, deletedByType);

        } catch (Exception e) {
            log.error("Import failed for world " + worldId, e);
            return ImportResult.failure("Import failed: " + e.getMessage());
        }
    }

    /**
     * Find sync type implementation by name.
     */
    private ResourceSyncType findSyncType(String name) {
        return syncTypes.stream()
                .filter(type -> type.name().equals(name))
                .findFirst()
                .orElse(null);
    }

    /**
     * Result of export operation.
     */
    public record ExportResult(
            boolean success,
            int entityCount,
            int deletedCount,
            Map<String, Integer> exportedByType,
            Map<String, Integer> deletedByType,
            String errorMessage,
            Instant timestamp
    ) {
        public static ExportResult success(int entityCount, int deletedCount,
                                           Map<String, Integer> exportedByType,
                                           Map<String, Integer> deletedByType) {
            return new ExportResult(true, entityCount, deletedCount, exportedByType, deletedByType, null, Instant.now());
        }

        public static ExportResult failure(String errorMessage) {
            return new ExportResult(false, 0, 0, Map.of(), Map.of(), errorMessage, Instant.now());
        }
    }

    /**
     * Result of import operation.
     */
    public record ImportResult(
            boolean success,
            int imported,
            int deleted,
            Map<String, Integer> importedByType,
            Map<String, Integer> deletedByType,
            String errorMessage,
            Instant timestamp
    ) {
        public static ImportResult success(int imported, int deleted,
                                           Map<String, Integer> importedByType,
                                           Map<String, Integer> deletedByType) {
            return new ImportResult(true, imported, deleted, importedByType, deletedByType, null, Instant.now());
        }

        public static ImportResult failure(String errorMessage) {
            return new ImportResult(false, 0, 0, Map.of(), Map.of(), errorMessage, Instant.now());
        }
    }
}
