package de.mhus.nimbus.world.control.service.sync.impl;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;
import de.mhus.nimbus.shared.service.SchemaMigrationService;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.control.service.sync.DocumentTransformer;
import de.mhus.nimbus.world.control.service.sync.ResourceSyncType;
import de.mhus.nimbus.world.shared.dto.ExternalResourceDTO;
import de.mhus.nimbus.world.shared.world.WEntity;
import de.mhus.nimbus.world.shared.world.WEntityService;
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
 * Import/export implementation for entities.
 * Exports MongoDB documents directly as YAML files (preserves all fields including _schema).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EntityResourceSyncType implements ResourceSyncType {

    private final WEntityService entityService;
    private final SchemaMigrationService migrationService;
    private final DocumentTransformer documentTransformer;
    private final ObjectMapper objectMapper;

    @Qualifier("syncYamlMapper")
    private final YAMLMapper yamlMapper;

    @Override
    public String name() {
        return "entity";
    }

    @Override
    public ResourceSyncType.ExportResult export(Path dataPath, WorldId worldId, boolean force, boolean removeOvertaken) throws IOException {
        Path entitiesDir = dataPath.resolve("entities");
        Files.createDirectories(entitiesDir);

        // Get entities as raw Documents through the owner service
        List<Document> documents = entityService.exportDocuments(worldId.getId());

        Set<String> dbEntityIds = new HashSet<>();
        int exported = 0;

        for (Document doc : documents) {
            try {
                String entityId = doc.getString("entityId");
                if (entityId == null) {
                    log.warn("Entity without entityId, skipping");
                    continue;
                }

                dbEntityIds.add(entityId);

                // Sanitize filename (replace : with _)
                String filename = entityId.replace(":", "_") + ".yaml";
                Path targetFile = entitiesDir.resolve(filename);

                // Write MongoDB Document directly as YAML
                yamlMapper.writeValue(targetFile.toFile(), doc);
                log.debug("Exported entity: {}", entityId);
                exported++;

            } catch (Exception e) {
                log.warn("Failed to export entity document", e);
            }
        }

        // Remove files not in DB if requested
        int deleted = 0;
        if (removeOvertaken && Files.exists(entitiesDir)) {
            try (Stream<Path> files = Files.list(entitiesDir)) {
                for (Path file : files.filter(f -> f.toString().endsWith(".yaml")).toList()) {
                    try {
                        Document doc = yamlMapper.readValue(file.toFile(), Document.class);
                        String entityId = doc.getString("entityId");

                        if (!dbEntityIds.contains(entityId)) {
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
        Path entitiesDir = dataPath.resolve("entities");
        if (!Files.exists(entitiesDir)) {
            log.info("No entities directory found");
            return ResourceSyncType.ImportResult.of(0, 0);
        }

        // Collect filesystem entity IDs
        Set<String> filesystemEntityIds = new HashSet<>();
        int imported = 0;

        try (Stream<Path> files = Files.list(entitiesDir)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".yaml")).toList()) {
                try {
                    // Read YAML as Document
                    Document doc = yamlMapper.readValue(file.toFile(), Document.class);
                    String entityId = doc.getString("entityId");
                    filesystemEntityIds.add(entityId);

                    String json = objectMapper.writeValueAsString(doc);

                    // Migrate if needed
                    String entityType = doc.getString("_class");
                    if (entityType == null) {
                        entityType = "de.mhus.nimbus.world.shared.world.WEntity";
                    }

                    String migratedJson = migrationService.migrateToLatest(json, entityType);
                    Document migratedDoc = Document.parse(migratedJson);

                    // Transform document (worldId replacement + prefix mapping)
                    migratedDoc = documentTransformer.transformForImport(migratedDoc, definition);
                    documentTransformer.ensureEpoches(migratedDoc);

                    // Find existing by unique constraint (worldId + entityId)
                    Document existing = entityService.findDocumentByWorldIdAndEntityId(
                            migratedDoc.getString("worldId"),
                            migratedDoc.getString("entityId")
                    ).orElse(null);

                    // Check if should import
                    if (!force && existing != null) {
                        Object fileUpdatedAt = migratedDoc.get("updatedAt");
                        Object dbUpdatedAt = existing.get("updatedAt");
                        if (fileUpdatedAt != null && dbUpdatedAt != null) {
                            if (dbUpdatedAt.toString().compareTo(fileUpdatedAt.toString()) > 0) {
                                log.debug("Skipping entity {} (DB is newer)", entityId);
                                continue;
                            }
                        }
                    }

                    // Upsert through the owner (reconciles _id by the unique key)
                    entityService.upsertDocument(migratedDoc);
                    log.debug("Imported entity: {}", entityId);
                    imported++;

                } catch (Exception e) {
                    log.warn("Failed to import entity from file: " + file, e);
                }
            }
        }

        // Remove overtaken if requested
        int deleted = 0;
        if (removeOvertaken) {
            List<WEntity> dbEntities = entityService.findByWorldId(worldId);

            for (WEntity entity : dbEntities) {
                if (!filesystemEntityIds.contains(entity.getName())) {
                    entityService.delete(worldId, entity.getName());
                    log.info("Deleted entity not in filesystem: {}", entity.getName());
                    deleted++;
                }
            }
        }

        return ResourceSyncType.ImportResult.of(imported, deleted);
    }
}
