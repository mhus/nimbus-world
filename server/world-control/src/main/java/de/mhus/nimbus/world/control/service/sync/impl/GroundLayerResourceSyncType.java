package de.mhus.nimbus.world.control.service.sync.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import de.mhus.nimbus.shared.service.SchemaMigrationService;
import de.mhus.nimbus.shared.storage.StorageData;
import de.mhus.nimbus.shared.storage.StorageDataRepository;
import de.mhus.nimbus.shared.storage.StorageService;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.control.service.sync.DocumentTransformer;
import de.mhus.nimbus.world.control.service.sync.ResourceSyncType;
import de.mhus.nimbus.world.shared.dto.ExternalResourceDTO;
import de.mhus.nimbus.world.shared.layer.*;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Import/export implementation for ground layers.
 * WLayer exported as YAML from MongoDB documents, WLayerTerrain as JSON chunks (StorageService).
 * Chunks are organized in subfolders (cx/100)_(cz/100).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GroundLayerResourceSyncType implements ResourceSyncType {

    private static final String LAYER_COLLECTION = "w_layers";

    private final WLayerService layerService;
    private final WLayerRepository layerRepository;
    private final WLayerTerrainRepository terrainRepository;
    private final StorageDataRepository storageDataRepository;
    private final StorageService storageService;
    private final MongoTemplate mongoTemplate;
    private final SchemaMigrationService migrationService;
    private final DocumentTransformer documentTransformer;
    private final ObjectMapper objectMapper;

    @Qualifier("syncYamlMapper")
    private final YAMLMapper yamlMapper;

    @Override
    public String name() {
        return "ground";
    }

    @Override
    public ResourceSyncType.ExportResult export(Path dataPath, WorldId worldId, boolean force, boolean removeOvertaken) throws IOException {
        Path groundDir = dataPath.resolve("ground");
        Files.createDirectories(groundDir);

        // Get GROUND layers from MongoDB as Documents
        Query layerQuery = Query.query(
                Criteria.where("worldId").is(worldId.getId())
                        .and("layerType").is("GROUND")
        );
        List<Document> layerDocs = mongoTemplate.find(layerQuery, Document.class, LAYER_COLLECTION);

        Set<String> dbLayerNames = new HashSet<>();
        int exported = 0;

        for (Document layerDoc : layerDocs) {
            try {
                String layerName = layerDoc.getString("name");
                String layerDataId = layerDoc.getString("layerDataId");
                if (layerName == null || layerDataId == null) {
                    log.warn("Layer without name or layerDataId, skipping");
                    continue;
                }

                dbLayerNames.add(layerName);
                Path layerDir = groundDir.resolve(layerName);
                Files.createDirectories(layerDir);

                // Export layer as _info.yaml (MongoDB Document)
                Path infoFile = layerDir.resolve("_info.yaml");
                yamlMapper.writeValue(infoFile.toFile(), layerDoc);
                exported++;

                // Export schema info from first chunk's StorageData
                Path schemaFile = layerDir.resolve("_schema.yaml");
                exportSchemaInfo(layerDataId, schemaFile);

                // Export terrain chunks (from StorageService)
                List<WLayerTerrain> terrains = terrainRepository.findByLayerDataId(layerDataId);
                for (WLayerTerrain terrain : terrains) {
                    try {
                        String[] parts = terrain.getChunkKey().split(":");
                        if (parts.length != 2) {
                            log.warn("Invalid chunkKey format: {}", terrain.getChunkKey());
                            continue;
                        }
                        int cx = Integer.parseInt(parts[0]);
                        int cz = Integer.parseInt(parts[1]);

                        // Calculate subfolder (cx/100)_(cz/100)
                        int folderX = cx / 100;
                        int folderZ = cz / 100;
                        String subfolderName = folderX + "_" + folderZ;
                        Path subfolder = layerDir.resolve(subfolderName);
                        Files.createDirectories(subfolder);

                        Path chunkFile = subfolder.resolve("chunk_" + cx + "_" + cz + ".json");

                        // Load chunk data directly from StorageService as JSON
                        if (terrain.getStorageId() == null) {
                            log.warn("Terrain chunk has no storageId: {}", terrain.getChunkKey());
                            continue;
                        }

                        try (InputStream stream = storageService.load(terrain.getStorageId())) {
                            // Read JSON from storage (may be compressed)
                            byte[] data = stream.readAllBytes();
                            String json;

                            if (terrain.isCompressed()) {
                                // Decompress if needed
                                try (java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(data);
                                     java.util.zip.GZIPInputStream gzis = new java.util.zip.GZIPInputStream(bis)) {
                                    json = new String(gzis.readAllBytes(), StandardCharsets.UTF_8);
                                }
                            } else {
                                json = new String(data, StandardCharsets.UTF_8);
                            }

                            // Format JSON for better git diff (newlines, no spaces)
                            String formattedJson = formatJsonForGit(json);

                            // Write formatted JSON to file
                            Files.writeString(chunkFile, formattedJson);
                            exported++;
                        }

                    } catch (Exception e) {
                        log.warn("Failed to export terrain chunk: " + terrain.getChunkKey(), e);
                    }
                }

                log.debug("Exported ground layer: {} with {} chunks", layerName, terrains.size());

            } catch (Exception e) {
                log.warn("Failed to export ground layer document", e);
            }
        }

        // Remove layer folders not in DB if requested
        int deleted = 0;
        if (removeOvertaken && Files.exists(groundDir)) {
            try (Stream<Path> dirs = Files.list(groundDir)) {
                for (Path dir : dirs.filter(Files::isDirectory).toList()) {
                    String dirName = dir.getFileName().toString();
                    if (!dbLayerNames.contains(dirName)) {
                        deleteRecursively(dir);
                        log.info("Deleted layer folder not in database: {}", dirName);
                        deleted++;
                    }
                }
            }
        }

        return ResourceSyncType.ExportResult.of(exported, deleted);
    }

    @Override
    public ResourceSyncType.ImportResult importData(Path dataPath, WorldId worldId, ExternalResourceDTO definition, boolean force, boolean removeOvertaken) throws IOException {
        Path groundDir = dataPath.resolve("ground");
        if (!Files.exists(groundDir)) {
            log.info("No ground directory found");
            return ResourceSyncType.ImportResult.of(0, 0);
        }

        Set<String> filesystemLayerNames = new HashSet<>();
        Map<String, Set<String>> filesystemChunks = new HashMap<>();
        int imported = 0;

        try (Stream<Path> layerDirs = Files.list(groundDir)) {
            for (Path layerDir : layerDirs.filter(Files::isDirectory).toList()) {
                String layerName = layerDir.getFileName().toString();
                filesystemLayerNames.add(layerName);
                Set<String> chunkKeys = new HashSet<>();
                filesystemChunks.put(layerName, chunkKeys);

                Path infoFile = layerDir.resolve("_info.yaml");
                if (!Files.exists(infoFile)) {
                    log.warn("No _info.yaml found in: {}", layerDir);
                    continue;
                }

                try {
                    // Import layer from _info.yaml (MongoDB Document)
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

                    // Find existing layer by unique constraint (worldId + name)
                    String targetWorldId = migratedLayerDoc.getString("worldId");
                    String targetName = migratedLayerDoc.getString("name");

                    Query findLayerQuery = new Query(
                            Criteria.where("worldId").is(targetWorldId)
                                    .and("name").is(targetName)
                    );
                    Document existingLayer = mongoTemplate.findOne(findLayerQuery, Document.class, LAYER_COLLECTION);

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

                    // Always remove _id from imported document first (may be serialized incorrectly)
                    migratedLayerDoc.remove("_id");

                    // If existing, use its ObjectId to update in place
                    if (existingLayer != null) {
                        migratedLayerDoc.put("_id", existingLayer.get("_id"));
                    }
                    // else: _id is removed, MongoDB will generate a new ObjectId

                    mongoTemplate.save(migratedLayerDoc, LAYER_COLLECTION);
                    imported++;

                    // Get layerDataId for chunk import
                    String layerDataId = migratedLayerDoc.getString("layerDataId");

                    // Import chunks recursively
                    try (Stream<Path> paths = Files.walk(layerDir)) {
                        List<Path> chunkFiles = paths
                                .filter(p -> p.toString().endsWith(".json"))
                                .toList();

                        for (Path chunkFile : chunkFiles) {
                            try {
                                // Read JSON directly
                                String chunkJson = Files.readString(chunkFile);

                                // Parse to get chunk coordinates
                                Document chunkDoc = Document.parse(chunkJson);
                                Integer cx = chunkDoc.getInteger("cx");
                                Integer cz = chunkDoc.getInteger("cz");

                                if (cx == null || cz == null) {
                                    log.warn("Chunk file missing cx/cz coordinates: {}", chunkFile);
                                    continue;
                                }

                                String chunkKey = cx + ":" + cz;
                                chunkKeys.add(chunkKey);

                                // Migrate if needed
                                String migratedChunkJson = migrationService.migrateToLatest(chunkJson, "de.mhus.nimbus.world.shared.layer.LayerChunkData");

                                // Parse to LayerChunkData
                                LayerChunkData chunkData = objectMapper.readValue(migratedChunkJson, LayerChunkData.class);

                                layerService.saveTerrainChunk(
                                        worldId.getId(),
                                        layerDataId,
                                        chunkKey,
                                        chunkData
                                );

                                imported++;

                            } catch (Exception e) {
                                log.warn("Failed to import chunk from file: " + chunkFile, e);
                            }
                        }
                    }

                    log.debug("Imported ground layer: {}", layerName);

                } catch (Exception e) {
                    log.warn("Failed to import ground layer from: " + layerDir, e);
                }
            }
        }

        // Remove overtaken if requested
        int deleted = 0;
        if (removeOvertaken) {
            List<WLayer> dbLayers = layerService.findLayersByWorld(worldId.getId()).stream()
                    .filter(l -> l.getLayerType() == LayerType.GROUND)
                    .toList();

            for (WLayer layer : dbLayers) {
                if (!filesystemLayerNames.contains(layer.getName())) {
                    layerService.deleteLayer(worldId.getId(), layer.getName());
                    log.info("Deleted ground layer not in filesystem: {}", layer.getName());
                    deleted++;
                } else if (filesystemChunks.containsKey(layer.getName())) {
                    // Remove chunks
                    Set<String> fsChunks = filesystemChunks.get(layer.getName());
                    List<WLayerTerrain> dbChunks = terrainRepository.findByLayerDataId(layer.getLayerDataId());

                    for (WLayerTerrain chunk : dbChunks) {
                        if (!fsChunks.contains(chunk.getChunkKey())) {
                            terrainRepository.delete(chunk);
                            log.info("Deleted chunk not in filesystem: {}/{}", layer.getName(), chunk.getChunkKey());
                            deleted++;
                        }
                    }
                }
            }
        }

        return ResourceSyncType.ImportResult.of(imported, deleted);
    }

    /**
     * Export schema information from StorageData to _schema.yaml.
     * Reads schema and version from first chunk's StorageData.
     */
    private void exportSchemaInfo(String layerDataId, Path schemaFile) {
        try {
            // Get first terrain chunk to extract schema info
            List<WLayerTerrain> terrains = terrainRepository.findByLayerDataId(layerDataId);
            if (terrains.isEmpty()) {
                log.debug("No terrain chunks found for layer, skipping schema export");
                return;
            }

            WLayerTerrain firstTerrain = terrains.get(0);
            if (firstTerrain.getStorageId() == null) {
                log.warn("First terrain chunk has no storageId, skipping schema export");
                return;
            }

            // Load first StorageData chunk to get schema info
            StorageData firstChunk = storageDataRepository.findByUuidAndIndex(firstTerrain.getStorageId(), 0);
            if (firstChunk == null) {
                log.warn("No storage data found for storageId: {}", firstTerrain.getStorageId());
                return;
            }

            // Create schema document
            Map<String, String> schemaInfo = new HashMap<>();
            schemaInfo.put("schema", firstChunk.getSchema());
            schemaInfo.put("schemaVersion", firstChunk.getSchemaVersion());

            // Write to _schema.yaml
            yamlMapper.writeValue(schemaFile.toFile(), schemaInfo);
            log.debug("Exported schema info: schema={} version={}", firstChunk.getSchema(), firstChunk.getSchemaVersion());

        } catch (Exception e) {
            log.warn("Failed to export schema info", e);
        }
    }

    /**
     * Format JSON for better git diff.
     * Adds newlines after {, }, [, ], and , but no indentation/spaces.
     * Preserves content within string values (between quotes).
     *
     * Example:
     * Input:  {"cx":5,"cz":-3,"blocks":[{"block":{"position":{"x":160.0}}}]}
     * Output: {
     *         "cx":5,
     *         "cz":-3,
     *         "blocks":[
     *         {
     *         "block":{
     *         "position":{
     *         "x":160.0
     *         }
     *         }
     *         }
     *         ]
     *         }
     */
    private String formatJsonForGit(String json) {
        StringBuilder formatted = new StringBuilder();
        boolean inString = false;
        boolean escapeNext = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            // Handle escape sequences
            if (escapeNext) {
                formatted.append(c);
                escapeNext = false;
                continue;
            }

            if (c == '\\') {
                formatted.append(c);
                escapeNext = true;
                continue;
            }

            // Track if we're inside a string value
            if (c == '"') {
                inString = !inString;
                formatted.append(c);
                continue;
            }

            // If inside string, just append
            if (inString) {
                formatted.append(c);
                continue;
            }

            // Outside of strings: add newlines after special chars
            formatted.append(c);

            if (c == '{' || c == '[' || c == ',') {
                formatted.append('\n');
            } else if (c == '}' || c == ']') {
                formatted.append('\n');
            }
        }

        return formatted.toString();
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

