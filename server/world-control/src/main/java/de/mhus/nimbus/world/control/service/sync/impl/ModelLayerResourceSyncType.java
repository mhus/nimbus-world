package de.mhus.nimbus.world.control.service.sync.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import de.mhus.nimbus.shared.service.SchemaMigrationService;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.control.service.sync.DocumentTransformer;
import de.mhus.nimbus.world.control.service.sync.ResourceSyncType;
import de.mhus.nimbus.world.shared.dto.ExternalResourceDTO;
import de.mhus.nimbus.world.shared.layer.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Import/export implementation for model layers.
 * Exports MongoDB documents directly as YAML files (preserves all fields including _schema).
 * Structure: models/{layerName}/_info.yaml + models/{layerName}/{modelName}.yaml
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ModelLayerResourceSyncType implements ResourceSyncType {

    private final WLayerService layerService;
    private final SchemaMigrationService migrationService;
    private final DocumentTransformer documentTransformer;
    private final ObjectMapper objectMapper;

    @Qualifier("syncYamlMapper")
    private final YAMLMapper yamlMapper;

    @Override
    public String name() {
        return "model";
    }

    @Override
    public ResourceSyncType.ExportResult export(Path dataPath, WorldId worldId, boolean force, boolean removeOvertaken) throws IOException {
        Path modelsDir = dataPath.resolve("models");
        Files.createDirectories(modelsDir);

        // Get MODEL layer documents through the owner service
        List<Document> layerDocs = layerService.exportLayerDocuments(worldId.getId(), LayerType.MODEL);

        Set<String> dbLayerNames = new HashSet<>();
        int exported = 0;

        for (Document layerDoc : layerDocs) {
            try {
                String layerName = layerDoc.getString("title");
                String layerDataId = layerDoc.getString("layerDataId");
                if (layerName == null || layerDataId == null) {
                    log.warn("Layer without title or layerDataId, skipping");
                    continue;
                }

                dbLayerNames.add(layerName);
                Path layerDir = modelsDir.resolve(layerName);
                Files.createDirectories(layerDir);

                // Export layer as _info.yaml
                Path infoFile = layerDir.resolve("_info.yaml");
                yamlMapper.writeValue(infoFile.toFile(), layerDoc);
                exported++;

                // Export models for this layer through the owner service
                List<Document> modelDocs = layerService.exportModelDocumentsByLayerDataId(layerDataId);

                for (Document modelDoc : modelDocs) {
                    String modelName = modelDoc.getString("title");
                    if (modelName == null) {
                        log.warn("Model without title, skipping");
                        continue;
                    }

                    Path modelFile = layerDir.resolve(modelName + ".yaml");
                    yamlMapper.writeValue(modelFile.toFile(), modelDoc);
                    exported++;
                }

                log.debug("Exported model layer: {} with {} models", layerName, modelDocs.size());

            } catch (Exception e) {
                log.warn("Failed to export model layer document", e);
            }
        }

        // Remove layer folders not in DB if requested
        int deleted = 0;
        if (removeOvertaken && Files.exists(modelsDir)) {
            try (Stream<Path> layerDirs = Files.list(modelsDir)) {
                for (Path layerDir : layerDirs.filter(Files::isDirectory).toList()) {
                    String layerName = layerDir.getFileName().toString();
                    if (!dbLayerNames.contains(layerName)) {
                        deleteRecursively(layerDir);
                        log.info("Deleted layer folder not in database: {}", layerName);
                        deleted++;
                    }
                }
            }
        }

        return ResourceSyncType.ExportResult.of(exported, deleted);
    }

    @Override
    public ResourceSyncType.ImportResult importData(Path dataPath, WorldId worldId, ExternalResourceDTO definition, boolean force, boolean removeOvertaken) throws IOException {
        Path modelsDir = dataPath.resolve("models");
        if (!Files.exists(modelsDir)) {
            log.info("No models directory found");
            return ResourceSyncType.ImportResult.of(0, 0);
        }

        Set<String> filesystemLayerNames = new HashSet<>();
        Map<String, Set<String>> filesystemModelNames = new HashMap<>();
        int imported = 0;

        try (Stream<Path> layerDirs = Files.list(modelsDir)) {
            for (Path layerDir : layerDirs.filter(Files::isDirectory).toList()) {
                String layerName = layerDir.getFileName().toString();
                filesystemLayerNames.add(layerName);
                Set<String> modelNames = new HashSet<>();
                filesystemModelNames.put(layerName, modelNames);

                Path infoFile = layerDir.resolve("_info.yaml");
                if (!Files.exists(infoFile)) {
                    log.warn("No _info.yaml found in: {}", layerDir);
                    continue;
                }

                try {
                    // Import layer from _info.yaml
                    Document layerDoc = yamlMapper.readValue(infoFile.toFile(), Document.class);

                    String json = objectMapper.writeValueAsString(layerDoc);
                    String entityType = layerDoc.getString("_class");
                    if (entityType == null) {
                        entityType = "de.mhus.nimbus.world.shared.layer.WLayer";
                    }
                    String migratedJson = migrationService.migrateToLatest(json, entityType);
                    Document migratedLayerDoc = Document.parse(migratedJson);

                    // Transform document (worldId replacement + prefix mapping)
                    migratedLayerDoc = documentTransformer.transformForImport(migratedLayerDoc, definition);
                    documentTransformer.ensureEpoches(migratedLayerDoc);

                    // Find existing layer by worldId + title (unique constraint)
                    String targetWorldId = migratedLayerDoc.getString("worldId");
                    String targetName = migratedLayerDoc.getString("title");

                    Document existingLayer = layerService.findLayerDocumentByWorldIdAndName(targetWorldId, targetName).orElse(null);

                    // Check if should import
                    if (!force && existingLayer != null) {
                        Object fileUpdatedAt = migratedLayerDoc.get("updatedAt");
                        Object dbUpdatedAt = existingLayer.get("updatedAt");
                        if (fileUpdatedAt != null && dbUpdatedAt != null) {
                            if (dbUpdatedAt.toString().compareTo(fileUpdatedAt.toString()) > 0) {
                                log.debug("Skipping layer {} (DB is newer)", layerName);
                                continue;
                            }
                        }
                    }

                    // Upsert the layer through the owner (reconciles _id by worldId + title)
                    layerService.upsertLayerDocument(migratedLayerDoc);
                    imported++;

                    // Import models
                    try (Stream<Path> modelFiles = Files.list(layerDir)) {
                        for (Path modelFile : modelFiles.filter(f -> f.toString().endsWith(".yaml") && !f.getFileName().toString().equals("_info.yaml")).toList()) {
                            try {
                                Document modelDoc = yamlMapper.readValue(modelFile.toFile(), Document.class);
                                String modelName = modelDoc.getString("title");
                                modelNames.add(modelName);

                                String modelJson = objectMapper.writeValueAsString(modelDoc);
                                String modelEntityType = modelDoc.getString("_class");
                                if (modelEntityType == null) {
                                    modelEntityType = "de.mhus.nimbus.world.shared.layer.WLayerModel";
                                }
                                String migratedModelJson = migrationService.migrateToLatest(modelJson, modelEntityType);
                                Document migratedModelDoc = Document.parse(migratedModelJson);

                                // Transform document (worldId replacement + prefix mapping)
                                migratedModelDoc = documentTransformer.transformForImport(migratedModelDoc, definition);

                                // Find existing model by worldId + layerDataId + title (should be unique)
                                String modelTargetWorldId = migratedModelDoc.getString("worldId");
                                String modelLayerDataId = migratedModelDoc.getString("layerDataId");
                                String modelTargetName = migratedModelDoc.getString("title");

                                Document existingModel = layerService.findModelDocumentByWorldIdAndLayerDataIdAndName(
                                        modelTargetWorldId, modelLayerDataId, modelTargetName
                                ).orElse(null);

                                // Check if should import
                                if (!force && existingModel != null) {
                                    Object fileUpdatedAt = migratedModelDoc.get("updatedAt");
                                    Object dbUpdatedAt = existingModel.get("updatedAt");
                                    if (fileUpdatedAt != null && dbUpdatedAt != null) {
                                        if (dbUpdatedAt.toString().compareTo(fileUpdatedAt.toString()) > 0) {
                                            log.debug("Skipping model {} (DB is newer)", modelName);
                                            continue;
                                        }
                                    }
                                }

                                // Upsert the model through the owner (reconciles _id by worldId + layerDataId + title)
                                layerService.upsertModelDocument(migratedModelDoc);
                                imported++;

                            } catch (Exception e) {
                                log.warn("Failed to import model from file: " + modelFile, e);
                            }
                        }
                    }

                    log.debug("Imported model layer: {}", layerName);

                } catch (Exception e) {
                    log.warn("Failed to import model layer from: " + layerDir, e);
                }
            }
        }

        // Remove overtaken if requested
        int deleted = 0;
        if (removeOvertaken) {
            List<WLayer> dbLayers = layerService.findLayersByWorld(worldId.getId()).stream()
                    .filter(l -> l.getLayerType() == LayerType.MODEL)
                    .toList();

            for (WLayer layer : dbLayers) {
                if (!filesystemLayerNames.contains(layer.getName())) {
                    layerService.deleteLayer(worldId.getId(), layer.getName());
                    log.info("Deleted model layer not in filesystem: {}", layer.getName());
                    deleted++;
                } else if (filesystemModelNames.containsKey(layer.getName())) {
                    // Remove models within this layer
                    Set<String> fsModels = filesystemModelNames.get(layer.getName());
                    List<WLayerModel> dbModels = layerService.findModelsByLayerDataId(layer.getLayerDataId());
                    for (WLayerModel model : dbModels) {
                        if (!fsModels.contains(model.getName())) {
                            layerService.deleteModel(model);
                            log.info("Deleted model not in filesystem: {}/{}", layer.getName(), model.getName());
                            deleted++;
                        }
                    }
                }
            }
        }

        return ResourceSyncType.ImportResult.of(imported, deleted);
    }

    /**
     * Delete directory recursively.
     */
    private void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (Stream<Path> entries = Files.list(path)) {
                for (Path entry : entries.toList()) {
                    deleteRecursively(entry);
                }
            }
        }
        Files.delete(path);
    }
}
