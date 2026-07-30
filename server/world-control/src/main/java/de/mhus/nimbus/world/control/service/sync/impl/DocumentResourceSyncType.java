package de.mhus.nimbus.world.control.service.sync.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import de.mhus.nimbus.shared.service.SchemaMigrationService;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.control.service.sync.DocumentTransformer;
import de.mhus.nimbus.world.control.service.sync.ResourceSyncType;
import de.mhus.nimbus.world.shared.dto.ExternalResourceDTO;
import de.mhus.nimbus.world.shared.world.WDocument;
import de.mhus.nimbus.world.shared.world.WDocumentService;
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
 * Import/export implementation for WDocument entities.
 * Documents are organized by collection subdirectory.
 * Unique constraint: worldId + documentId.
 * File structure: documents/{collection}/{documentId}.yaml
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentResourceSyncType implements ResourceSyncType {

    private final WDocumentService documentService;
    private final SchemaMigrationService migrationService;
    private final DocumentTransformer documentTransformer;
    private final ObjectMapper objectMapper;

    @Qualifier("syncYamlMapper")
    private final YAMLMapper yamlMapper;

    @Override
    public String name() {
        return "document";
    }

    @Override
    public ExportResult export(Path dataPath, WorldId worldId, boolean force, boolean removeOvertaken) throws IOException {
        Path documentsDir = dataPath.resolve("documents");
        Files.createDirectories(documentsDir);

        List<Document> documents = documentService.exportDocuments(worldId.getId());

        Map<String, Set<String>> dbDocIds = new HashMap<>();
        int exported = 0;

        for (Document doc : documents) {
            try {
                String collection = doc.getString("collection");
                String documentId = doc.getString("documentId");

                if (documentId == null) {
                    log.warn("WDocument without documentId, skipping");
                    continue;
                }

                String subDir = collection != null ? collection : "_default";
                dbDocIds.computeIfAbsent(subDir, k -> new HashSet<>()).add(documentId);

                Path collectionDir = documentsDir.resolve(subDir);
                Files.createDirectories(collectionDir);

                Path targetFile = collectionDir.resolve(documentId + ".yaml");
                yamlMapper.writeValue(targetFile.toFile(), doc);
                log.debug("Exported WDocument: collection={}, documentId={}", collection, documentId);
                exported++;

            } catch (Exception e) {
                log.warn("Failed to export WDocument", e);
            }
        }

        int deleted = 0;
        if (removeOvertaken && Files.exists(documentsDir)) {
            try (Stream<Path> collectionDirs = Files.list(documentsDir)) {
                for (Path collectionDir : collectionDirs.filter(Files::isDirectory).toList()) {
                    String subDir = collectionDir.getFileName().toString();
                    Set<String> dbIds = dbDocIds.getOrDefault(subDir, Set.of());

                    try (Stream<Path> files = Files.list(collectionDir)) {
                        for (Path file : files.filter(f -> f.toString().endsWith(".yaml")).toList()) {
                            String filename = file.getFileName().toString();
                            String docId = filename.substring(0, filename.length() - 5);

                            if (!dbIds.contains(docId)) {
                                Files.delete(file);
                                log.info("Deleted file not in database: {}", file);
                                deleted++;
                            }
                        }
                    }

                    try (Stream<Path> files = Files.list(collectionDir)) {
                        if (files.findAny().isEmpty()) {
                            Files.delete(collectionDir);
                            log.debug("Deleted empty collection directory: {}", collectionDir);
                        }
                    }
                }
            }
        }

        return ExportResult.of(exported, deleted);
    }

    @Override
    public ImportResult importData(Path dataPath, WorldId worldId, ExternalResourceDTO definition, boolean force, boolean removeOvertaken) throws IOException {
        Path documentsDir = dataPath.resolve("documents");
        if (!Files.exists(documentsDir)) {
            log.info("No documents directory found");
            return ImportResult.of(0, 0);
        }

        Set<String> filesystemDocIds = new HashSet<>();
        int imported = 0;

        try (Stream<Path> collectionDirs = Files.list(documentsDir)) {
            for (Path collectionDir : collectionDirs.filter(Files::isDirectory).toList()) {
                try (Stream<Path> files = Files.list(collectionDir)) {
                    for (Path file : files.filter(f -> f.toString().endsWith(".yaml")).toList()) {
                        try {
                            Document doc = yamlMapper.readValue(file.toFile(), Document.class);
                            String documentId = doc.getString("documentId");

                            if (documentId == null) {
                                log.warn("WDocument missing documentId in file: {}", file);
                                continue;
                            }

                            filesystemDocIds.add(documentId);

                            String json = objectMapper.writeValueAsString(doc);

                            String entityType = doc.getString("_class");
                            if (entityType == null) {
                                entityType = "de.mhus.nimbus.world.shared.world.WDocument";
                            }

                            String migratedJson = migrationService.migrateToLatest(json, entityType);
                            Document migratedDoc = Document.parse(migratedJson);

                            migratedDoc = documentTransformer.transformForImport(migratedDoc, definition);

                            // Find existing by unique constraint (worldId + documentId)
                            Document existing = documentService.findDocumentByWorldIdAndDocumentId(
                                    migratedDoc.getString("worldId"),
                                    migratedDoc.getString("documentId")
                            ).orElse(null);

                            if (!force && existing != null) {
                                Object fileUpdatedAt = migratedDoc.get("updatedAt");
                                Object dbUpdatedAt = existing.get("updatedAt");
                                if (fileUpdatedAt != null && dbUpdatedAt != null) {
                                    if (dbUpdatedAt.toString().compareTo(fileUpdatedAt.toString()) > 0) {
                                        log.debug("Skipping WDocument {} (DB is newer)", documentId);
                                        continue;
                                    }
                                }
                            }

                            // Upsert through the owner (reconciles _id by the unique key)
                            documentService.upsertDocument(migratedDoc);
                            log.debug("Imported WDocument: documentId={}", documentId);
                            imported++;

                        } catch (Exception e) {
                            log.warn("Failed to import WDocument from file: " + file, e);
                        }
                    }
                }
            }
        }

        int deleted = 0;
        if (removeOvertaken) {
            List<Document> dbDocuments = documentService.exportDocuments(worldId.getId());

            for (Document doc : dbDocuments) {
                String documentId = doc.getString("documentId");
                if (documentId == null) continue;

                // Skip read-only documents (imported from resources, not synced)
                Boolean readOnly = doc.getBoolean("readOnly");
                if (readOnly != null && readOnly) continue;

                if (!filesystemDocIds.contains(documentId)) {
                    documentService.deleteDocumentById(doc.get("_id"));
                    log.info("Deleted WDocument not in filesystem: documentId={}", documentId);
                    deleted++;
                }
            }
        }

        return ImportResult.of(imported, deleted);
    }
}
