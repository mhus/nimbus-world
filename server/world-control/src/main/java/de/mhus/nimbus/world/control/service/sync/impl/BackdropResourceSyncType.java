package de.mhus.nimbus.world.control.service.sync.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import de.mhus.nimbus.shared.service.SchemaMigrationService;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.control.service.sync.DocumentTransformer;
import de.mhus.nimbus.world.control.service.sync.ResourceSyncType;
import de.mhus.nimbus.world.shared.dto.ExternalResourceDTO;
import de.mhus.nimbus.world.shared.world.WBackdrop;
import de.mhus.nimbus.world.shared.world.WBackdropService;
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
 * Import/export implementation for backdrops.
 * Exports MongoDB documents directly as YAML files (preserves all fields including _schema).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BackdropResourceSyncType implements ResourceSyncType {

    private static final String COLLECTION_NAME = "w_backdrops";

    private final WBackdropService backdropService;
    private final MongoTemplate mongoTemplate;
    private final SchemaMigrationService migrationService;
    private final DocumentTransformer documentTransformer;
    private final ObjectMapper objectMapper;

    @Qualifier("syncYamlMapper")
    private final YAMLMapper yamlMapper;

    @Override
    public String name() {
        return "backdrop";
    }

    @Override
    public ResourceSyncType.ExportResult export(Path dataPath, WorldId worldId, boolean force, boolean removeOvertaken) throws IOException {
        Path backdropsDir = dataPath.resolve("backdrops");
        Files.createDirectories(backdropsDir);

        // Get backdrops directly from MongoDB as Documents
        Query query = new Query(Criteria.where("worldId").is(worldId.getId()));
        List<Document> documents = mongoTemplate.find(query, Document.class, COLLECTION_NAME);

        Set<String> dbBackdropIds = new HashSet<>();
        int exported = 0;

        for (Document doc : documents) {
            try {
                String backdropId = doc.getString("backdropId");
                if (backdropId == null) {
                    log.warn("Backdrop without backdropId, skipping");
                    continue;
                }

                dbBackdropIds.add(backdropId);
                Path targetFile = backdropsDir.resolve(backdropId + ".yaml");

                // Write MongoDB Document directly as YAML
                yamlMapper.writeValue(targetFile.toFile(), doc);
                log.debug("Exported backdrop: {}", backdropId);
                exported++;

            } catch (Exception e) {
                log.warn("Failed to export backdrop document", e);
            }
        }

        // Remove files not in DB if requested
        int deleted = 0;
        if (removeOvertaken && Files.exists(backdropsDir)) {
            try (Stream<Path> files = Files.list(backdropsDir)) {
                for (Path file : files.filter(f -> f.toString().endsWith(".yaml")).toList()) {
                    String filename = file.getFileName().toString();
                    String backdropId = filename.substring(0, filename.length() - 5); // Remove .yaml

                    if (!dbBackdropIds.contains(backdropId)) {
                        Files.delete(file);
                        log.info("Deleted file not in database: {}", file);
                        deleted++;
                    }
                }
            }
        }

        return ResourceSyncType.ExportResult.of(exported, deleted);
    }

    @Override
    public ResourceSyncType.ImportResult importData(Path dataPath, WorldId worldId, ExternalResourceDTO definition, boolean force, boolean removeOvertaken) throws IOException {
        Path backdropsDir = dataPath.resolve("backdrops");
        if (!Files.exists(backdropsDir)) {
            log.info("No backdrops directory found");
            return ResourceSyncType.ImportResult.of(0, 0);
        }

        // Collect filesystem backdrop IDs
        Set<String> filesystemBackdropIds = new HashSet<>();
        int imported = 0;

        try (Stream<Path> files = Files.list(backdropsDir)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".yaml")).toList()) {
                try {
                    // Read YAML and convert to JSON for migration
                    Document doc = yamlMapper.readValue(file.toFile(), Document.class);
                    String backdropId = doc.getString("backdropId");
                    filesystemBackdropIds.add(backdropId);

                    String json = objectMapper.writeValueAsString(doc);

                    // Migrate if needed
                    String entityType = doc.getString("_class");
                    if (entityType == null) {
                        entityType = "de.mhus.nimbus.world.shared.world.WBackdrop";
                    }

                    String migratedJson = migrationService.migrateToLatest(json, entityType);
                    Document migratedDoc = Document.parse(migratedJson);

                    // Transform document (worldId replacement + prefix mapping)
                    migratedDoc = documentTransformer.transformForImport(migratedDoc, definition);

                    // Find existing by unique constraint (worldId + backdropId)
                    Query findQuery = new Query(
                            Criteria.where("worldId").is(migratedDoc.getString("worldId"))
                                    .and("backdropId").is(migratedDoc.getString("backdropId"))
                    );
                    Document existing = mongoTemplate.findOne(findQuery, Document.class, COLLECTION_NAME);

                    // Check if should import
                    if (!force && existing != null) {
                        Object fileUpdatedAt = migratedDoc.get("updatedAt");
                        Object dbUpdatedAt = existing.get("updatedAt");
                        if (fileUpdatedAt != null && dbUpdatedAt != null) {
                            if (dbUpdatedAt.toString().compareTo(fileUpdatedAt.toString()) > 0) {
                                log.debug("Skipping backdrop {} (DB is newer)", backdropId);
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
                    log.debug("Imported backdrop: {}", backdropId);
                    imported++;

                } catch (Exception e) {
                    log.warn("Failed to import backdrop from file: " + file, e);
                }
            }
        }

        // Remove overtaken if requested
        int deleted = 0;
        if (removeOvertaken) {
            List<WBackdrop> dbBackdrops = backdropService.findByWorldId(worldId);

            for (WBackdrop backdrop : dbBackdrops) {
                if (!filesystemBackdropIds.contains(backdrop.getBackdropId())) {
                    backdropService.delete(worldId, backdrop.getBackdropId());
                    log.info("Deleted backdrop not in filesystem: {}", backdrop.getBackdropId());
                    deleted++;
                }
            }
        }

        return ResourceSyncType.ImportResult.of(imported, deleted);
    }
}
