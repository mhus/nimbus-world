package de.mhus.nimbus.world.control.service.sync.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
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
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
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

    private static final String COLLECTION_NAME = "w_items";

    private final WItemService itemService;
    private final MongoTemplate mongoTemplate;
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

        // Get items directly from MongoDB as Documents
        Query query = new Query(Criteria.where("worldId").is(worldId.getId()));
        List<Document> documents = mongoTemplate.find(query, Document.class, COLLECTION_NAME);

        Set<String> dbItemIds = new HashSet<>();
        int exported = 0;

        for (Document doc : documents) {
            try {
                String itemId = doc.getString("itemId");
                if (itemId == null) {
                    log.warn("Item without itemId, skipping");
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
                        String itemId = doc.getString("itemId");

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
                    String itemId = doc.getString("itemId");
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

                    // Find existing by unique constraint (worldId + itemId)
                    Query findQuery = new Query(
                            Criteria.where("worldId").is(migratedDoc.getString("worldId"))
                                    .and("itemId").is(migratedDoc.getString("itemId"))
                    );
                    Document existing = mongoTemplate.findOne(findQuery, Document.class, COLLECTION_NAME);

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

                    // Always remove _id from imported document first (may be serialized incorrectly)
                    migratedDoc.remove("_id");

                    // If existing, use its ObjectId to update in place
                    if (existing != null) {
                        migratedDoc.put("_id", existing.get("_id"));
                    }
                    // else: _id is removed, MongoDB will generate a new ObjectId

                    // Save to MongoDB
                    mongoTemplate.save(migratedDoc, COLLECTION_NAME);
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
                if (!filesystemItemIds.contains(item.getItemId())) {
                    itemService.delete(worldId, item.getItemId());
                    log.info("Deleted item not in filesystem: {}", item.getItemId());
                    deleted++;
                }
            }
        }

        return ResourceSyncType.ImportResult.of(imported, deleted);
    }
}
