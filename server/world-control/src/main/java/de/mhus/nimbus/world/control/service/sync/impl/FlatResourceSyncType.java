package de.mhus.nimbus.world.control.service.sync.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import de.mhus.nimbus.shared.service.SchemaMigrationService;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.control.service.sync.DocumentTransformer;
import de.mhus.nimbus.world.control.service.sync.ResourceSyncType;
import de.mhus.nimbus.world.shared.dto.ExternalResourceDTO;
import de.mhus.nimbus.world.shared.generator.WFlatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Qualifier;
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
 * Import/export implementation for WFlat entities.
 * Flats are organized by layerDataId subdirectory.
 * Unique constraint: worldId + layerDataId + flatId.
 * File structure: flats/{layerDataId}/{flatId}.yaml
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FlatResourceSyncType implements ResourceSyncType {

    private final WFlatService flatService;
    private final SchemaMigrationService migrationService;
    private final DocumentTransformer documentTransformer;
    private final ObjectMapper objectMapper;

    @Qualifier("syncYamlMapper")
    private final YAMLMapper yamlMapper;

    @Override
    public String name() {
        return "flat";
    }

    @Override
    public ExportResult export(Path dataPath, WorldId worldId, boolean force, boolean removeOvertaken) throws IOException {
        Path flatsDir = dataPath.resolve("flats");
        Files.createDirectories(flatsDir);

        List<Document> documents = flatService.exportDocuments(worldId.getId());

        // Track exported IDs per layerDataId for removeOvertaken
        Map<String, Set<String>> dbFlatIds = new HashMap<>();
        int exported = 0;

        for (Document doc : documents) {
            try {
                String layerDataId = doc.getString("layerDataId");
                String flatId = doc.getString("flatId");

                if (flatId == null) {
                    log.warn("WFlat without flatId, skipping");
                    continue;
                }

                String subDir = layerDataId != null ? layerDataId : "_default";
                dbFlatIds.computeIfAbsent(subDir, k -> new HashSet<>()).add(flatId);

                Path layerDir = flatsDir.resolve(subDir);
                Files.createDirectories(layerDir);

                Path targetFile = layerDir.resolve(flatId + ".yaml");
                yamlMapper.writeValue(targetFile.toFile(), doc);
                log.debug("Exported WFlat: layerDataId={}, flatId={}", layerDataId, flatId);
                exported++;

            } catch (Exception e) {
                log.warn("Failed to export WFlat", e);
            }
        }

        int deleted = 0;
        if (removeOvertaken && Files.exists(flatsDir)) {
            try (Stream<Path> layerDirs = Files.list(flatsDir)) {
                for (Path layerDir : layerDirs.filter(Files::isDirectory).toList()) {
                    String subDir = layerDir.getFileName().toString();
                    Set<String> dbIds = dbFlatIds.getOrDefault(subDir, Set.of());

                    try (Stream<Path> files = Files.list(layerDir)) {
                        for (Path file : files.filter(f -> f.toString().endsWith(".yaml")).toList()) {
                            String filename = file.getFileName().toString();
                            String flatId = filename.substring(0, filename.length() - 5);

                            if (!dbIds.contains(flatId)) {
                                Files.delete(file);
                                log.info("Deleted file not in database: {}", file);
                                deleted++;
                            }
                        }
                    }

                    // Clean up empty directories
                    try (Stream<Path> files = Files.list(layerDir)) {
                        if (files.findAny().isEmpty()) {
                            Files.delete(layerDir);
                            log.debug("Deleted empty layer directory: {}", layerDir);
                        }
                    }
                }
            }
        }

        return ExportResult.of(exported, deleted);
    }

    @Override
    public ImportResult importData(Path dataPath, WorldId worldId, ExternalResourceDTO definition, boolean force, boolean removeOvertaken) throws IOException {
        Path flatsDir = dataPath.resolve("flats");
        if (!Files.exists(flatsDir)) {
            log.info("No flats directory found");
            return ImportResult.of(0, 0);
        }

        // Track composite keys: "layerDataId:flatId"
        Set<String> filesystemKeys = new HashSet<>();
        int imported = 0;

        try (Stream<Path> layerDirs = Files.list(flatsDir)) {
            for (Path layerDir : layerDirs.filter(Files::isDirectory).toList()) {
                try (Stream<Path> files = Files.list(layerDir)) {
                    for (Path file : files.filter(f -> f.toString().endsWith(".yaml")).toList()) {
                        try {
                            Document doc = yamlMapper.readValue(file.toFile(), Document.class);
                            String flatId = doc.getString("flatId");
                            String layerDataId = doc.getString("layerDataId");

                            if (flatId == null) {
                                log.warn("WFlat missing flatId in file: {}", file);
                                continue;
                            }

                            filesystemKeys.add((layerDataId != null ? layerDataId : "_default") + ":" + flatId);

                            String json = objectMapper.writeValueAsString(doc);

                            String entityType = doc.getString("_class");
                            if (entityType == null) {
                                entityType = "de.mhus.nimbus.world.shared.generator.WFlat";
                            }

                            String migratedJson = migrationService.migrateToLatest(json, entityType);
                            Document migratedDoc = Document.parse(migratedJson);

                            migratedDoc = documentTransformer.transformForImport(migratedDoc, definition);

                            // Find existing by unique constraint (worldId + layerDataId + flatId)
                            Document existing = flatService.findDocumentByWorldIdAndLayerDataIdAndFlatId(
                                    migratedDoc.getString("worldId"),
                                    migratedDoc.getString("layerDataId"),
                                    migratedDoc.getString("flatId")
                            ).orElse(null);

                            if (!force && existing != null) {
                                Object fileUpdatedAt = migratedDoc.get("updatedAt");
                                Object dbUpdatedAt = existing.get("updatedAt");
                                if (fileUpdatedAt != null && dbUpdatedAt != null) {
                                    if (dbUpdatedAt.toString().compareTo(fileUpdatedAt.toString()) > 0) {
                                        log.debug("Skipping WFlat {} (DB is newer)", flatId);
                                        continue;
                                    }
                                }
                            }

                            // Upsert through the owner (reconciles _id by the unique key)
                            flatService.upsertDocument(migratedDoc);
                            log.debug("Imported WFlat: layerDataId={}, flatId={}", layerDataId, flatId);
                            imported++;

                        } catch (Exception e) {
                            log.warn("Failed to import WFlat from file: " + file, e);
                        }
                    }
                }
            }
        }

        int deleted = 0;
        if (removeOvertaken) {
            List<Document> dbFlats = flatService.exportDocuments(worldId.getId());

            for (Document doc : dbFlats) {
                String flatId = doc.getString("flatId");
                String layerDataId = doc.getString("layerDataId");
                if (flatId == null) continue;

                String key = (layerDataId != null ? layerDataId : "_default") + ":" + flatId;
                if (!filesystemKeys.contains(key)) {
                    flatService.deleteDocumentById(doc.get("_id"));
                    log.info("Deleted WFlat not in filesystem: layerDataId={}, flatId={}", layerDataId, flatId);
                    deleted++;
                }
            }
        }

        return ImportResult.of(imported, deleted);
    }
}
