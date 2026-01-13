package de.mhus.nimbus.world.control.service.sync.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import de.mhus.nimbus.shared.service.SchemaMigrationService;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.control.service.sync.DocumentTransformer;
import de.mhus.nimbus.world.control.service.sync.ResourceSyncType;
import de.mhus.nimbus.world.shared.dto.ExternalResourceDTO;
import de.mhus.nimbus.world.shared.world.WHexGrid;
import de.mhus.nimbus.world.shared.world.WHexGridService;
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
 * Import/export implementation for hex grids.
 * Exports MongoDB documents directly as YAML files (preserves all fields including _schema).
 * WHexGrid is uniquely identified by worldId + position.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HexGridResourceSyncType implements ResourceSyncType {

    private static final String COLLECTION_NAME = "w_hexgrids";

    private final WHexGridService hexGridService;
    private final MongoTemplate mongoTemplate;
    private final SchemaMigrationService migrationService;
    private final DocumentTransformer documentTransformer;
    private final ObjectMapper objectMapper;

    @Qualifier("syncYamlMapper")
    private final YAMLMapper yamlMapper;

    @Override
    public String name() {
        return "hexgrid";
    }

    @Override
    public ResourceSyncType.ExportResult export(Path dataPath, WorldId worldId, boolean force, boolean removeOvertaken) throws IOException {
        Path hexGridsDir = dataPath.resolve("hexgrids");
        Files.createDirectories(hexGridsDir);

        // Get hex grids directly from MongoDB as Documents
        Query query = new Query(Criteria.where("worldId").is(worldId.getId()));
        List<Document> documents = mongoTemplate.find(query, Document.class, COLLECTION_NAME);

        Set<String> dbPositions = new HashSet<>();
        int exported = 0;

        for (Document doc : documents) {
            try {
                String position = doc.getString("position");
                if (position == null) {
                    log.warn("HexGrid without position, skipping");
                    continue;
                }

                dbPositions.add(position);

                // Use position as filename, replacing : with _ for filesystem safety
                String filename = position.replace(':', '_');
                Path targetFile = hexGridsDir.resolve(filename + ".yaml");

                // Write MongoDB Document directly as YAML
                yamlMapper.writeValue(targetFile.toFile(), doc);
                log.debug("Exported hex grid: {}", position);
                exported++;

            } catch (Exception e) {
                log.warn("Failed to export hex grid document", e);
            }
        }

        // Remove files not in DB if requested
        int deleted = 0;
        if (removeOvertaken && Files.exists(hexGridsDir)) {
            try (Stream<Path> files = Files.list(hexGridsDir)) {
                for (Path file : files.filter(f -> f.toString().endsWith(".yaml")).toList()) {
                    String filename = file.getFileName().toString();
                    String position = filename.substring(0, filename.length() - 5).replace('_', ':'); // Remove .yaml and restore :

                    if (!dbPositions.contains(position)) {
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
        Path hexGridsDir = dataPath.resolve("hexgrids");
        if (!Files.exists(hexGridsDir)) {
            log.info("No hexgrids directory found");
            return ResourceSyncType.ImportResult.of(0, 0);
        }

        // Collect filesystem positions
        Set<String> filesystemPositions = new HashSet<>();
        int imported = 0;

        try (Stream<Path> files = Files.list(hexGridsDir)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".yaml")).toList()) {
                try {
                    // Read YAML and convert to JSON for migration
                    Document doc = yamlMapper.readValue(file.toFile(), Document.class);
                    String position = doc.getString("position");
                    filesystemPositions.add(position);

                    String json = objectMapper.writeValueAsString(doc);

                    // Migrate if needed
                    String entityType = doc.getString("_class");
                    if (entityType == null) {
                        entityType = "de.mhus.nimbus.world.shared.world.WHexGrid";
                    }

                    String migratedJson = migrationService.migrateToLatest(json, entityType);
                    Document migratedDoc = Document.parse(migratedJson);

                    // Transform document (worldId replacement + prefix mapping)
                    migratedDoc = documentTransformer.transformForImport(migratedDoc, definition);

                    // Find existing by unique constraint (worldId + position)
                    Query findQuery = new Query(
                            Criteria.where("worldId").is(migratedDoc.getString("worldId"))
                                    .and("position").is(migratedDoc.getString("position"))
                    );
                    Document existing = mongoTemplate.findOne(findQuery, Document.class, COLLECTION_NAME);

                    // Check if should import
                    if (!force && existing != null) {
                        Object fileUpdatedAt = migratedDoc.get("updatedAt");
                        Object dbUpdatedAt = existing.get("updatedAt");
                        if (fileUpdatedAt != null && dbUpdatedAt != null) {
                            if (dbUpdatedAt.toString().compareTo(fileUpdatedAt.toString()) > 0) {
                                log.debug("Skipping hex grid {} (DB is newer)", position);
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
                    log.debug("Imported hex grid: {}", position);
                    imported++;

                } catch (Exception e) {
                    log.warn("Failed to import hex grid from file: " + file, e);
                }
            }
        }

        // Remove overtaken if requested
        int deleted = 0;
        if (removeOvertaken) {
            List<WHexGrid> dbHexGrids = hexGridService.findByWorldId(worldId.getId());

            for (WHexGrid hexGrid : dbHexGrids) {
                if (!filesystemPositions.contains(hexGrid.getPosition())) {
                    // Delete using MongoDB directly since we have worldId and position
                    Query deleteQuery = new Query(
                            Criteria.where("worldId").is(worldId.getId())
                                    .and("position").is(hexGrid.getPosition())
                    );
                    mongoTemplate.remove(deleteQuery, COLLECTION_NAME);
                    log.info("Deleted hex grid not in filesystem: {}", hexGrid.getPosition());
                    deleted++;
                }
            }
        }

        return ResourceSyncType.ImportResult.of(imported, deleted);
    }
}
