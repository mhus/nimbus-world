package de.mhus.nimbus.world.control.service.sync.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import de.mhus.nimbus.shared.service.SchemaMigrationService;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.control.service.sync.DocumentTransformer;
import de.mhus.nimbus.world.control.service.sync.ResourceSyncType;
import de.mhus.nimbus.world.shared.dto.ExternalResourceDTO;
import de.mhus.nimbus.world.shared.world.WAnything;
import de.mhus.nimbus.world.shared.world.WAnythingService;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Import/export implementation for WAnything entities.
 * Exports MongoDB documents directly as YAML files (preserves all fields including _schema).
 * WAnything is uniquely identified by worldId + collection + name.
 * Only exports WAnything entities that match the specified worldId.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnythingResourceSyncType implements ResourceSyncType {

    private static final String COLLECTION_NAME = "w_anything";

    private final WAnythingService anythingService;
    private final MongoTemplate mongoTemplate;
    private final SchemaMigrationService migrationService;
    private final DocumentTransformer documentTransformer;
    private final ObjectMapper objectMapper;

    @Qualifier("syncYamlMapper")
    private final YAMLMapper yamlMapper;

    @Override
    public String name() {
        return "anything";
    }

    @Override
    public ResourceSyncType.ExportResult export(Path dataPath, WorldId worldId, boolean force, boolean removeOvertaken) throws IOException {
        Path anythingDir = dataPath.resolve("anything");
        Files.createDirectories(anythingDir);

        // Get WAnything entities directly from MongoDB as Documents, filtered by worldId
        Query query = new Query(Criteria.where("worldId").is(worldId.getId()));
        List<Document> documents = mongoTemplate.find(query, Document.class, COLLECTION_NAME);

        // Track exported entities by collection+name
        Map<String, Set<String>> dbAnythingIds = new HashMap<>();
        int exported = 0;

        for (Document doc : documents) {
            try {
                String collection = doc.getString("collection");
                String name = doc.getString("name");

                if (collection == null || name == null) {
                    log.warn("WAnything without collection or name, skipping");
                    continue;
                }

                // Track this entity
                dbAnythingIds.computeIfAbsent(collection, k -> new HashSet<>()).add(name);

                // Create subdirectory for collection
                Path collectionDir = anythingDir.resolve(collection);
                Files.createDirectories(collectionDir);

                // Write to file: anything/{collection}/{name}.yaml
                Path targetFile = collectionDir.resolve(name + ".yaml");

                // Write MongoDB Document directly as YAML
                yamlMapper.writeValue(targetFile.toFile(), doc);
                log.debug("Exported WAnything: collection={}, name={}", collection, name);
                exported++;

            } catch (Exception e) {
                log.warn("Failed to export WAnything document", e);
            }
        }

        // Remove files not in DB if requested
        int deleted = 0;
        if (removeOvertaken && Files.exists(anythingDir)) {
            try (Stream<Path> collectionDirs = Files.list(anythingDir)) {
                for (Path collectionDir : collectionDirs.filter(Files::isDirectory).toList()) {
                    String collection = collectionDir.getFileName().toString();
                    Set<String> dbNames = dbAnythingIds.getOrDefault(collection, Set.of());

                    try (Stream<Path> files = Files.list(collectionDir)) {
                        for (Path file : files.filter(f -> f.toString().endsWith(".yaml")).toList()) {
                            String filename = file.getFileName().toString();
                            String name = filename.substring(0, filename.length() - 5); // Remove .yaml

                            if (!dbNames.contains(name)) {
                                Files.delete(file);
                                log.info("Deleted file not in database: {}", file);
                                deleted++;
                            }
                        }
                    }

                    // Remove empty collection directories
                    try (Stream<Path> files = Files.list(collectionDir)) {
                        if (files.findAny().isEmpty()) {
                            Files.delete(collectionDir);
                            log.debug("Deleted empty collection directory: {}", collectionDir);
                        }
                    }
                }
            }
        }

        return ResourceSyncType.ExportResult.of(exported, deleted);
    }

    @Override
    public ResourceSyncType.ImportResult importData(Path dataPath, WorldId worldId, ExternalResourceDTO definition, boolean force, boolean removeOvertaken) throws IOException {
        Path anythingDir = dataPath.resolve("anything");
        if (!Files.exists(anythingDir)) {
            log.info("No anything directory found");
            return ResourceSyncType.ImportResult.of(0, 0);
        }

        // Track filesystem entities by collection+name
        Map<String, Set<String>> filesystemAnythingIds = new HashMap<>();
        int imported = 0;

        // Process all collection subdirectories
        try (Stream<Path> collectionDirs = Files.list(anythingDir)) {
            for (Path collectionDir : collectionDirs.filter(Files::isDirectory).toList()) {
                String collection = collectionDir.getFileName().toString();

                try (Stream<Path> files = Files.list(collectionDir)) {
                    for (Path file : files.filter(f -> f.toString().endsWith(".yaml")).toList()) {
                        try {
                            // Read YAML and convert to JSON for migration
                            Document doc = yamlMapper.readValue(file.toFile(), Document.class);
                            String name = doc.getString("name");
                            String docCollection = doc.getString("collection");

                            if (name == null || docCollection == null) {
                                log.warn("WAnything document missing name or collection in file: {}", file);
                                continue;
                            }

                            filesystemAnythingIds.computeIfAbsent(docCollection, k -> new HashSet<>()).add(name);

                            String json = objectMapper.writeValueAsString(doc);

                            // Migrate if needed
                            String entityType = doc.getString("_class");
                            if (entityType == null) {
                                entityType = "de.mhus.nimbus.world.shared.world.WAnything";
                            }

                            String migratedJson = migrationService.migrateToLatest(json, entityType);
                            Document migratedDoc = Document.parse(migratedJson);

                            // Transform document (worldId replacement + prefix mapping)
                            migratedDoc = documentTransformer.transformForImport(migratedDoc, definition);

                            // Find existing by unique constraint (worldId + collection + name)
                            Query findQuery = new Query(
                                    Criteria.where("worldId").is(migratedDoc.getString("worldId"))
                                            .and("collection").is(migratedDoc.getString("collection"))
                                            .and("name").is(migratedDoc.getString("name"))
                            );
                            Document existing = mongoTemplate.findOne(findQuery, Document.class, COLLECTION_NAME);

                            // Check if should import
                            if (!force && existing != null) {
                                Object fileUpdatedAt = migratedDoc.get("updatedAt");
                                Object dbUpdatedAt = existing.get("updatedAt");
                                if (fileUpdatedAt != null && dbUpdatedAt != null) {
                                    if (dbUpdatedAt.toString().compareTo(fileUpdatedAt.toString()) > 0) {
                                        log.debug("Skipping WAnything {}/{} (DB is newer)", docCollection, name);
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
                            log.debug("Imported WAnything: collection={}, name={}", docCollection, name);
                            imported++;

                        } catch (Exception e) {
                            log.warn("Failed to import WAnything from file: " + file, e);
                        }
                    }
                }
            }
        }

        // Remove overtaken entities if requested
        int deleted = 0;
        if (removeOvertaken) {
            // Get all WAnything entities for this world from database
            Query query = new Query(Criteria.where("worldId").is(worldId.getId()));
            List<Document> dbDocuments = mongoTemplate.find(query, Document.class, COLLECTION_NAME);

            for (Document doc : dbDocuments) {
                String collection = doc.getString("collection");
                String name = doc.getString("name");

                if (collection == null || name == null) {
                    continue;
                }

                // Check if this entity exists in filesystem
                Set<String> filesystemNames = filesystemAnythingIds.getOrDefault(collection, Set.of());
                if (!filesystemNames.contains(name)) {
                    anythingService.deleteByWorldIdAndCollectionAndName(worldId.getId(), collection, name);
                    log.info("Deleted WAnything not in filesystem: collection={}, name={}", collection, name);
                    deleted++;
                }
            }
        }

        return ResourceSyncType.ImportResult.of(imported, deleted);
    }
}
