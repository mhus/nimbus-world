package de.mhus.nimbus.world.control.service.sync.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import de.mhus.nimbus.shared.service.SchemaMigrationService;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.control.service.sync.DocumentTransformer;
import de.mhus.nimbus.world.control.service.sync.ResourceSyncType;
import de.mhus.nimbus.world.shared.dto.ExternalResourceDTO;
import de.mhus.nimbus.world.shared.world.SAsset;
import de.mhus.nimbus.world.shared.world.SAssetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Import/export implementation for assets.
 * Exports MongoDB documents directly as .info.yaml + binary files.
 * Binary data comes from StorageService, metadata from MongoDB documents.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AssetResourceSyncType implements ResourceSyncType {

    private static final String COLLECTION_NAME = "s_assets";

    private final SAssetService assetService;
    private final MongoTemplate mongoTemplate;
    private final SchemaMigrationService migrationService;
    private final DocumentTransformer documentTransformer;
    private final ObjectMapper objectMapper;

    @Qualifier("syncYamlMapper")
    private final YAMLMapper yamlMapper;

    @Override
    public String name() {
        return "asset";
    }

    @Override
    public ResourceSyncType.ExportResult export(Path dataPath, WorldId worldId, boolean force, boolean removeOvertaken) throws IOException {
        Path assetsDir = dataPath.resolve("assets");
        Files.createDirectories(assetsDir);

        // Get assets directly from MongoDB as Documents
        Query query = new Query(Criteria.where("worldId").is(worldId.getId()));
        List<Document> documents = mongoTemplate.find(query, Document.class, COLLECTION_NAME);

        Set<String> dbAssetPaths = new HashSet<>();
        int exported = 0;

        for (Document doc : documents) {
            try {
                String path = doc.getString("path");
                Object assetIdObj = doc.get("_id");
                if (path == null || assetIdObj == null) {
                    log.warn("Asset without path or _id, skipping");
                    continue;
                }

                String assetId = assetIdObj.toString(); // ObjectId to String
                dbAssetPaths.add(path);
                Path targetBinary = assetsDir.resolve(path);
                Path targetInfo = assetsDir.resolve(path + ".info.yaml");

                // Create parent directories
                Files.createDirectories(targetBinary.getParent());

                // Find SAsset entity for binary data
                List<SAsset> assets = assetService.findByWorldId(worldId);
                SAsset asset = assets.stream()
                        .filter(a -> assetId.equals(a.getId()))
                        .findFirst()
                        .orElse(null);

                if (asset == null) {
                    log.warn("Asset entity not found for document: {}", assetId);
                    continue;
                }

                // Export binary data
                try (InputStream stream = assetService.loadContent(asset)) {
                    Files.copy(stream, targetBinary, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }

                // Export metadata as MongoDB Document (preserves all fields)
                yamlMapper.writeValue(targetInfo.toFile(), doc);
                log.debug("Exported asset: {}", path);
                exported++;

            } catch (Exception e) {
                log.warn("Failed to export asset document", e);
            }
        }

        // Remove files not in DB if requested
        int deleted = 0;
        if (removeOvertaken && Files.exists(assetsDir)) {
            try (Stream<Path> paths = Files.walk(assetsDir)) {
                List<Path> infoFiles = paths.filter(p -> p.toString().endsWith(".info.yaml")).toList();

                for (Path infoFile : infoFiles) {
                    try {
                        Document doc = yamlMapper.readValue(infoFile.toFile(), Document.class);
                        String path = doc.getString("path");

                        if (!dbAssetPaths.contains(path)) {
                            // Delete both info and binary file
                            Files.delete(infoFile);
                            String relativePath = assetsDir.relativize(infoFile).toString();
                            relativePath = relativePath.substring(0, relativePath.length() - ".info.yaml".length());
                            Path binaryFile = assetsDir.resolve(relativePath);
                            if (Files.exists(binaryFile)) {
                                Files.delete(binaryFile);
                            }
                            log.info("Deleted files not in database: {}", path);
                            deleted++;
                        }
                    } catch (IOException e) {
                        log.warn("Failed to check file for deletion: " + infoFile, e);
                    }
                }
            }
        }

        return ResourceSyncType.ExportResult.of(exported, deleted);
    }

    @Override
    public ResourceSyncType.ImportResult importData(Path dataPath, WorldId worldId, ExternalResourceDTO definition, boolean force, boolean removeOvertaken) throws IOException {
        Path assetsDir = dataPath.resolve("assets");
        if (!Files.exists(assetsDir)) {
            log.info("No assets directory found");
            return ResourceSyncType.ImportResult.of(0, 0);
        }

        // Collect filesystem assets
        Set<String> filesystemAssetPaths = new HashSet<>();
        int imported = 0;

        // Find all .info.yaml files recursively
        try (Stream<Path> paths = Files.walk(assetsDir)) {
            List<Path> infoFiles = paths
                    .filter(p -> p.toString().endsWith(".info.yaml"))
                    .toList();

            for (Path infoFile : infoFiles) {
                try {
                    // Read YAML as Document
                    Document doc = yamlMapper.readValue(infoFile.toFile(), Document.class);
                    String path = doc.getString("path");
                    filesystemAssetPaths.add(path);

                    // Get binary file path
                    String relativePath = assetsDir.relativize(infoFile).toString();
                    relativePath = relativePath.substring(0, relativePath.length() - ".info.yaml".length());
                    Path binaryFile = assetsDir.resolve(relativePath);

                    if (!Files.exists(binaryFile)) {
                        log.warn("Binary file not found for asset: {}", relativePath);
                        continue;
                    }

                    String json = objectMapper.writeValueAsString(doc);

                    // Migrate if needed
                    String entityType = doc.getString("_class");
                    if (entityType == null) {
                        entityType = "de.mhus.nimbus.world.shared.world.SAsset";
                    }

                    String migratedJson = migrationService.migrateToLatest(json, entityType);
                    Document migratedDoc = Document.parse(migratedJson);

                    // Transform document (worldId replacement + prefix mapping)
                    migratedDoc = documentTransformer.transformForImport(migratedDoc, definition);

                    // Find existing asset by worldId + path (unique constraint)
                    String targetWorldId = migratedDoc.getString("worldId");
                    String targetPath = migratedDoc.getString("path");

                    Query findQuery = new Query(
                            Criteria.where("worldId").is(targetWorldId)
                                    .and("path").is(targetPath)
                    );
                    Document existing = mongoTemplate.findOne(findQuery, Document.class, COLLECTION_NAME);

                    // Check if should import metadata
                    if (!force && existing != null) {
                        Object fileCreatedAt = migratedDoc.get("createdAt");
                        Object dbCreatedAt = existing.get("createdAt");
                        if (fileCreatedAt != null && dbCreatedAt != null) {
                            if (dbCreatedAt.toString().compareTo(fileCreatedAt.toString()) > 0) {
                                log.debug("Skipping asset {} (DB is newer)", path);
                                continue;
                            }
                        }
                    }

                    // Always remove _id from imported document first (may be serialized incorrectly)
                    migratedDoc.remove("_id");

                    if (existing != null) {
                        // Asset exists - use existing _id (ObjectId) to update in place
                        migratedDoc.put("_id", existing.get("_id"));

                        // Preserve existing storageId if present (will be updated by updateContent)
                        String existingStorageId = existing.getString("storageId");
                        if (existingStorageId != null) {
                            migratedDoc.put("storageId", existingStorageId);
                        }
                    }
                    // else: _id is removed, MongoDB will generate a new ObjectId

                    // Save metadata
                    mongoTemplate.save(migratedDoc, COLLECTION_NAME);

                    // Update binary content
                    SAsset asset = assetService.findByPath(worldId, path).orElse(null);
                    if (asset != null) {
                        try (InputStream stream = Files.newInputStream(binaryFile)) {
                            assetService.updateContent(asset, stream);
                        }
                    }

                    log.debug("Imported asset: {}", path);
                    imported++;

                } catch (Exception e) {
                    log.warn("Failed to import asset from file: " + infoFile, e);
                }
            }
        }

        // Remove overtaken if requested
        int deleted = 0;
        if (removeOvertaken) {
            List<SAsset> dbAssets = assetService.findByWorldId(worldId);

            for (SAsset asset : dbAssets) {
                if (!filesystemAssetPaths.contains(asset.getPath())) {
                    assetService.delete(asset);
                    log.info("Deleted asset not in filesystem: {}", asset.getPath());
                    deleted++;
                }
            }
        }

        return ResourceSyncType.ImportResult.of(imported, deleted);
    }
}
