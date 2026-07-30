package de.mhus.nimbus.world.control.service.sync.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import de.mhus.nimbus.shared.service.SchemaMigrationService;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.control.service.sync.DocumentTransformer;
import de.mhus.nimbus.world.control.service.sync.ResourceSyncType;
import de.mhus.nimbus.world.shared.dto.ExternalResourceDTO;
import de.mhus.nimbus.world.shared.world.WEntityModel;
import de.mhus.nimbus.world.shared.world.WEntityModelService;
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
 * Import/export implementation for entity models.
 * Exports MongoDB documents directly as YAML files (preserves all fields including _schema).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EntityModelResourceSyncType implements ResourceSyncType {

    private final WEntityModelService entityModelService;
    private final SchemaMigrationService migrationService;
    private final DocumentTransformer documentTransformer;
    private final ObjectMapper objectMapper;

    @Qualifier("syncYamlMapper")
    private final YAMLMapper yamlMapper;

    @Override
    public String name() {
        return "entitymodel";
    }

    @Override
    public ResourceSyncType.ExportResult export(Path dataPath, WorldId worldId, boolean force, boolean removeOvertaken) throws IOException {
        Path entityModelsDir = dataPath.resolve("entitymodels");
        Files.createDirectories(entityModelsDir);

        // Get entity models as raw Documents through the owner service
        List<Document> documents = entityModelService.exportDocuments(worldId.getId());

        Set<String> dbEntityModelIds = new HashSet<>();
        int exported = 0;

        for (Document doc : documents) {
            try {
                String modelId = doc.getString("name");
                if (modelId == null) {
                    log.warn("EntityModel without name, skipping");
                    continue;
                }

                dbEntityModelIds.add(modelId);

                // Sanitize filename (replace : with _)
                String filename = modelId.replace(":", "_") + ".yaml";
                Path targetFile = entityModelsDir.resolve(filename);

                // Write MongoDB Document directly as YAML
                yamlMapper.writeValue(targetFile.toFile(), doc);
                log.debug("Exported entity model: {}", modelId);
                exported++;

            } catch (Exception e) {
                log.warn("Failed to export entity model document", e);
            }
        }

        // Remove files not in DB if requested
        int deleted = 0;
        if (removeOvertaken && Files.exists(entityModelsDir)) {
            try (Stream<Path> files = Files.list(entityModelsDir)) {
                for (Path file : files.filter(f -> f.toString().endsWith(".yaml")).toList()) {
                    try {
                        Document doc = yamlMapper.readValue(file.toFile(), Document.class);
                        String modelId = doc.getString("name");

                        if (!dbEntityModelIds.contains(modelId)) {
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
        Path entityModelsDir = dataPath.resolve("entitymodels");
        if (!Files.exists(entityModelsDir)) {
            log.info("No entitymodels directory found");
            return ResourceSyncType.ImportResult.of(0, 0);
        }

        // Collect filesystem entity model IDs
        Set<String> filesystemEntityModelIds = new HashSet<>();
        int imported = 0;

        try (Stream<Path> files = Files.list(entityModelsDir)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".yaml")).toList()) {
                try {
                    // Read YAML as Document
                    Document doc = yamlMapper.readValue(file.toFile(), Document.class);
                    String modelId = doc.getString("name");
                    filesystemEntityModelIds.add(modelId);

                    String json = objectMapper.writeValueAsString(doc);

                    // Migrate if needed
                    String entityType = doc.getString("_class");
                    if (entityType == null) {
                        entityType = "de.mhus.nimbus.world.shared.world.WEntityModel";
                    }

                    String migratedJson = migrationService.migrateToLatest(json, entityType);
                    Document migratedDoc = Document.parse(migratedJson);

                    // Transform document (worldId replacement + prefix mapping)
                    migratedDoc = documentTransformer.transformForImport(migratedDoc, definition);

                    // Find existing by unique constraint (worldId + name).
                    // BUG FIX: the owner keys on the actual natural-key field 'name'
                    // (the previous code queried a non-existent 'modelId' field).
                    Document existing = entityModelService.findDocumentByWorldIdAndName(
                            migratedDoc.getString("worldId"),
                            migratedDoc.getString("name")
                    ).orElse(null);

                    // Check if should import
                    if (!force && existing != null) {
                        Object fileUpdatedAt = migratedDoc.get("updatedAt");
                        Object dbUpdatedAt = existing.get("updatedAt");
                        if (fileUpdatedAt != null && dbUpdatedAt != null) {
                            if (dbUpdatedAt.toString().compareTo(fileUpdatedAt.toString()) > 0) {
                                log.debug("Skipping entity model {} (DB is newer)", modelId);
                                continue;
                            }
                        }
                    }

                    // Upsert through the owner (reconciles _id by the unique key)
                    entityModelService.upsertDocument(migratedDoc);
                    log.debug("Imported entity model: {}", modelId);
                    imported++;

                } catch (Exception e) {
                    log.warn("Failed to import entity model from file: " + file, e);
                }
            }
        }

        // Remove overtaken if requested
        int deleted = 0;
        if (removeOvertaken) {
            List<WEntityModel> dbEntityModels = entityModelService.findByWorldId(worldId);

            for (WEntityModel entityModel : dbEntityModels) {
                if (!filesystemEntityModelIds.contains(entityModel.getName())) {
                    entityModelService.delete(worldId, entityModel.getName());
                    log.info("Deleted entity model not in filesystem: {}", entityModel.getName());
                    deleted++;
                }
            }
        }

        return ResourceSyncType.ImportResult.of(imported, deleted);
    }
}

