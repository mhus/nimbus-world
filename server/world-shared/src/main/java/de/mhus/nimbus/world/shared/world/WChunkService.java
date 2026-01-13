package de.mhus.nimbus.world.shared.world;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.generated.network.messages.ChunkDataTransferObject;
import de.mhus.nimbus.generated.types.Block;
import de.mhus.nimbus.generated.types.ChunkData;
import de.mhus.nimbus.generated.types.Vector3;
import de.mhus.nimbus.generated.types.Vector3Int;
import de.mhus.nimbus.shared.storage.StorageService;
import de.mhus.nimbus.shared.types.SchemaVersion;
import de.mhus.nimbus.shared.types.WorldId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Service für Verwaltung von Welt-Chunks (inline oder extern gespeicherter Datenblock).
 * Chunks exist separately for each world/zone.
 * Instances CANNOT have their own chunks - always taken from the defined world.
 * List loading does NOT fall back to main world.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WChunkService {

    public static final String STORAGE_SCHEMA = "WChunkStorage";
    public static final SchemaVersion STORAGE_SCHEMA_VERSION = SchemaVersion.create("1.0.1");

    private final WChunkRepository repository;
    private final StorageService storageService;
    private final WWorldService worldService;
    private final WItemPositionService itemRegistryService;

    @Value("${nimbus.chunk.compression.enabled:true}")
    private boolean compressionEnabled;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Find chunk by chunkKey.
     * Instances always look up in their world (without instance suffix).
     */
    @Transactional(readOnly = true)
    public Optional<WChunk> find(WorldId worldId, String chunkKey) {
        var lookupWorld = worldId.withoutInstance();
        return repository.findByWorldIdAndChunk(lookupWorld.getId(), chunkKey);
    }

    /**
     * Save chunk data.
     * Filters out instances - chunks are stored per world/zone (not per instance).
     */
    @Transactional
    public WChunk saveChunk(WorldId worldId, String chunkKey, ChunkData data) {
        if (blank(worldId.getId()) || blank(chunkKey)) {
            throw new IllegalArgumentException("worldId und chunkKey erforderlich");
        }
        if (data == null) throw new IllegalArgumentException("ChunkData erforderlich");

        var lookupWorld = worldId.withoutInstance();

        // Extract server metadata from blocks and store in separate map
        Map<String, Map<String, String>> infoServer = extractServerMetadata(data);

        // Felder 'status' und 'i' vor Serialisierung null setzen
        data.setStatus(null);
        data.setI(null);

        String json;
        try {
            json = objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            throw new IllegalStateException("Serialisierung ChunkData fehlgeschlagen", e);
        }

        WChunk entity = repository.findByWorldIdAndChunk(lookupWorld.getId(), chunkKey)
                .orElseGet(() -> {
                    WChunk neu = WChunk.builder().worldId(lookupWorld.getId()).chunk(chunkKey).build();
                    neu.touchCreate();
                    return neu;
                });

        // Komprimierung wenn aktiviert
        byte[] dataBytes;
        if (compressionEnabled) {
            try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                 GZIPOutputStream gzip = new GZIPOutputStream(buffer)) {
                gzip.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                gzip.finish();
                dataBytes = buffer.toByteArray();
                entity.setCompressed(true);
                log.debug("Chunk komprimiert chunkKey={} original={} compressed={} ratio={}",
                        chunkKey, json.length(), dataBytes.length,
                        String.format("%.1f%%", 100.0 * dataBytes.length / json.length()));
            } catch (Exception e) {
                throw new IllegalStateException("Komprimierung ChunkData fehlgeschlagen", e);
            }
        } else {
            dataBytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            entity.setCompressed(false);
        }

        // Alle Chunks werden jetzt extern über StorageService gespeichert
        try (InputStream stream = new ByteArrayInputStream(dataBytes)) {
            StorageService.StorageInfo storageInfo;
            if (entity.getStorageId() != null) {
                // Update existing chunk
                storageInfo = storageService.update(STORAGE_SCHEMA, STORAGE_SCHEMA_VERSION, entity.getStorageId(), stream);
            } else {
                // Create new chunk
                storageInfo = storageService.store(STORAGE_SCHEMA, STORAGE_SCHEMA_VERSION, lookupWorld.getId(), "chunk/" + chunkKey, stream);
            }
            entity.setStorageId(storageInfo.id());
            log.debug("Chunk extern gespeichert chunkKey={} size={} storageId={} world={} compressed={}",
                    chunkKey, storageInfo.size(), storageInfo.id(), lookupWorld.getId(), entity.isCompressed());
        } catch (Exception e) {
            throw new IllegalStateException("Speichern ChunkData fehlgeschlagen", e);
        }

        entity.setInfoServer(infoServer.isEmpty() ? null : infoServer);
        entity.touchUpdate();
        return repository.save(entity);
    }

    /**
     * Get chunk stream.
     * Filters out instances.
     */
    @Transactional(readOnly = true)
    public InputStream getStream(WorldId worldId, String chunkKey) {
        var lookupWorld = worldId.withoutInstance();

        WChunk chunk = repository.findByWorldIdAndChunk(lookupWorld.getId(), chunkKey).orElse(null);

        if (chunk == null || chunk.getStorageId() == null) {
            return new ByteArrayInputStream(new byte[0]);
        }

        // Alle Chunks sind jetzt extern gespeichert - Stream direkt vom StorageService zurückgeben
        InputStream stream = storageService.load(chunk.getStorageId());
        if (stream == null) {
            return new ByteArrayInputStream(new byte[0]);
        }

        // Dekomprimierung wenn nötig
        if (chunk.isCompressed()) {
            try {
                return new GZIPInputStream(stream);
            } catch (Exception e) {
                log.error("Fehler beim Dekomprimieren von Chunk chunkKey={} world={}", chunkKey, worldId.getId(), e);
                return new ByteArrayInputStream(new byte[0]);
            }
        }

        return stream;
    }

    /**
     * Get compressed stream without decompression.
     * Returns raw compressed data for client-side decompression (future use).
     * Filters out instances.
     */
    @Transactional(readOnly = true)
    public InputStream getCompressedStream(WorldId worldId, String chunkKey) {
        var lookupWorld = worldId.withoutInstance();

        WChunk chunk = repository.findByWorldIdAndChunk(lookupWorld.getId(), chunkKey).orElse(null);

        if (chunk == null || chunk.getStorageId() == null) {
            return new ByteArrayInputStream(new byte[0]);
        }

        // Return raw stream (no decompression)
        InputStream stream = storageService.load(chunk.getStorageId());
        return stream != null ? stream : new ByteArrayInputStream(new byte[0]);
    }

    /**
     * Streams chunk content directly to HTTP response without loading into memory.
     * Verhindert Memory-Probleme bei großen Chunks.
     * Filters out instances.
     */
    @Transactional(readOnly = true)
    public boolean streamToResponse(WorldId worldId, String chunkKey, jakarta.servlet.http.HttpServletResponse response) {
        var lookupWorld = worldId.withoutInstance();

        WChunk chunk = repository.findByWorldIdAndChunk(lookupWorld.getId(), chunkKey).orElse(null);

        if (chunk == null || chunk.getStorageId() == null) {
            return false;
        }

        try (InputStream inputStream = storageService.load(chunk.getStorageId())) {
            if (inputStream == null) {
                return false;
            }
            InputStream stream = inputStream;
            if (chunk.isCompressed()) {
                stream = new GZIPInputStream(inputStream);
            }

            // Set content type and headers
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");

            // Stream direkt zum Client ohne Memory-Belastung
            try (OutputStream outputStream = response.getOutputStream()) {
                stream.transferTo(outputStream);
                outputStream.flush();
            }

            log.debug("Chunk erfolgreich gestreamt chunkKey={} world={}", chunkKey, worldId.getId());
            return true;

        } catch (Exception e) {
            log.warn("Fehler beim Streamen des Chunks chunkKey={} world={}", chunkKey, worldId.getId(), e);
            return false;
        }
    }

    /**
     * Load chunk data.
     * Filters out instances.
     */
    @Transactional(readOnly = true)
    public Optional<ChunkData> loadChunkData(WorldId worldId, String chunkKey, boolean create) {
        var lookupWorld = worldId.withoutInstance();

        Optional<WChunk> chunkOpt = repository.findByWorldIdAndChunk(lookupWorld.getId(), chunkKey);

        if (chunkOpt.isPresent()) {
            // Chunk exists in database - load it
            WChunk entity = chunkOpt.get();
            if (entity.getStorageId() == null) {
                log.warn("Chunk ohne StorageId gefunden chunkKey={} world={}", chunkKey, worldId.getId());
                return Optional.empty();
            }

            // Alle Chunks sind jetzt extern gespeichert - Stream-basierte Deserialisierung
            try (InputStream inputStream = storageService.load(entity.getStorageId())) {
                if (inputStream == null) {
                    return Optional.empty();
                }
                InputStream stream = inputStream;
                if (entity.isCompressed()) {
                    stream = new GZIPInputStream(inputStream);
                }

                // Direkte Deserialisierung vom Stream ohne Memory-Verschwendung
                ChunkData chunkData = objectMapper.readValue(stream, ChunkData.class);
                return Optional.ofNullable(chunkData);

            } catch (Exception e) {
                log.warn("ChunkData Deserialisierung fehlgeschlagen chunkKey={} world={}", chunkKey, worldId.getId(), e);
                return Optional.empty();
            }
        } else if (create) {
            // Chunk not found - generate default chunk based on world settings
            log.debug("Chunk not found in DB, generating default: chunkKey={} world={}", chunkKey, lookupWorld.getId());
            return Optional.ofNullable(generateDefaultChunk(lookupWorld.getId(), chunkKey));
        } else {
            // Chunk not found and create=false - return empty
            return Optional.empty();
        }
    }

    /**
     * Generate default chunk based on world configuration.
     * Creates ground blocks up to groundLevel and water blocks up to waterLevel.
     */
    private ChunkData generateDefaultChunk(String worldId, String chunkKey) {
        try {
            // Parse chunk coordinates from key (format: "cx:cz")
            String[] parts = chunkKey.split(":");
            if (parts.length != 2) {
                log.warn("Invalid chunk key format: {}", chunkKey);
                return null;
            }

            int cx = Integer.parseInt(parts[0]);
            int cz = Integer.parseInt(parts[1]);

            // Load world configuration
            WWorld world = worldService.getByWorldId(worldId).orElse(null);
            if (world == null) {
                log.warn("World not found for default chunk generation: {}", worldId);
                return null;
            }

            int groundLevel = world.getGroundLevel();
            Integer waterLevel = world.getWaterLevel();
            String groundBlockType = world.getGroundBlockType();
            String waterBlockType = world.getWaterBlockType();

            var chunkSize = world.getPublicData().getChunkSize();
            // Create chunk data
            ChunkData chunkData = new ChunkData();
            chunkData.setCx(cx);
            chunkData.setCz(cz);
            chunkData.setSize((byte)chunkSize);

            List<Block> blocks = new ArrayList<>();

            // Generate blocks for the chunk (32x32 xz area)
            for (int localX = 0; localX < chunkSize; localX++) {
                for (int localZ = 0; localZ < chunkSize; localZ++) {
                    int worldX = cx * chunkSize + localX;
                    int worldZ = cz * chunkSize + localZ;

                    // Create ground block at groundLevel
                    if (groundLevel >= 0 && groundBlockType != null) {
                        Block groundBlock = createBlock(worldX, groundLevel, worldZ, groundBlockType);
                        blocks.add(groundBlock);
                    }

                    // Create water blocks from groundLevel+1 to waterLevel
                    if (waterLevel != null && waterLevel > groundLevel && waterBlockType != null) {
                        for (int y = groundLevel + 1; y <= waterLevel; y++) {
                            Block waterBlock = createBlock(worldX, y, worldZ, waterBlockType);
                            blocks.add(waterBlock);
                        }
                    }
                }
            }

            chunkData.setBlocks(blocks);

            log.debug("Generated default chunk: cx={}, cz={}, blocks={}, groundLevel={}, waterLevel={}",
                    cx, cz, blocks.size(), groundLevel, waterLevel);

            return chunkData;

        } catch (Exception e) {
            log.error("Failed to generate default chunk: chunkKey={}", chunkKey, e);
            return null;
        }
    }

    /**
     * Create a simple block at the given position.
     */
    private Block createBlock(int x, int y, int z, String blockTypeId) {
        Block block = new Block();

        Vector3Int position = new Vector3Int();
        position.setX(x);
        position.setY(y);
        position.setZ(z);
        block.setPosition(position);

        block.setBlockTypeId(blockTypeId);

        return block;
    }

    /**
     * Delete chunk.
     * Filters out instances.
     */
    @Transactional
    public boolean delete(WorldId worldId, String chunkKey) {
        var lookupWorld = worldId.withoutInstance();
        return repository.findByWorldIdAndChunk(lookupWorld.getId(), chunkKey).map(c -> {
            if (c.getStorageId() != null) {
                safeDeleteExternal(storageService, c.getStorageId());
            }
            repository.delete(c);
            log.debug("Chunk gelöscht chunkKey={} world={}", chunkKey, lookupWorld.getId());
            return true;
        }).orElse(false);
    }

    private void safeDeleteExternal(StorageService storage, String storageId) {
        try { storage.delete(storageId); } catch (Exception e) { log.warn("Externer Chunk-Speicher konnte nicht gelöscht werden id={}", storageId, e); }
    }

    /**
     * Convert WChunk to ChunkDataTransferObject for network transmission.
     * If chunk is compressed in storage, directly uses compressed storage data.
     * If not compressed, loads and converts ChunkData normally.
     *
     * @param worldId World identifier
     * @param chunk WChunk entity
     * @return Transfer object optimized for network transmission
     */
    public ChunkDataTransferObject toTransferObject(WorldId worldId, WChunk chunk) {
        if (chunk == null) return null;

        String chunkKey = chunk.getChunk();
        int cx = Integer.parseInt(chunkKey.split(":")[0]);
        int cz = Integer.parseInt(chunkKey.split(":")[1]);

        // Load items for this chunk from registry
        var items = itemRegistryService.getItemsInChunk(worldId, cx, cz);

        // If chunk is compressed in storage, use storage data directly
        if (chunk.isCompressed() && chunk.getStorageId() != null) {
            try {
                // Load compressed storage data directly (no decompression!)
                InputStream compressedStream = storageService.load(chunk.getStorageId());
                if (compressedStream == null) {
                    log.warn("Compressed chunk has no storage data: chunkKey={}", chunkKey);
                    return null;
                }

                // Read all bytes from stream (already compressed ChunkData)
                byte[] compressedData = compressedStream.readAllBytes();

                log.debug("Using compressed storage data directly: chunkKey={} size={} bytes",
                        chunkKey, compressedData.length);

                // Return with compressed ChunkData as-is
                return ChunkDataTransferObject.builder()
                        .cx(cx)
                        .cz(cz)
                        .i(items.isEmpty() ? null : items)  // items not compressed
                        .c(compressedData)  // compressed ChunkData from storage (as-is)
                        .build();

            } catch (Exception e) {
                log.error("Failed to load compressed storage data: chunkKey={}", chunkKey, e);
                // Fall through to normal loading
            }
        }

        // Uncompressed: Load ChunkData and convert normally
        Optional<ChunkData> chunkDataOpt = loadChunkData(worldId, chunkKey, false);
        if (chunkDataOpt.isEmpty()) {
            log.warn("Chunk data not found: chunkKey={}", chunkKey);
            return null;
        }

        ChunkData chunkData = chunkDataOpt.get();

        return ChunkDataTransferObject.builder()
                .cx(chunkData.getCx())
                .cz(chunkData.getCz())
                .b(chunkData.getBlocks())        // blocks → b
                .i(items.isEmpty() ? null : items)  // items from registry → i
                .h(chunkData.getHeightData())
                .backdrop(convertBackdrop(chunkData.getBackdrop()))
                // Note: AreaData (a) currently not in ChunkData
                .build();
    }

    /**
     * Convert ChunkDataBackdropDTO to ChunkDataTransferObjectBackdropDTO.
     * Just directly assigns the lists since both DTOs are structurally identical.
     * TODO: These DTOs are structurally identical - consider unifying them in the generator
     */
    public de.mhus.nimbus.generated.network.messages.ChunkDataTransferObjectBackdropDTO convertBackdrop(
            de.mhus.nimbus.generated.types.ChunkDataBackdropDTO source) {
        if (source == null) return null;

        var target = new de.mhus.nimbus.generated.network.messages.ChunkDataTransferObjectBackdropDTO();
        target.setN(source.getN());
        target.setE(source.getE());
        target.setS(source.getS());
        target.setW(source.getW());
        return target;
    }

    /**
     * Get height data for a specific column (x, z) in chunk data.
     * Searches through the heightData array for the matching x,z coordinates
     * and converts the int[] to HeightDataDto.
     *
     * @param chunkData Chunk data containing heightData
     * @param x Local x coordinate within chunk (0 to chunkSize-1)
     * @param z Local z coordinate within chunk (0 to chunkSize-1)
     * @return HeightDataDto if found, null otherwise
     */
    public de.mhus.nimbus.world.shared.dto.HeightDataDto getHeightDataForColumn(ChunkData chunkData, int x, int z) {
        if (chunkData == null || chunkData.getHeightData() == null) {
            return null;
        }

        // Search for matching column in heightData array
        for (int[] columnData : chunkData.getHeightData()) {
            if (columnData.length >= 4 && columnData[0] == x && columnData[1] == z) {
                // Found matching column
                // Format: [x, z, maxHeight, groundLevel, waterLevel?]
                int maxHeight = columnData[2];
                int groundLevel = columnData[3];
                Integer waterLevel = columnData.length > 4 ? columnData[4] : null;

                return new de.mhus.nimbus.world.shared.dto.HeightDataDto(
                        x, z, maxHeight, groundLevel, waterLevel
                );
            }
        }

        return null;
    }

    /**
     * Extract server metadata from blocks and remove it from block metadata.
     * Server metadata is stored separately in WChunk.infoServer to prevent sending it to clients.
     *
     * @param chunkData Chunk data containing blocks
     * @return Map of block coordinates to server metadata
     */
    private Map<String, Map<String, String>> extractServerMetadata(ChunkData chunkData) {
        Map<String, Map<String, String>> infoServer = new HashMap<>();

        if (chunkData.getBlocks() == null) {
            return infoServer;
        }

        for (Block block : chunkData.getBlocks()) {
            if (block.getMetadata() == null || block.getMetadata().getServer() == null) {
                continue;
            }

            // Extract position
            Vector3Int position = block.getPosition();
            if (position == null) {
                continue;
            }

            String coordinate = position.getX() + "," + position.getY() + "," + position.getZ();

            // Store server metadata
            Map<String, String> serverData = block.getMetadata().getServer();
            if (serverData != null && !serverData.isEmpty()) {
                infoServer.put(coordinate, new HashMap<>(serverData));
            }

            // Remove server metadata from block
            block.getMetadata().setServer(null);

            // Check if metadata is now empty (all fields null)
            if (isMetadataEmpty(block.getMetadata())) {
                block.setMetadata(null);
            }
        }

        return infoServer;
    }

    /**
     * Check if all fields in BlockMetadata are null.
     *
     * @param metadata BlockMetadata to check
     * @return true if all fields are null
     */
    private boolean isMetadataEmpty(de.mhus.nimbus.generated.types.BlockMetadata metadata) {
        if (metadata == null) {
            return true;
        }

        return metadata.getId() == null
                && metadata.getTitle() == null
                && metadata.getGroupId() == null
                && metadata.getServer() == null
                && metadata.getClient() == null;
    }

    /**
     * Get server metadata for a specific block position.
     * Loads world, calculates chunk coordinates, loads chunk, and returns server info.
     *
     * @param worldId World identifier
     * @param x Block x coordinate (world coordinates)
     * @param y Block y coordinate (world coordinates)
     * @param z Block z coordinate (world coordinates)
     * @return Server metadata map for the block, or null if not found
     */
    public Map<String, String> getServerInfo(WorldId worldId, int x, int y, int z) {
        // Load world to get chunkSize
        Optional<WWorld> worldOpt = worldService.getByWorldId(worldId.withoutInstance().getId());
        if (worldOpt.isEmpty()) {
            log.warn("World not found for server info lookup: worldId={}", worldId);
            return null;
        }

        WWorld world = worldOpt.get();
        String chunkKey = world.getChunkKey(x,z);

        log.trace("Looking up server info for block: worldId={}, pos=({},{},{}), chunk={}",
                worldId, x, y, z, chunkKey);

        // Load chunk
        Optional<WChunk> chunkOpt = find(worldId, chunkKey);
        if (chunkOpt.isEmpty()) {
            log.trace("Chunk not found for server info lookup: worldId={}, chunkKey={}",
                    worldId, chunkKey);
            return null;
        }

        // Get server info for block
        WChunk chunk = chunkOpt.get();
        return chunk.getServerInfoForBlock(x, y, z);
    }

    private boolean blank(String s) { return s == null || s.isBlank(); }
}
