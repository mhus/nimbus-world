package de.mhus.nimbus.world.control.service.sync.impl;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;
import de.mhus.nimbus.shared.service.SchemaMigrationService;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.control.service.sync.DocumentTransformer;
import de.mhus.nimbus.world.control.service.sync.ResourceSyncType;
import de.mhus.nimbus.world.shared.dto.ExternalResourceDTO;
import de.mhus.nimbus.world.shared.world.WItem;
import de.mhus.nimbus.world.shared.world.WItemService;
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
 * Import/export implementation for items.
 * Exports MongoDB documents directly as YAML files (preserves all fields including _schema).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ItemResourceSyncType implements ResourceSyncType {

    private final WItemService itemService;
    private final SchemaMigrationService migrationService;
    private final DocumentTransformer documentTransformer;
    private final ObjectMapper objectMapper;

    @Qualifier("syncYamlMapper")
    private final YAMLMapper yamlMapper;

    @Override
    public String name() {
        return "item";
    }

    @Override
    public ResourceSyncType.ExportResult export(Path dataPath, WorldId worldId, boolean force, boolean removeOvertaken) throws IOException {
        Path itemsDir = dataPath.resolve("items");
        Files.createDirectories(itemsDir);

        // Get items as raw Documents through the owner service
        List<Document> documents = itemService.exportDocuments(worldId.getId());

        Set<String> dbItemIds = new HashSet<>();
        int exported = 0;

        for (Document doc : documents) {
            try {
                String itemId = doc.getString("name");
                if (itemId == null) {
                    log.warn("Item without name, skipping");
                    continue;
                }

                dbItemIds.add(itemId);

                // Sanitize filename (replace : with _)
                String filename = itemId.replace(":", "_") + ".yaml";
                Path targetFile = itemsDir.resolve(filename);

                // Write MongoDB Document directly as YAML
                yamlMapper.writeValue(targetFile.toFile(), doc);
                log.debug("Exported item: {}", itemId);
                exported++;

            } catch (Exception e) {
                log.warn("Failed to export item document", e);
            }
        }

        // Remove files not in DB if requested
        int deleted = 0;
        if (removeOvertaken && Files.exists(itemsDir)) {
            try (Stream<Path> files = Files.list(itemsDir)) {
                for (Path file : files.filter(f -> f.toString().endsWith(".yaml")).toList()) {
                    try {
                        Document doc = yamlMapper.readValue(file.toFile(), Document.class);
                        String itemId = doc.getString("name");

                        if (!dbItemIds.contains(itemId)) {
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
        Path itemsDir = dataPath.resolve("items");
        if (!Files.exists(itemsDir)) {
            log.info("No items directory found");
            return ResourceSyncType.ImportResult.of(0, 0);
        }

        // Collect filesystem item IDs
        Set<String> filesystemItemIds = new HashSet<>();
        int imported = 0;

        try (Stream<Path> files = Files.list(itemsDir)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".yaml")).toList()) {
                try {
                    // Read YAML as Document
                    Document doc = yamlMapper.readValue(file.toFile(), Document.class);
                    String itemId = doc.getString("name");
                    filesystemItemIds.add(itemId);

                    String json = objectMapper.writeValueAsString(doc);

                    // Migrate if needed
                    String entityType = doc.getString("_class");
                    if (entityType == null) {
                        entityType = "de.mhus.nimbus.world.shared.world.WItem";
                    }

                    String migratedJson = migrationService.migrateToLatest(json, entityType);
                    Document migratedDoc = Document.parse(migratedJson);

                    // Transform document (worldId replacement + prefix mapping)
                    migratedDoc = documentTransformer.transformForImport(migratedDoc, definition);

                    // Find existing by unique constraint (worldId + name).
                    // BUG FIX: the owner keys on the actual natural-key field 'name'
                    // (the previous code queried a non-existent 'itemId' field).
                    Document existing = itemService.findDocumentByWorldIdAndName(
                            migratedDoc.getString("worldId"),
                            migratedDoc.getString("name")
                    ).orElse(null);

                    // Check if should import
                    if (!force && existing != null) {
                        Object fileUpdatedAt = migratedDoc.get("updatedAt");
                        Object dbUpdatedAt = existing.get("updatedAt");
                        if (fileUpdatedAt != null && dbUpdatedAt != null) {
                            if (dbUpdatedAt.toString().compareTo(fileUpdatedAt.toString()) > 0) {
                                log.debug("Skipping item {} (DB is newer)", itemId);
                                continue;
                            }
                        }
                    }

                    // Upsert through the owner (reconciles _id by the unique key)
                    itemService.upsertDocument(migratedDoc);
                    log.debug("Imported item: {}", itemId);
                    imported++;

                } catch (Exception e) {
                    log.warn("Failed to import item from file: " + file, e);
                }
            }
        }

        // Remove overtaken if requested
        int deleted = 0;
        if (removeOvertaken) {
            List<WItem> dbItems = itemService.findByWorldId(worldId);

            for (WItem item : dbItems) {
                if (!filesystemItemIds.contains(item.getName())) {
                    itemService.delete(worldId, item.getName());
                    log.info("Deleted item not in filesystem: {}", item.getName());
                    deleted++;
                }
            }
        }

        return ResourceSyncType.ImportResult.of(imported, deleted);
    }
}
