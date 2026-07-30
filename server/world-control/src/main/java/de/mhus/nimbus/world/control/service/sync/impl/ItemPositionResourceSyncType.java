package de.mhus.nimbus.world.control.service.sync.impl;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;
import de.mhus.nimbus.shared.service.SchemaMigrationService;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.control.service.sync.DocumentTransformer;
import de.mhus.nimbus.world.control.service.sync.ResourceSyncType;
import de.mhus.nimbus.world.shared.dto.ExternalResourceDTO;
import de.mhus.nimbus.world.shared.world.WItemPosition;
import de.mhus.nimbus.world.shared.world.WItemPositionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Import/export implementation for item positions.
 * Exports MongoDB documents directly as YAML files (preserves all fields including _schema).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ItemPositionResourceSyncType implements ResourceSyncType {

    private final WItemPositionService itemPositionService;
    private final SchemaMigrationService migrationService;
    private final DocumentTransformer documentTransformer;
    private final ObjectMapper objectMapper;

    @Qualifier("syncYamlMapper")
    private final YAMLMapper yamlMapper;

    @Override
    public String name() {
        return "itemposition";
    }

    @Override
    public ResourceSyncType.ExportResult export(Path dataPath, WorldId worldId, boolean force, boolean removeOvertaken) throws IOException {
        Path itemPositionsDir = dataPath.resolve("itempositions");
        Files.createDirectories(itemPositionsDir);

        // Get item positions as raw Documents through the owner service
        List<Document> documents = itemPositionService.exportDocuments(worldId.getId());

        Set<Object> dbItemPositionIds = new HashSet<>();
        int exported = 0;

        for (Document doc : documents) {
            try {
                Object idObj = doc.get("_id");
                if (idObj == null) {
                    log.warn("ItemPosition without _id, skipping");
                    continue;
                }

                dbItemPositionIds.add(idObj);

                // Use _id as filename (ObjectId to String)
                String filename = idObj.toString() + ".yaml";
                Path targetFile = itemPositionsDir.resolve(filename);

                // Write MongoDB Document directly as YAML
                yamlMapper.writeValue(targetFile.toFile(), doc);
                log.debug("Exported item position: {}", idObj);
                exported++;

            } catch (Exception e) {
                log.warn("Failed to export item position document", e);
            }
        }

        // Remove files not in DB if requested
        int deleted = 0;
        if (removeOvertaken && Files.exists(itemPositionsDir)) {
            try (Stream<Path> files = Files.list(itemPositionsDir)) {
                for (Path file : files.filter(f -> f.toString().endsWith(".yaml")).toList()) {
                    try {
                        Document doc = yamlMapper.readValue(file.toFile(), Document.class);
                        Object idObj = doc.get("_id");

                        if (!dbItemPositionIds.contains(idObj)) {
                            Files.delete(file);
                            log.info("Deleted file not in database: {}", file);
                            deleted++;
                        }
                    } catch (IOException e) {
                        log.warn("Failed to check file for deletion: " + file, e);
                    }
                }
            }
        }

        return ResourceSyncType.ExportResult.of(exported, deleted);
    }

    @Override
    public ResourceSyncType.ImportResult importData(Path dataPath, WorldId worldId, ExternalResourceDTO definition, boolean force, boolean removeOvertaken) throws IOException {
        Path itemPositionsDir = dataPath.resolve("itempositions");
        if (!Files.exists(itemPositionsDir)) {
            log.info("No itempositions directory found");
            return ResourceSyncType.ImportResult.of(0, 0);
        }

        // Collect filesystem item position IDs
        Set<String> filesystemItemPositionIds = new HashSet<>();
        int imported = 0;

        try (Stream<Path> files = Files.list(itemPositionsDir)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".yaml")).toList()) {
                try {
                    // Read YAML as Document
                    Document doc = yamlMapper.readValue(file.toFile(), Document.class);
                    Object idObj = doc.get("_id");
                    if (idObj != null) {
                        filesystemItemPositionIds.add(idObj.toString());
                    }

                    String json = objectMapper.writeValueAsString(doc);

                    // Migrate if needed
                    String entityType = doc.getString("_class");
                    if (entityType == null) {
                        entityType = "de.mhus.nimbus.world.shared.world.WItemPosition";
                    }

                    String migratedJson = migrationService.migrateToLatest(json, entityType);
                    Document migratedDoc = Document.parse(migratedJson);

                    // Transform document (worldId replacement + prefix mapping)
                    migratedDoc = documentTransformer.transformForImport(migratedDoc, definition);
                    documentTransformer.ensureEpoches(migratedDoc);

                    // Find existing by unique constraint (worldId + itemId)
                    Document existing = itemPositionService.findDocumentByWorldIdAndItemId(
                            migratedDoc.getString("worldId"),
                            migratedDoc.getString("itemId")
                    ).orElse(null);

                    // Check if should import
                    if (!force && existing != null) {
                        Object fileUpdatedAt = migratedDoc.get("updatedAt");
                        Object dbUpdatedAt = existing.get("updatedAt");
                        if (fileUpdatedAt != null && dbUpdatedAt != null) {
                            if (dbUpdatedAt.toString().compareTo(fileUpdatedAt.toString()) > 0) {
                                log.debug("Skipping item position {} (DB is newer)", idObj);
                                continue;
                            }
                        }
                    }

                    // Upsert through the owner (reconciles _id by the unique key)
                    itemPositionService.upsertDocument(migratedDoc);
                    log.debug("Imported item position: {}", idObj);
                    imported++;

                } catch (Exception e) {
                    log.warn("Failed to import item position from file: " + file, e);
                }
            }
        }

        // Remove overtaken if requested
        int deleted = 0;
        if (removeOvertaken) {
            // Get item positions as raw Documents through the owner service
            List<Document> dbDocs = itemPositionService.exportDocuments(worldId.getId());

            for (Document dbDoc : dbDocs) {
                Object idObj = dbDoc.get("_id");
                if (idObj != null && !filesystemItemPositionIds.contains(idObj.toString())) {
                    itemPositionService.deleteDocumentById(idObj);
                    log.info("Deleted item position not in filesystem: {}", idObj);
                    deleted++;
                }
            }
        }

        return ResourceSyncType.ImportResult.of(imported, deleted);
    }
}

