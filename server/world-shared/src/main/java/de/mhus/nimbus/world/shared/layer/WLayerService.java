package de.mhus.nimbus.world.shared.layer;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.shared.storage.StorageService;
import de.mhus.nimbus.shared.types.SchemaVersion;
import de.mhus.nimbus.world.shared.world.WWorldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Service for layer management (CRUD operations).
 *
 * Layers exist separately for each world/zone.
 * Instances CANNOT have their own layers - always taken from the defined world.
 * Branches can have copied layers (TODO: not yet implemented).
 * - Layers must be copied first before they can be modified in a branch.
 * - When generating chunks in branches, check for copied layers.
 * List loading does NOT fall back to main world.
 *
 * IMPORTANT: This service uses String worldId parameters.
 * Callers must ensure instances are filtered out before calling (use worldId.withoutInstance()).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WLayerService {

    public static final String STORAGE_SCHEMA = "WLayerTerrainStorage";
    public static final SchemaVersion STORAGE_SCHEMA_VERSION = SchemaVersion.create("1.0.1");

    private final WLayerRepository layerRepository;
    private final WLayerTerrainRepository terrainRepository;
    private final WLayerModelRepository modelRepository;
    private final WDirtyChunkService dirtyChunkService;
    private final StorageService storageService;
    private final ObjectMapper objectMapper;
    private final WWorldService worldService;

    @Value("${nimbus.layer.terrain.compression.enabled:true}")
    private boolean compressionEnabled;

    // ==================== LAYER CRUD ====================

    /**
     * Create a new layer.
     *
     * NOTE: worldId should NOT include instance suffix (use worldId.withoutInstance()).
     *
     * @param worldId         World identifier (without instance)
     * @param name            Layer name (unique per world)
     * @param layerType       Layer type (GROUND or MODEL)
     * @param order           Overlay order
     * @param allChunks       True if affects all chunks
     * @param affectedChunks  List of affected chunks (if allChunks is false)
     * @param baseGround      True if this is the base ground layer
     * @return Created layer
     */
    @Transactional
    public WLayer createLayer(String worldId, String name, LayerType layerType,
                              int order, boolean allChunks, List<String> affectedChunks, boolean baseGround) {
        // Validate
        if (worldId == null || worldId.isBlank()) {
            throw new IllegalArgumentException("worldId is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (layerType == null) {
            throw new IllegalArgumentException("layerType is required");
        }

        // Check for duplicate name
        if (layerRepository.findByWorldIdAndName(worldId, name).isPresent()) {
            throw new IllegalArgumentException("Layer with name '" + name + "' already exists in world " + worldId);
        }

        // Generate layerDataId
        String layerDataId = UUID.randomUUID().toString();

        // Create layer
        WLayer layer = WLayer.builder()
                .worldId(worldId)
                .name(name)
                .layerType(layerType)
                .layerDataId(layerDataId)
                .order(order)
                .allChunks(allChunks)
                .affectedChunks(affectedChunks != null ? affectedChunks : List.of())
                .enabled(true)
                .baseGround(baseGround)
                .build();
        layer.touchCreate();

        WLayer saved = layerRepository.save(layer);
        log.info("Created layer: world={} name={} type={} order={}", worldId, name, layerType, order);

        return saved;
    }

    /**
     * Update a layer using a Consumer.
     *
     * @param worldId   World identifier
     * @param layerName Layer name
     * @param updater   Consumer to modify the layer
     * @return Updated layer
     */
    @Transactional
    public Optional<WLayer> updateLayer(String worldId, String layerName, Consumer<WLayer> updater) {
        Optional<WLayer> layerOpt = layerRepository.findByWorldIdAndName(worldId, layerName);
        if (layerOpt.isEmpty()) {
            return Optional.empty();
        }

        WLayer layer = layerOpt.get();
        updater.accept(layer);
        layer.touchUpdate();

        WLayer saved = layerRepository.save(layer);
        log.info("Updated layer: world={} name={}", worldId, layerName);

        // Mark affected chunks as dirty
        markAffectedChunksDirty(saved, "layer_updated");

        return Optional.of(saved);
    }

    /**
     * Delete a layer and its associated data.
     *
     * @param worldId   World identifier
     * @param layerName Layer name
     * @return True if deleted
     */
    @Transactional
    public boolean deleteLayer(String worldId, String layerName) {
        Optional<WLayer> layerOpt = layerRepository.findByWorldIdAndName(worldId, layerName);
        if (layerOpt.isEmpty()) {
            return false;
        }

        WLayer layer = layerOpt.get();

        // Mark affected chunks as dirty before deletion
        markAffectedChunksDirty(layer, "layer_deleted");

        // Delete associated data
        if (layer.getLayerType() == LayerType.GROUND) {
            deleteTerrainData(layer.getLayerDataId());
        } else if (layer.getLayerType() == LayerType.MODEL) {
            modelRepository.deleteByLayerDataId(layer.getLayerDataId());
        }

        // Delete layer
        layerRepository.delete(layer);
        log.info("Deleted layer: world={} name={} type={}", worldId, layerName, layer.getLayerType());

        return true;
    }

    /**
     * Find a layer by ID.
     */
    @Transactional(readOnly = true)
    public Optional<WLayer> findById(String id) {
        return layerRepository.findById(id);
    }

    /**
     * Find a layer by world and name.
     */
    @Transactional(readOnly = true)
    public Optional<WLayer> findLayer(String worldId, String layerName) {
        return layerRepository.findByWorldIdAndName(worldId, layerName);
    }

    /**
     * Find a layer by world and name (alias for REST API).
     */
    @Transactional(readOnly = true)
    public Optional<WLayer> findByWorldIdAndName(String worldId, String layerName) {
        return layerRepository.findByWorldIdAndName(worldId, layerName);
    }

    /**
     * Find a layer by world ID and layer data ID.
     * This is the preferred method as only worldId + layerDataId is guaranteed unique.
     */
    @Transactional(readOnly = true)
    public Optional<WLayer> findByWorldIdAndLayerDataId(String worldId, String layerDataId) {
        return layerRepository.findByWorldIdAndLayerDataId(worldId, layerDataId);
    }

    /**
     * Find all layers for a world.
     * No fallback to parent world - returns only layers in this specific world.
     *
     * NOTE: worldId should NOT include instance suffix (use worldId.withoutInstance()).
     */
    @Transactional(readOnly = true)
    public List<WLayer> findLayersByWorld(String worldId) {
        return layerRepository.findByWorldIdOrderByOrderAsc(worldId);
    }

    /**
     * Find all layers for a world (alias for REST API).
     * No fallback to parent world - returns only layers in this specific world.
     *
     * NOTE: worldId should NOT include instance suffix (use worldId.withoutInstance()).
     */
    @Transactional(readOnly = true)
    public List<WLayer> findByWorldId(String worldId) {
        return layerRepository.findByWorldIdOrderByOrderAsc(worldId);
    }

    /**
     * Find all layers for a world with optional query filter.
     * No fallback to parent world - returns only layers in this specific world.
     *
     * NOTE: worldId should NOT include instance suffix (use worldId.withoutInstance()).
     */
    @Transactional(readOnly = true)
    public List<WLayer> findByWorldIdAndQuery(String worldId, String query) {
        List<WLayer> all = layerRepository.findByWorldIdOrderByOrderAsc(worldId);

        // Apply search filter if provided
        if (query != null && !query.isBlank()) {
            all = filterByQuery(all, query);
        }

        return all;
    }

    private List<WLayer> filterByQuery(List<WLayer> layers, String query) {
        String lowerQuery = query.toLowerCase();
        return layers.stream()
                .filter(layer -> {
                    String name = layer.getName();
                    String id = layer.getId();
                    return (name != null && name.toLowerCase().contains(lowerQuery)) ||
                            (id != null && id.toLowerCase().contains(lowerQuery));
                })
                .collect(Collectors.toList());
    }

    /**
     * Save a layer.
     */
    @Transactional
    public WLayer save(WLayer layer) {
        return layerRepository.save(layer);
    }

    /**
     * Delete a layer by ID.
     */
    @Transactional
    public void delete(String id) {
        Optional<WLayer> layerOpt = layerRepository.findById(id);
        if (layerOpt.isPresent()) {
            WLayer layer = layerOpt.get();
            // Mark affected chunks as dirty before deletion
            markAffectedChunksDirty(layer, "layer_deleted");

            // Delete associated data
            if (layer.getLayerType() == LayerType.GROUND) {
                deleteTerrainData(layer.getLayerDataId());
            } else if (layer.getLayerType() == LayerType.MODEL) {
                modelRepository.deleteByLayerDataId(layer.getLayerDataId());
            }

            // Delete layer
            layerRepository.deleteById(id);
            log.info("Deleted layer: id={} name={} type={}", id, layer.getName(), layer.getLayerType());
        }
    }

    /**
     * Get layers affecting a specific chunk (sorted by order).
     *
     * TODO: For branches, also check for copied layers.
     *
     * NOTE: worldId should NOT include instance suffix (use worldId.withoutInstance()).
     *
     * @param worldId  World identifier (without instance)
     * @param chunkKey Chunk key
     * @return List of layers sorted by order
     */
    @Transactional(readOnly = true)
    public List<WLayer> getLayersAffectingChunk(String worldId, String chunkKey) {
        return layerRepository.findLayersAffectingChunk(worldId, chunkKey)
                .stream()
                .sorted(Comparator.comparingInt(WLayer::getOrder))
                .collect(Collectors.toList());
    }

    // ==================== TERRAIN LAYER OPERATIONS ====================

    /**
     * Save terrain chunk data.
     *
     * @param worldId     World identifier
     * @param layerDataId Layer data ID
     * @param chunkKey    Chunk key
     * @param data        Layer chunk data
     * @return Saved terrain entity
     */
    @Transactional
    public WLayerTerrain saveTerrainChunk(String worldId, String layerDataId,
                                          String chunkKey, LayerChunkData data) {
        if (data == null) {
            throw new IllegalArgumentException("LayerChunkData is required");
        }

        // Serialize to JSON
        String json;
        try {
            json = objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize ChunkData", e);
        }

        // Find or create entity
        WLayerTerrain entity = terrainRepository
                .findByLayerDataIdAndChunkKey(layerDataId, chunkKey)
                .orElseGet(() -> {
                    WLayerTerrain newEntity = WLayerTerrain.builder()
                            .worldId(worldId)
                            .layerDataId(layerDataId)
                            .chunkKey(chunkKey)
                            .build();
                    newEntity.touchCreate();
                    return newEntity;
                });

        // Compression if enabled
        byte[] dataBytes;
        if (compressionEnabled) {
            try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                 GZIPOutputStream gzip = new GZIPOutputStream(buffer)) {
                gzip.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                gzip.finish();
                dataBytes = buffer.toByteArray();
                entity.setCompressed(true);
                log.debug("Layer terrain chunk compressed: layerDataId={} chunkKey={} original={} compressed={} ratio={}",
                        layerDataId, chunkKey, json.length(), dataBytes.length,
                        String.format("%.1f%%", 100.0 * dataBytes.length / json.length()));
            } catch (Exception e) {
                throw new IllegalStateException("Failed to compress layer terrain chunk", e);
            }
        } else {
            dataBytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            entity.setCompressed(false);
        }

        // Store via StorageService
        try (InputStream stream = new ByteArrayInputStream(dataBytes)) {
            StorageService.StorageInfo storageInfo;
            if (entity.getStorageId() != null) {
                storageInfo = storageService.update(STORAGE_SCHEMA, STORAGE_SCHEMA_VERSION, entity.getStorageId(), stream);
            } else {
                storageInfo = storageService.store(STORAGE_SCHEMA, STORAGE_SCHEMA_VERSION, worldId, "layer/terrain/" + layerDataId + "/" + chunkKey, stream);
            }
            entity.setStorageId(storageInfo.id());
            log.debug("Terrain chunk stored: layerDataId={} chunkKey={} storageId={} size={} compressed={}",
                    layerDataId, chunkKey, storageInfo.id(), storageInfo.size(), entity.isCompressed());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to store terrain chunk", e);
        }

        entity.touchUpdate();
        WLayerTerrain saved = terrainRepository.save(entity);

        // Mark chunk as dirty
        dirtyChunkService.markChunkDirty(worldId, chunkKey, "terrain_layer_updated");

        return saved;
    }

    /**
     * Load terrain chunk data.
     *
     * @param layerDataId Layer data ID
     * @param chunkKey    Chunk key
     * @return Layer chunk data if found
     */
    @Transactional(readOnly = true)
    public Optional<LayerChunkData> loadTerrainChunk(String layerDataId, String chunkKey) {
        Optional<WLayerTerrain> terrainOpt = terrainRepository
                .findByLayerDataIdAndChunkKey(layerDataId, chunkKey);

        if (terrainOpt.isEmpty()) {
            return Optional.empty();
        }

        WLayerTerrain terrain = terrainOpt.get();
        if (terrain.getStorageId() == null) {
            log.warn("Terrain chunk has no storageId: layerDataId={} chunkKey={}", layerDataId, chunkKey);
            return Optional.empty();
        }

        // Load from storage
        try (InputStream inputStream = storageService.load(terrain.getStorageId())) {
            if (inputStream == null) {
                return Optional.empty();
            }
            InputStream stream = inputStream;
            // Decompression if needed
            // Note: If compressed field is not set in DB (legacy data), it defaults to false (uncompressed)
            if (terrain.isCompressed()) {
                stream = new GZIPInputStream(inputStream);
            }
            LayerChunkData chunkData = objectMapper.readValue(stream, LayerChunkData.class);
            return Optional.of(chunkData);
        } catch (Exception e) {
            log.error("Failed to load terrain chunk: layerDataId={} chunkKey={}", layerDataId, chunkKey, e);
            return Optional.empty();
        }
    }

    /**
     * Delete terrain chunk.
     */
    @Transactional
    public boolean deleteTerrainChunk(String layerDataId, String chunkKey) {
        Optional<WLayerTerrain> terrainOpt = terrainRepository
                .findByLayerDataIdAndChunkKey(layerDataId, chunkKey);

        if (terrainOpt.isEmpty()) {
            return false;
        }

        WLayerTerrain terrain = terrainOpt.get();

        // Delete from storage
        if (terrain.getStorageId() != null) {
            try {
                storageService.delete(terrain.getStorageId());
            } catch (Exception e) {
                log.warn("Failed to delete terrain chunk from storage: storageId={}", terrain.getStorageId(), e);
            }
        }

        // Delete entity
        terrainRepository.delete(terrain);

        // Mark chunk as dirty
        dirtyChunkService.markChunkDirty(terrain.getWorldId(), chunkKey, "terrain_chunk_deleted");

        log.debug("Deleted terrain chunk: layerDataId={} chunkKey={}", layerDataId, chunkKey);
        return true;
    }

    /**
     * Delete all terrain data for a layer.
     */
    private void deleteTerrainData(String layerDataId) {
        List<WLayerTerrain> terrains = terrainRepository.findByLayerDataId(layerDataId);
        for (WLayerTerrain terrain : terrains) {
            if (terrain.getStorageId() != null) {
                try {
                    storageService.delete(terrain.getStorageId());
                } catch (Exception e) {
                    log.warn("Failed to delete terrain storage: storageId={}", terrain.getStorageId(), e);
                }
            }
        }
        terrainRepository.deleteByLayerDataId(layerDataId);
        log.debug("Deleted terrain data: layerDataId={} count={}", layerDataId, terrains.size());
    }

    // ==================== TERRAIN GENERATION FROM MODEL ====================

    /**
     * Recreate a complete MODEL-based layer from all WLayerModel documents.
     * This method:
     * 1. Deletes all existing WLayerTerrain chunks for this layer
     * 2. Recalculates affected chunks from all WLayerModel documents
     * 3. Regenerates WLayerTerrain for each affected chunk
     * 4. Updates WLayer.affectedChunks completely
     * 5. Optionally marks chunks as dirty
     *
     * IMPORTANT: This does NOT happen automatically during DirtyChunk processing.
     * This must be called manually when a model-based layer needs complete regeneration.
     *
     * @param layerDataId       Layer data ID to recreate
     * @param markChunksDirty   If true, marks affected chunks as dirty after recreation
     * @return Number of chunks recreated, or -1 if layer not found
     */
    @Transactional
    public int recreateModelBasedLayer(String layerDataId, boolean markChunksDirty) {
        // Get the layer by layerDataId
        Optional<WLayer> layerOpt = layerRepository.findByLayerDataId(layerDataId);
        if (layerOpt.isEmpty()) {
            log.warn("Layer not found for recreation: layerDataId={}", layerDataId);
            return -1;
        }

        WLayer layer = layerOpt.get();

        // Verify it's a MODEL layer
        if (layer.getLayerType() != LayerType.MODEL) {
            log.warn("Cannot recreate non-MODEL layer: layerDataId={} type={}", layerDataId, layer.getLayerType());
            return -1;
        }

        log.info("Starting recreation of MODEL-based layer: layerDataId={} name={}", layerDataId, layer.getName());

        // Step 1: Delete all existing WLayerTerrain chunks for this layer
        deleteTerrainData(layerDataId);
        log.debug("Deleted existing terrain data for layer: layerDataId={}", layerDataId);

        // Step 2: Load all WLayerModel for this layer (sorted by order)
        List<WLayerModel> models = modelRepository.findByLayerDataIdOrderByOrder(layerDataId);

        if (models.isEmpty()) {
            log.info("No models found for layer recreation: layerDataId={}", layerDataId);
            // Clear affected chunks since there are no models
            layer.setAffectedChunks(new ArrayList<>());
            layer.touchUpdate();
            layerRepository.save(layer);
            return 0;
        }

        log.debug("Found {} models for layer recreation: layerDataId={}", models.size(), layerDataId);

        // Step 3: Calculate all affected chunks from all models
        Set<String> allAffectedChunks = new HashSet<>();

        for (WLayerModel model : models) {
            Set<String> modelChunks = calculateAffectedChunks(model);
            allAffectedChunks.addAll(modelChunks);
            log.trace("Model {} affects {} chunks", model.getName(), modelChunks.size());
        }

        if (allAffectedChunks.isEmpty()) {
            log.info("No affected chunks calculated for layer: layerDataId={}", layerDataId);
            // Clear affected chunks
            layer.setAffectedChunks(new ArrayList<>());
            layer.touchUpdate();
            layerRepository.save(layer);
            return 0;
        }

        log.debug("Total affected chunks: {}", allAffectedChunks.size());

        // Step 4: Update WLayer.affectedChunks completely (replace, not merge)
        layer.setAffectedChunks(new ArrayList<>(allAffectedChunks));
        layer.touchUpdate();
        layerRepository.save(layer);
        log.debug("Updated layer affected chunks: layer={} count={}", layer.getName(), allAffectedChunks.size());

        // Step 5: Recreate terrain for each affected chunk
        int chunksProcessed = 0;
        for (String chunkKey : allAffectedChunks) {
            try {
                recreateTerrainChunk(layer.getWorldId(), models, layerDataId, chunkKey);
                chunksProcessed++;
            } catch (Exception e) {
                log.error("Failed to recreate terrain chunk: layerDataId={} chunkKey={}", layerDataId, chunkKey, e);
            }
        }

        log.info("Recreated MODEL-based layer: layerDataId={} name={} chunks={} models={}",
                layerDataId, layer.getName(), chunksProcessed, models.size());

        // Step 6: Mark chunks as dirty if requested
        if (markChunksDirty && chunksProcessed > 0) {
            dirtyChunkService.markChunksDirty(layer.getWorldId(), new ArrayList<>(allAffectedChunks), "model_layer_recreated");
        }

        return chunksProcessed;
    }

    /**
     * Recreate WLayerTerrain for specific WLayerModels of a MODEL-based layer.
     * Used after merging cached changes into WLayerModel to update the terrain representation.
     *
     * This method writes the complete affected models into WLayerTerrain:
     * - Calculates all chunks affected by the given models
     * - Deletes existing terrain data for those chunks
     * - Regenerates terrain from ALL models in the layer (respecting order)
     * - Marks chunks as dirty for client update
     *
     * @param layerDataId Layer data ID
     * @param affectedModelIds Set of WLayerModel IDs that were modified
     * @param markChunksDirty Whether to mark chunks as dirty after regeneration
     * @return Number of chunks successfully regenerated
     */
    @Transactional
    public int recreateTerrainForModels(String layerDataId, Set<String> affectedModelIds, boolean markChunksDirty) {
        if (affectedModelIds == null || affectedModelIds.isEmpty()) {
            log.debug("No models to regenerate terrain for: layerDataId={}", layerDataId);
            return 0;
        }

        // Get the layer by layerDataId
        Optional<WLayer> layerOpt = layerRepository.findByLayerDataId(layerDataId);
        if (layerOpt.isEmpty()) {
            log.warn("Layer not found for terrain regeneration: layerDataId={}", layerDataId);
            return 0;
        }

        WLayer layer = layerOpt.get();

        // Verify it's a MODEL layer
        if (layer.getLayerType() != LayerType.MODEL) {
            log.warn("Cannot regenerate terrain for non-MODEL layer: layerDataId={} type={}",
                    layerDataId, layer.getLayerType());
            return 0;
        }

        log.info("Starting terrain regeneration for MODEL layer: layerDataId={} affectedModels={}",
                layerDataId, affectedModelIds.size());

        // Load all WLayerModel for this layer (sorted by order)
        List<WLayerModel> allModels = modelRepository.findByLayerDataIdOrderByOrder(layerDataId);

        if (allModels.isEmpty()) {
            log.info("No models found for terrain regeneration: layerDataId={}", layerDataId);
            return 0;
        }

        // Calculate all chunks affected by the modified models
        Set<String> affectedChunks = new HashSet<>();
        for (WLayerModel model : allModels) {
            if (affectedModelIds.contains(model.getId())) {
                Set<String> modelChunks = calculateAffectedChunks(model);
                affectedChunks.addAll(modelChunks);
                log.debug("Model {} affects {} chunks", model.getName(), modelChunks.size());
            }
        }

        if (affectedChunks.isEmpty()) {
            log.info("No affected chunks calculated for terrain regeneration: layerDataId={}", layerDataId);
            return 0;
        }

        log.info("Total affected chunks for terrain regeneration: {} (from {} models)",
                affectedChunks.size(), affectedModelIds.size());

        // Delete existing terrain data for affected chunks
        for (String chunkKey : affectedChunks) {
            try {
                deleteTerrainChunk(layerDataId, chunkKey);
            } catch (Exception e) {
                log.warn("Failed to delete terrain chunk before regeneration: layerDataId={} chunkKey={}",
                        layerDataId, chunkKey, e);
            }
        }

        // Recreate terrain for each affected chunk (using ALL models, respecting order)
        int chunksProcessed = 0;
        for (String chunkKey : affectedChunks) {
            try {
                recreateTerrainChunk(layer.getWorldId(), allModels, layerDataId, chunkKey);
                chunksProcessed++;
            } catch (Exception e) {
                log.error("Failed to recreate terrain chunk: layerDataId={} chunkKey={}",
                        layerDataId, chunkKey, e);
            }
        }

        log.info("Regenerated terrain for MODEL layer: layerDataId={} chunks={}/{}",
                layerDataId, chunksProcessed, affectedChunks.size());

        // Mark chunks as dirty if requested
        if (markChunksDirty && chunksProcessed > 0) {
            dirtyChunkService.markChunksDirty(layer.getWorldId(),
                    new ArrayList<>(affectedChunks), "model_terrain_regenerated");
        }

        return chunksProcessed;
    }

    /**
     * Recreate a single terrain chunk from all models.
     * Models are already sorted by order.
     */
    private void recreateTerrainChunk(String worldId, List<WLayerModel> models, String layerDataId, String chunkKey) {
        // Parse chunk coordinates
        String[] parts = chunkKey.split(":");
        if (parts.length != 2) {
            log.warn("Invalid chunk key format: {}", chunkKey);
            return;
        }
        var world = worldService.getByWorldId(worldId).orElseThrow(
                () -> new IllegalArgumentException("World not found: " + worldId)
        );
        var chunkSize = (byte) world.getPublicData().getChunkSize();

        int cx = Integer.parseInt(parts[0]);
        int cz = Integer.parseInt(parts[1]);

        // Calculate chunk bounds
        int chunkMinX = cx * chunkSize;
        int chunkMaxX = chunkMinX + chunkSize - 1;
        int chunkMinZ = cz * chunkSize;
        int chunkMaxZ = chunkMinZ + chunkSize - 1;

        // Build block map from all models (sorted by order)
        Map<String, LayerBlock> blockMap = new HashMap<>();

        for (WLayerModel model : models) {
            if (model.getContent() == null || model.getContent().isEmpty()) {
                continue;
            }

            int mountX = model.getMountX();
            int mountY = model.getMountY();
            int mountZ = model.getMountZ();

            // Add/overlay model blocks that fall within chunk bounds
            for (LayerBlock layerBlock : model.getContent()) {
                if (layerBlock.getBlock() == null || layerBlock.getBlock().getPosition() == null) {
                    continue;
                }

                de.mhus.nimbus.generated.types.Block relativeBlock = layerBlock.getBlock();

                // Calculate world position
                int worldX = mountX + (int) relativeBlock.getPosition().getX();
                int worldY = mountY + (int) relativeBlock.getPosition().getY();
                int worldZ = mountZ + (int) relativeBlock.getPosition().getZ();

                // Check if within chunk bounds
                if (worldX >= chunkMinX && worldX <= chunkMaxX &&
                        worldZ >= chunkMinZ && worldZ <= chunkMaxZ) {

                    de.mhus.nimbus.generated.types.Vector3Int worldPos = new de.mhus.nimbus.generated.types.Vector3Int();
                    worldPos.setX(worldX);
                    worldPos.setY(worldY);
                    worldPos.setZ(worldZ);
                    String key = blockKey(worldPos);

                    // Create new LayerBlock with world coordinates
                    LayerBlock newLayerBlock = LayerBlock.builder()
                            .block(cloneBlockWithPosition(relativeBlock, worldPos))
                            .group(layerBlock.getGroup())
                            .metadata(layerBlock.getMetadata())
                            .build();

                    blockMap.put(key, newLayerBlock);
                }
            }
        }

        // Save terrain chunk (only if there are blocks)
        if (!blockMap.isEmpty()) {
            LayerChunkData chunkData = LayerChunkData.builder()
                    .blocks(new ArrayList<>(blockMap.values()))
                    .build();

            if (worldId != null) {
                saveTerrainChunk(worldId, layerDataId, chunkKey, chunkData);
            }
        }
    }

    /**
     * Resolve a reference model ID (format: "worldId/name" or "worldId:branch/name").
     * Returns the referenced model or empty if not found.
     *
     * @param referenceModelId Reference in format "worldId/name" (e.g., "earth616:westview/TownHall")
     * @return Referenced model or empty
     */
    private Optional<WLayerModel> resolveReferenceModel(String referenceModelId) {
        if (referenceModelId == null || referenceModelId.isBlank()) {
            return Optional.empty();
        }

        // Parse reference: "worldId/name"
        int slashIndex = referenceModelId.lastIndexOf('/');
        if (slashIndex <= 0) {
            log.warn("Invalid referenceModelId format (expected 'worldId/name'): {}", referenceModelId);
            return Optional.empty();
        }

        String worldId = referenceModelId.substring(0, slashIndex);
        String modelName = referenceModelId.substring(slashIndex + 1);

        // Find model by worldId and name
        List<WLayerModel> models = modelRepository.findByWorldIdAndName(worldId, modelName);
        if (models.isEmpty()) {
            log.warn("Referenced model not found: worldId={} name={}", worldId, modelName);
            return Optional.empty();
        }

        if (models.size() > 1) {
            log.warn("Multiple models found for reference (using first): worldId={} name={} count={}",
                    worldId, modelName, models.size());
        }

        return Optional.of(models.get(0));
    }

    /**
     * Rotate a position around origin by 90-degree steps.
     * Rotation is around Y-axis (vertical axis).
     *
     * @param x        X coordinate
     * @param z        Z coordinate
     * @param rotation Rotation in 90-degree steps (0-3)
     * @return Rotated [x, z] coordinates
     */
    private int[] rotatePosition(int x, int z, int rotation) {
        int normalizedRotation = rotation % 4;
        return switch (normalizedRotation) {
            case 1 -> new int[]{-z, x};  // 90 degrees clockwise
            case 2 -> new int[]{-x, -z}; // 180 degrees
            case 3 -> new int[]{z, -x};  // 270 degrees clockwise (90 counter-clockwise)
            default -> new int[]{x, z};  // 0 degrees (no rotation)
        };
    }

    /**
     * Resolve and merge referenced models recursively.
     * Follows referenceModelId chain up to maxDepth and returns merged content.
     * Mount points are added together, rotations are accumulated.
     *
     * @param model            Current model to resolve
     * @param depth            Current recursion depth
     * @param maxDepth         Maximum recursion depth (default 10)
     * @param mountXOffset     Accumulated mount X offset from parent references
     * @param mountYOffset     Accumulated mount Y offset from parent references
     * @param mountZOffset     Accumulated mount Z offset from parent references
     * @param accumulatedRotation Accumulated rotation from parent references (0-3)
     * @return List of LayerBlocks with resolved positions
     */
    private List<LayerBlock> resolveModelWithReferences(WLayerModel model, int depth, int maxDepth,
                                                        int mountXOffset, int mountYOffset, int mountZOffset,
                                                        int accumulatedRotation) {
        if (depth > maxDepth) {
            log.warn("Maximum reference depth exceeded: modelId={} depth={}", model.getId(), depth);
            return new ArrayList<>();
        }

        List<LayerBlock> result = new ArrayList<>();
        int currentRotation = (model.getRotation() + accumulatedRotation) % 4;

        // First, resolve reference if present
        if (model.getReferenceModelId() != null && !model.getReferenceModelId().isBlank()) {
            Optional<WLayerModel> refModelOpt = resolveReferenceModel(model.getReferenceModelId());
            if (refModelOpt.isPresent()) {
                WLayerModel refModel = refModelOpt.get();
                log.debug("Following reference: modelId={} -> refId={} refName={} depth={} rotation={}",
                        model.getId(), refModel.getId(), refModel.getName(), depth, currentRotation);

                // Apply current model's rotation to mount offsets before recursing
                int[] rotatedMount = rotatePosition(model.getMountX(), model.getMountZ(), accumulatedRotation);

                // Recursively resolve reference with accumulated mount offsets and rotation
                List<LayerBlock> refBlocks = resolveModelWithReferences(
                        refModel,
                        depth + 1,
                        maxDepth,
                        mountXOffset + rotatedMount[0],
                        mountYOffset + model.getMountY(),
                        mountZOffset + rotatedMount[1],
                        currentRotation
                );
                result.addAll(refBlocks);
            }
        }

        // Then add this model's own content with accumulated mount offsets and rotation
        if (model.getContent() != null && !model.getContent().isEmpty()) {
            for (LayerBlock block : model.getContent()) {
                if (block.getBlock() == null || block.getBlock().getPosition() == null) {
                    continue;
                }

                // Get original position
                de.mhus.nimbus.generated.types.Vector3Int originalPos = block.getBlock().getPosition();
                int x = (int) originalPos.getX();
                int y = (int) originalPos.getY();
                int z = (int) originalPos.getZ();

                // Apply accumulated rotation
                int[] rotated = rotatePosition(x, z, accumulatedRotation);

                // Then add accumulated mount offsets
                de.mhus.nimbus.generated.types.Vector3Int adjustedPos = new de.mhus.nimbus.generated.types.Vector3Int();
                adjustedPos.setX(rotated[0] + mountXOffset);
                adjustedPos.setY(y + mountYOffset);
                adjustedPos.setZ(rotated[1] + mountZOffset);

                LayerBlock adjustedBlock = LayerBlock.builder()
                        .block(cloneBlockWithPosition(block.getBlock(), adjustedPos))
                        .group(block.getGroup())
                        .metadata(block.getMetadata())
                        .build();

                result.add(adjustedBlock);
            }
        }

        return result;
    }

    /**
     * Transfer a single WLayerModel into WLayerTerrain storage.
     * This method calculates affected chunks, generates terrain data for each chunk,
     * and optionally marks chunks as dirty.
     *
     * IMPORTANT: This does NOT happen automatically during DirtyChunk processing.
     * This must be called manually when a model is created or updated.
     *
     * @param modelId           Model document ID to transfer
     * @param markChunksDirty   If true, marks affected chunks as dirty after transfer
     * @return Number of chunks affected, or -1 if model not found
     */
    @Transactional
    public int transferModelToTerrain(String modelId, boolean markChunksDirty) {
        // Load model
        Optional<WLayerModel> modelOpt = modelRepository.findById(modelId);
        if (modelOpt.isEmpty()) {
            log.warn("Model not found for transfer: id={}", modelId);
            return -1;
        }

        WLayerModel model = modelOpt.get();

        // Get the layer by layerDataId
        Optional<WLayer> layerOpt = layerRepository.findByLayerDataId(model.getLayerDataId());
        if (layerOpt.isEmpty()) {
            log.warn("Layer not found for model transfer: layerDataId={}", model.getLayerDataId());
            return -1;
        }

        WLayer layer = layerOpt.get();

        // Resolve model with references (max depth 10)
        // Start with rotation 0 since we want to apply the model's own rotation
        log.debug("Resolving model with references: modelId={} rotation={}", modelId, model.getRotation());
        List<LayerBlock> resolvedContent = resolveModelWithReferences(model, 0, 10, 0, 0, 0, 0);

        if (resolvedContent.isEmpty()) {
            log.debug("Model has no resolved content: modelId={}", modelId);
            return 0;
        }

        // Create temporary model with resolved content for chunk calculation
        WLayerModel resolvedModel = WLayerModel.builder()
                .worldId(model.getWorldId())
                .mountX(model.getMountX())
                .mountY(model.getMountY())
                .mountZ(model.getMountZ())
                .content(resolvedContent)
                .build();

        // Calculate affected chunks from resolved model content
        Set<String> affectedChunks = calculateAffectedChunks(resolvedModel);

        if (affectedChunks.isEmpty()) {
            log.debug("Resolved model has no affected chunks: modelId={}", modelId);
            return 0;
        }

        // Update WLayer.affectedChunks if needed
        updateLayerAffectedChunks(layer, affectedChunks);

        // Transfer resolved model blocks to terrain for each affected chunk
        int chunksProcessed = 0;
        for (String chunkKey : affectedChunks) {
            try {
                transferModelToTerrainChunk(resolvedModel, layer.getLayerDataId(), chunkKey);
                chunksProcessed++;
            } catch (Exception e) {
                log.error("Failed to transfer model to terrain chunk: modelId={} chunkKey={}", modelId, chunkKey, e);
            }
        }

        log.info("Transferred model to terrain: modelId={} name={} chunks={}",
                modelId, model.getName(), chunksProcessed);

        // Mark chunks as dirty if requested
        if (markChunksDirty && chunksProcessed > 0) {
            dirtyChunkService.markChunksDirty(model.getWorldId(), new ArrayList<>(affectedChunks), "model_transferred");
        }

        return chunksProcessed;
    }

    /**
     * Calculate affected chunks from model content.
     */
    private Set<String> calculateAffectedChunks(WLayerModel model) {
        Set<String> chunks = new HashSet<>();

        if (model.getContent() == null || model.getContent().isEmpty()) {
            return chunks;
        }

        var world = worldService.getByWorldId(model.getWorldId()).orElseThrow(
                () -> new IllegalArgumentException("World not found: " + model.getWorldId())
        );
        var chunkSize = (byte) world.getPublicData().getChunkSize();
        int mountX = model.getMountX();
        int mountZ = model.getMountZ();

        for (LayerBlock layerBlock : model.getContent()) {
            if (layerBlock.getBlock() == null || layerBlock.getBlock().getPosition() == null) {
                continue;
            }

            // Calculate world position
            int worldX = mountX + (int) layerBlock.getBlock().getPosition().getX();
            int worldZ = mountZ + (int) layerBlock.getBlock().getPosition().getZ();

            // Calculate chunk coordinates
            int cx = Math.floorDiv(worldX, chunkSize);
            int cz = Math.floorDiv(worldZ, chunkSize);

            chunks.add(cx + ":" + cz);
        }

        return chunks;
    }

    /**
     * Update WLayer.affectedChunks if new chunks are affected.
     */
    private void updateLayerAffectedChunks(WLayer layer, Set<String> newChunks) {
        if (layer.isAllChunks()) {
            // Layer affects all chunks, no need to update
            return;
        }

        Set<String> existingChunks = new HashSet<>(layer.getAffectedChunks());
        Set<String> updatedChunks = new HashSet<>(existingChunks);
        updatedChunks.addAll(newChunks);

        if (updatedChunks.size() > existingChunks.size()) {
            layer.setAffectedChunks(new ArrayList<>(updatedChunks));
            layer.touchUpdate();
            layerRepository.save(layer);
            log.debug("Updated layer affected chunks: layer={} oldCount={} newCount={}",
                    layer.getName(), existingChunks.size(), updatedChunks.size());
        }
    }

    /**
     * Transfer a single model's blocks to a specific terrain chunk.
     */
    private void transferModelToTerrainChunk(WLayerModel model, String layerDataId, String chunkKey) {
        // Parse chunk coordinates
        String[] parts = chunkKey.split(":");
        if (parts.length != 2) {
            log.warn("Invalid chunk key format: {}", chunkKey);
            return;
        }

        var world = worldService.getByWorldId(model.getWorldId()).orElseThrow(
                () -> new IllegalArgumentException("World not found: " + model.getWorldId())
        );
        var chunkSize = (byte) world.getPublicData().getChunkSize();

        int cx = Integer.parseInt(parts[0]);
        int cz = Integer.parseInt(parts[1]);

        // Calculate chunk bounds
        int chunkMinX = cx * chunkSize;
        int chunkMaxX = chunkMinX + chunkSize - 1;
        int chunkMinZ = cz * chunkSize;
        int chunkMaxZ = chunkMinZ + chunkSize - 1;

        // Load existing terrain chunk or create new one
        Optional<LayerChunkData> existingDataOpt = loadTerrainChunk(layerDataId, chunkKey);
        Map<String, LayerBlock> blockMap = new HashMap<>();

        // Add existing blocks to map
        if (existingDataOpt.isPresent()) {
            LayerChunkData existingData = existingDataOpt.get();
            if (existingData.getBlocks() != null) {
                for (LayerBlock block : existingData.getBlocks()) {
                    if (block.getBlock() != null && block.getBlock().getPosition() != null) {
                        String key = blockKey(block.getBlock().getPosition());
                        blockMap.put(key, block);
                    }
                }
            }
        }

        // Add/overlay model blocks that fall within chunk bounds
        int mountX = model.getMountX();
        int mountY = model.getMountY();
        int mountZ = model.getMountZ();

        for (LayerBlock layerBlock : model.getContent()) {
            if (layerBlock.getBlock() == null || layerBlock.getBlock().getPosition() == null) {
                continue;
            }

            de.mhus.nimbus.generated.types.Block relativeBlock = layerBlock.getBlock();

            // Calculate world position
            int worldX = mountX + (int) relativeBlock.getPosition().getX();
            int worldY = mountY + (int) relativeBlock.getPosition().getY();
            int worldZ = mountZ + (int) relativeBlock.getPosition().getZ();

            // Check if within chunk bounds
            if (worldX >= chunkMinX && worldX <= chunkMaxX &&
                    worldZ >= chunkMinZ && worldZ <= chunkMaxZ) {

                de.mhus.nimbus.generated.types.Vector3Int worldPos = new de.mhus.nimbus.generated.types.Vector3Int();
                worldPos.setX(worldX);
                worldPos.setY(worldY);
                worldPos.setZ(worldZ);
                String key = blockKey(worldPos);

                // Create new LayerBlock with world coordinates
                LayerBlock newLayerBlock = LayerBlock.builder()
                        .block(cloneBlockWithPosition(relativeBlock, worldPos))
                        .group(layerBlock.getGroup())
                        .metadata(layerBlock.getMetadata())
                        .build();

                blockMap.put(key, newLayerBlock);
            }
        }

        // Save terrain chunk
        LayerChunkData chunkData = LayerChunkData.builder()
                .blocks(new ArrayList<>(blockMap.values()))
                .build();

        saveTerrainChunk(model.getWorldId(), layerDataId, chunkKey, chunkData);
    }

    /**
     * Generate block key from position.
     */
    private String blockKey(de.mhus.nimbus.generated.types.Vector3Int pos) {
        return pos.getX() + ":" + pos.getY() + ":" + pos.getZ();
    }

    /**
     * Clone block with new position.
     */
    private de.mhus.nimbus.generated.types.Block cloneBlockWithPosition(
            de.mhus.nimbus.generated.types.Block source,
            de.mhus.nimbus.generated.types.Vector3Int newPosition) {
        try {
            String json = objectMapper.writeValueAsString(source);
            de.mhus.nimbus.generated.types.Block cloned = objectMapper.readValue(json, de.mhus.nimbus.generated.types.Block.class);
            cloned.setPosition(newPosition);
            return cloned;
        } catch (Exception e) {
            log.error("Failed to clone block", e);
            // Fallback: modify original (not ideal but safe)
            source.setPosition(newPosition);
            return source;
        }
    }

    // ==================== MODEL LAYER OPERATIONS ====================

    /**
     * Create a new model layer document.
     *
     * NEW CONCEPT:
     * - Multiple WLayerModel documents can share the same layerDataId
     * - Each model has its own mount point and content
     * - Models are merged during chunk generation
     *
     * @param worldId     World identifier
     * @param layerDataId Layer data ID
     * @param name        Model name (optional)
     * @param title       Model title (optional)
     * @param mountX      Mount point X coordinate
     * @param mountY      Mount point Y coordinate
     * @param mountZ      Mount point Z coordinate
     * @param rotation    Rotation in 90 degree steps
     * @param order       Overlay order for this model
     * @param content     List of layer blocks with relative positions
     * @return Created model entity
     */
    @Transactional
    public WLayerModel createModel(String worldId, String layerDataId, String name, String title,
                                    int mountX, int mountY, int mountZ, int rotation,
                                    int order, List<LayerBlock> content) {
        if (content == null) {
            throw new IllegalArgumentException("Content is required");
        }

        WLayerModel newModel = WLayerModel.builder()
                .worldId(worldId)
                .layerDataId(layerDataId)
                .name(name)
                .title(title)
                .mountX(mountX)
                .mountY(mountY)
                .mountZ(mountZ)
                .rotation(rotation)
                .order(order)
                .content(content)
                .build();
        newModel.touchCreate();

        WLayerModel saved = modelRepository.save(newModel);
        log.info("Created model: id={} layerDataId={} name={} blocks={}", saved.getId(), layerDataId, name, content.size());

        return saved;
    }

    /**
     * Update an existing model layer document.
     *
     * @param modelId Model document ID
     * @param updater Consumer to modify the model
     * @return Updated model
     */
    @Transactional
    public Optional<WLayerModel> updateModel(String modelId, Consumer<WLayerModel> updater) {
        Optional<WLayerModel> modelOpt = modelRepository.findById(modelId);
        if (modelOpt.isEmpty()) {
            return Optional.empty();
        }

        WLayerModel model = modelOpt.get();
        updater.accept(model);
        model.touchUpdate();

        WLayerModel saved = modelRepository.save(model);
        log.info("Updated model: id={} name={}", modelId, model.getName());

        return Optional.of(saved);
    }

    /**
     * Transform a model layer by automatically adjusting the center point.
     * Calculates the average position of all blocks and shifts coordinates so this becomes the new origin.
     * mountX/Y/Z are adjusted in the opposite direction to keep world position.
     *
     * @param modelId Model identifier
     * @return Updated model with transformed coordinates
     */
    @Transactional
    public Optional<WLayerModel> autoAdjustCenter(String modelId) {
        Optional<WLayerModel> modelOpt = modelRepository.findById(modelId);
        if (modelOpt.isEmpty()) {
            log.warn("Model not found for adjustCenter: modelId={}", modelId);
            return Optional.empty();
        }

        WLayerModel model = modelOpt.get();

        // Calculate center point from all blocks
        List<LayerBlock> content = model.getContent();
        if (content == null || content.isEmpty()) {
            log.warn("Model has no content to adjust: modelId={}", modelId);
            return Optional.of(model);
        }

        // Calculate average position (center)
        double sumX = 0, sumY = 0, sumZ = 0;
        int count = 0;

        for (LayerBlock block : content) {
            if (block.getBlock() != null && block.getBlock().getPosition() != null) {
                de.mhus.nimbus.generated.types.Vector3Int pos = block.getBlock().getPosition();
                sumX += pos.getX();
                sumY += pos.getY();
                sumZ += pos.getZ();
                count++;
            }
        }

        if (count == 0) {
            log.warn("Model has no valid block positions to adjust: modelId={}", modelId);
            return Optional.of(model);
        }

        // Calculate center offset
        int offsetX = (int) Math.round(sumX / count);
        int offsetY = (int) Math.round(sumY / count);
        int offsetZ = (int) Math.round(sumZ / count);

        log.info("Adjusting center for model: modelId={} offset=({},{},{})", modelId, offsetX, offsetY, offsetZ);

        // Shift all block coordinates
        for (LayerBlock block : content) {
            if (block.getBlock() != null && block.getBlock().getPosition() != null) {
                de.mhus.nimbus.generated.types.Vector3Int pos = block.getBlock().getPosition();
                pos.setX(pos.getX() - offsetX);
                pos.setY(pos.getY() - offsetY);
                pos.setZ(pos.getZ() - offsetZ);
            }
        }

        // Adjust mount point in opposite direction to keep world position
        model.setMountX(model.getMountX() + offsetX);
        model.setMountY(model.getMountY() + offsetY);
        model.setMountZ(model.getMountZ() + offsetZ);

        model.touchUpdate();
        WLayerModel saved = modelRepository.save(model);

        log.info("Auto-adjusted center for model: modelId={} newMount=({},{},{})",
                modelId, saved.getMountX(), saved.getMountY(), saved.getMountZ());

        return Optional.of(saved);
    }

    /**
     * Transform a model layer by manually adjusting the center point.
     * Shifts all block coordinates by the specified offset and adjusts mountX/Y/Z in opposite direction.
     * The model remains at the same world position but with a transformed origin.
     *
     * @param modelId Model identifier
     * @param offsetX X offset to shift blocks (will be subtracted from block positions)
     * @param offsetY Y offset to shift blocks (will be subtracted from block positions)
     * @param offsetZ Z offset to shift blocks (will be subtracted from block positions)
     * @return Updated model with transformed coordinates
     */
    @Transactional
    public Optional<WLayerModel> manualAdjustCenter(String modelId, int offsetX, int offsetY, int offsetZ) {
        Optional<WLayerModel> modelOpt = modelRepository.findById(modelId);
        if (modelOpt.isEmpty()) {
            log.warn("Model not found for manual adjustCenter: modelId={}", modelId);
            return Optional.empty();
        }

        WLayerModel model = modelOpt.get();

        List<LayerBlock> content = model.getContent();
        if (content == null || content.isEmpty()) {
            log.warn("Model has no content to adjust: modelId={}", modelId);
            return Optional.of(model);
        }

        log.info("Manual adjust center for model: modelId={} offset=({},{},{})", modelId, offsetX, offsetY, offsetZ);

        // Shift all block coordinates
        for (LayerBlock block : content) {
            if (block.getBlock() != null && block.getBlock().getPosition() != null) {
                de.mhus.nimbus.generated.types.Vector3Int pos = block.getBlock().getPosition();
                pos.setX(pos.getX() - offsetX);
                pos.setY(pos.getY() - offsetY);
                pos.setZ(pos.getZ() - offsetZ);
            }
        }

        // Adjust mount point in opposite direction to keep world position
        model.setMountX(model.getMountX() + offsetX);
        model.setMountY(model.getMountY() + offsetY);
        model.setMountZ(model.getMountZ() + offsetZ);

        model.touchUpdate();
        WLayerModel saved = modelRepository.save(model);

        log.info("Manual adjusted center for model: modelId={} newMount=({},{},{})",
                modelId, saved.getMountX(), saved.getMountY(), saved.getMountZ());

        return Optional.of(saved);
    }

    /**
     * Transform a model layer by moving all blocks.
     * Shifts all block coordinates by the specified offset.
     * Unlike adjust center methods, the mount point is NOT changed.
     *
     * @param modelId Model identifier
     * @param offsetX X offset to move blocks
     * @param offsetY Y offset to move blocks
     * @param offsetZ Z offset to move blocks
     * @return Updated model with transformed coordinates
     */
    @Transactional
    public Optional<WLayerModel> transformMove(String modelId, int offsetX, int offsetY, int offsetZ) {
        Optional<WLayerModel> modelOpt = modelRepository.findById(modelId);
        if (modelOpt.isEmpty()) {
            log.warn("Model not found for transformMove: modelId={}", modelId);
            return Optional.empty();
        }

        WLayerModel model = modelOpt.get();

        List<LayerBlock> content = model.getContent();
        if (content == null || content.isEmpty()) {
            log.warn("Model has no content to move: modelId={}", modelId);
            return Optional.of(model);
        }

        log.info("Transform move for model: modelId={} offset=({},{},{})", modelId, offsetX, offsetY, offsetZ);

        // Shift all block coordinates
        for (LayerBlock block : content) {
            if (block.getBlock() != null && block.getBlock().getPosition() != null) {
                de.mhus.nimbus.generated.types.Vector3Int pos = block.getBlock().getPosition();
                pos.setX(pos.getX() + offsetX);
                pos.setY(pos.getY() + offsetY);
                pos.setZ(pos.getZ() + offsetZ);
            }
        }

        // Mount point stays the same
        model.touchUpdate();
        WLayerModel saved = modelRepository.save(model);

        log.info("Transform moved model: modelId={} offset=({},{},{})",
                modelId, offsetX, offsetY, offsetZ);

        return Optional.of(saved);
    }

    /**
     * Copy a model layer to another layer (possibly in different world).
     * Creates a complete copy of the WLayerModel with new worldId, layerDataId, and optional new name.
     *
     * @param sourceModelId Source model identifier
     * @param targetLayerId Target layer identifier (must be MODEL type)
     * @param newName Optional new name for the copied model (null keeps original)
     * @return Created model copy
     */
    @Transactional
    public Optional<WLayerModel> copyModel(String sourceModelId, String targetLayerId, String newName) {
        // Load source model
        Optional<WLayerModel> sourceOpt = modelRepository.findById(sourceModelId);
        if (sourceOpt.isEmpty()) {
            log.warn("Source model not found for copy: modelId={}", sourceModelId);
            return Optional.empty();
        }

        // Load target layer
        Optional<WLayer> targetLayerOpt = layerRepository.findById(targetLayerId);
        if (targetLayerOpt.isEmpty()) {
            log.warn("Target layer not found for copy: layerId={}", targetLayerId);
            return Optional.empty();
        }

        WLayer targetLayer = targetLayerOpt.get();
        if (targetLayer.getLayerType() != LayerType.MODEL) {
            log.warn("Target layer is not MODEL type: layerId={} type={}", targetLayerId, targetLayer.getLayerType());
            throw new IllegalArgumentException("Target layer must be MODEL type");
        }

        WLayerModel source = sourceOpt.get();

        // Create copy with new worldId and layerDataId from target layer
        WLayerModel copy = WLayerModel.builder()
                .worldId(targetLayer.getWorldId())
                .name(newName != null ? newName : source.getName())
                .title(source.getTitle())
                .layerDataId(targetLayer.getLayerDataId())
                .mountX(source.getMountX())
                .mountY(source.getMountY())
                .mountZ(source.getMountZ())
                .rotation(source.getRotation())
                .referenceModelId(source.getReferenceModelId())
                .order(source.getOrder())
                .content(new ArrayList<>(source.getContent())) // Deep copy of content list
                .groups(new HashMap<>(source.getGroups())) // Copy of groups map
                .build();

        copy.touchCreate();
        WLayerModel saved = modelRepository.save(copy);

        log.info("Copied model: sourceId={} targetLayerId={} newId={} newName={}",
                sourceModelId, targetLayerId, saved.getId(), saved.getName());

        return Optional.of(saved);
    }

    /**
     * Save model layer content (deprecated - use createModel or updateModel).
     *
     * @deprecated This method assumes 1:1 relationship. Use createModel for new concept.
     */
    @Deprecated
    @Transactional
    public WLayerModel saveModel(String worldId, String layerDataId, List<LayerBlock> content) {
        if (content == null) {
            throw new IllegalArgumentException("Content is required");
        }

        // Find or create entity (old behavior - only one model per layerDataId)
        WLayerModel entity = modelRepository.findFirstByLayerDataId(layerDataId)
                .orElseGet(() -> {
                    WLayerModel newEntity = WLayerModel.builder()
                            .worldId(worldId)
                            .layerDataId(layerDataId)
                            .build();
                    newEntity.touchCreate();
                    return newEntity;
                });

        entity.setContent(content);
        entity.touchUpdate();

        WLayerModel saved = modelRepository.save(entity);
        log.info("Saved model: layerDataId={} blocks={}", layerDataId, content.size());

        return saved;
    }

    /**
     * Get model IDs for a layerDataId.
     *
     * NEW CONCEPT: Returns only IDs to avoid heavy memory load.
     * Load full models step by step using loadModelById.
     */
    @Transactional(readOnly = true)
    public List<String> getModelIds(String layerDataId) {
        return modelRepository.findByLayerDataIdOrderByOrder(layerDataId).stream()
                .map(WLayerModel::getId)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Count models for a layerDataId.
     */
    @Transactional(readOnly = true)
    public long countModels(String layerDataId) {
        return modelRepository.countByLayerDataId(layerDataId);
    }

    /**
     * Load single model layer content (deprecated).
     *
     * @deprecated Returns only the first model. Use getModelIds + loadModelById for new concept.
     */
    @Transactional(readOnly = true)
    public Optional<WLayerModel> loadModelByMongoId(String modelId) {
        return modelRepository.findById(modelId);
    }

    /**
     * Load a specific model by its document ID.
     */
    @Transactional(readOnly = true)
    public Optional<WLayerModel> loadModelById(String modelId) {
        return modelRepository.findById(modelId);
    }

    /**
     * Delete a specific model document.
     *
     * @param modelId Model document ID
     * @return True if deleted
     */
    @Transactional
    public boolean deleteModelById(String modelId) {
        if (!modelRepository.existsById(modelId)) {
            return false;
        }

        modelRepository.deleteById(modelId);
        log.debug("Deleted model: id={}", modelId);
        return true;
    }

    /**
     * Delete all model documents for a layerDataId.
     */
    @Transactional
    public boolean deleteModel(String layerDataId) {
        long count = modelRepository.countByLayerDataId(layerDataId);
        if (count == 0) {
            return false;
        }

        modelRepository.deleteByLayerDataId(layerDataId);
        log.debug("Deleted {} models: layerDataId={}", count, layerDataId);
        return true;
    }

    /**
     * Get all block positions from all models in a layer.
     * Returns world coordinates (mount point + relative position).
     *
     * @param layerDataId Layer data ID
     * @return List of block positions as int arrays [x, y, z]
     */
    @Transactional(readOnly = true)
    public List<int[]> getModelBlockPositions(String layerDataId) {
        List<WLayerModel> models = modelRepository.findByLayerDataIdOrderByOrder(layerDataId);
        if (models.isEmpty()) {
            return List.of();
        }

        List<int[]> positions = new ArrayList<>();
        for (WLayerModel model : models) {
            model.getBlockPositions().forEach(positions::add);
        }

        return positions;
    }

    /**
     * Result object for block origin lookup.
     */
    public record BlockOrigin(
            WLayer layer,
            WLayerTerrain terrain,
            WLayerModel model,      // Optional - only for MODEL layers
            LayerBlock layerBlock   // The actual block data from the layer
    ) {}

    /**
     * Find the origin of a block at a specific position.
     * Searches backwards through layers (by order descending) to find which layer
     * defines the block at this position.
     *
     * IMPORTANT: Does not load all WLayerModel documents at once to prevent memory overflow.
     * Loads models one by one when checking MODEL layers.
     *
     * @param worldId World identifier
     * @param x       Block X coordinate
     * @param y       Block Y coordinate
     * @param z       Block Z coordinate
     * @return BlockOrigin with layer, terrain, and optional model, or null if not found
     */
    @Transactional(readOnly = true)
    public BlockOrigin findBlockOrigin(String worldId, int x, int y, int z) {
        // Calculate chunk key
        var world = worldService.getByWorldId(worldId).orElseThrow(
                () -> new IllegalArgumentException("World not found: " + worldId)
        );
        var chunkSize = (byte) world.getPublicData().getChunkSize();
        int cx = Math.floorDiv(x, chunkSize);
        int cz = Math.floorDiv(z, chunkSize);
        String chunkKey = cx + ":" + cz;

        // Get all layers affecting this chunk, sorted by order
        List<WLayer> layers = getLayersAffectingChunk(worldId, chunkKey);

        // Search backwards (highest order first = top layer first)
        for (int i = layers.size() - 1; i >= 0; i--) {
            WLayer layer = layers.get(i);

            if (!layer.isEnabled()) {
                continue;
            }

            // Load terrain chunk
            Optional<WLayerTerrain> terrainOpt = terrainRepository
                    .findByLayerDataIdAndChunkKey(layer.getLayerDataId(), chunkKey);

            if (terrainOpt.isEmpty()) {
                // For MODEL layers without terrain: Search directly in models
                if (layer.getLayerType() == LayerType.MODEL) {
                    WLayerModel sourceModel = findModelForBlock(layer.getLayerDataId(), x, y, z);
                    if (sourceModel != null) {
                        // Found in model, but no terrain entry yet
                        // Find the LayerBlock with this position
                        LayerBlock foundBlock = findLayerBlockInModel(sourceModel, x, y, z);
                        if (foundBlock != null) {
                            log.debug("Block found in model but not in terrain: layerDataId={} pos=({},{},{})",
                                    layer.getLayerDataId(), x, y, z);
                            // Return with null terrain to indicate model-only source
                            return new BlockOrigin(layer, null, sourceModel, foundBlock);
                        }
                    }
                }
                continue;
            }

            WLayerTerrain terrain = terrainOpt.get();

            // Load terrain data from storage
            Optional<LayerChunkData> chunkDataOpt = loadTerrainChunk(layer.getLayerDataId(), chunkKey);
            if (chunkDataOpt.isEmpty()) {
                continue;
            }

            LayerChunkData chunkData = chunkDataOpt.get();

            // Search for block at position in terrain data
            if (chunkData.getBlocks() != null) {
                for (LayerBlock layerBlock : chunkData.getBlocks()) {
                    if (layerBlock.getBlock() == null || layerBlock.getBlock().getPosition() == null) {
                        continue;
                    }

                    de.mhus.nimbus.generated.types.Vector3Int pos = layerBlock.getBlock().getPosition();
                    if (pos.getX() == x && pos.getY() == y && pos.getZ() == z) {
                        // Found block at this position

                        // For MODEL layers: Find which model contains this block
                        WLayerModel sourceModel = null;
                        if (layer.getLayerType() == LayerType.MODEL) {
                            sourceModel = findModelForBlock(layer.getLayerDataId(), x, y, z);
                        }

                        return new BlockOrigin(layer, terrain, sourceModel, layerBlock);
                    }
                }
            }
        }

        // Block not found in any layer
        return null;
    }

    /**
     * Find which WLayerModel contains a block at the given world coordinates.
     * Loads models one by one to prevent memory overflow.
     * Takes rotation into account when calculating world coordinates.
     *
     * @param layerDataId Layer data ID
     * @param worldX      World X coordinate
     * @param worldY      World Y coordinate
     * @param worldZ      World Z coordinate
     * @return WLayerModel containing this block, or null if not found
     */
    private WLayerModel findModelForBlock(String layerDataId, int worldX, int worldY, int worldZ) {
        // Get model IDs (lightweight - only IDs, not full documents)
        List<String> modelIds = getModelIds(layerDataId);

        // Search through models one by one (reverse order - highest first)
        for (int i = modelIds.size() - 1; i >= 0; i--) {
            String modelId = modelIds.get(i);

            // Load single model
            Optional<WLayerModel> modelOpt = loadModelById(modelId);
            if (modelOpt.isEmpty()) {
                continue;
            }

            WLayerModel model = modelOpt.get();

            // Check if this model contains the block at world coordinates
            if (findLayerBlockInModel(model, worldX, worldY, worldZ) != null) {
                return model;
            }
        }

        return null;
    }

    /**
     * Find the LayerBlock in a model at given world coordinates.
     * Takes rotation into account.
     *
     * @param model  WLayerModel to search in
     * @param worldX World X coordinate
     * @param worldY World Y coordinate
     * @param worldZ World Z coordinate
     * @return LayerBlock if found, null otherwise
     */
    private LayerBlock findLayerBlockInModel(WLayerModel model, int worldX, int worldY, int worldZ) {
        if (model.getContent() == null) {
            return null;
        }

        for (LayerBlock layerBlock : model.getContent()) {
            if (layerBlock.getBlock() == null || layerBlock.getBlock().getPosition() == null) {
                continue;
            }

            de.mhus.nimbus.generated.types.Vector3Int relativePos = layerBlock.getBlock().getPosition();

            // Apply rotation to relative position
            int[] rotatedPos = applyRotation(
                    relativePos.getX(),
                    relativePos.getZ(),
                    model.getRotation()
            );

            // Calculate world position with rotation
            int blockWorldX = model.getMountX() + rotatedPos[0];
            int blockWorldY = model.getMountY() + relativePos.getY();
            int blockWorldZ = model.getMountZ() + rotatedPos[1];

            if (blockWorldX == worldX && blockWorldY == worldY && blockWorldZ == worldZ) {
                return layerBlock;
            }
        }

        return null;
    }

    /**
     * Apply rotation to relative coordinates.
     * Rotation is in 90 degree steps: 0 = no rotation, 1 = 90°, 2 = 180°, 3 = 270°
     *
     * @param x        Relative X coordinate
     * @param z        Relative Z coordinate
     * @param rotation Rotation in 90 degree steps
     * @return Rotated coordinates [x, z]
     */
    private int[] applyRotation(int x, int z, int rotation) {
        // Normalize rotation to 0-3
        int rot = rotation % 4;
        if (rot < 0) rot += 4;

        return switch (rot) {
            case 0 -> new int[]{x, z};          // No rotation
            case 1 -> new int[]{-z, x};         // 90° clockwise
            case 2 -> new int[]{-x, -z};        // 180°
            case 3 -> new int[]{z, -x};         // 270° clockwise (= 90° counter-clockwise)
            default -> new int[]{x, z};
        };
    }

    // ==================== HELPER METHODS ====================

    /**
     * Mark affected chunks as dirty when layer changes.
     */
    private void markAffectedChunksDirty(WLayer layer, String reason) {
        if (layer.isAllChunks()) {
            // All chunks affected - warn about performance
            log.warn("Layer affects all chunks, consider full world regeneration: layer={} world={}",
                    layer.getName(), layer.getWorldId());
            // TODO: Implement strategy for marking all chunks dirty
            // Option 1: Mark all existing WChunks dirty
            // Option 2: Set flag in WWorld for full regeneration
        } else {
            dirtyChunkService.markChunksDirty(layer.getWorldId(), layer.getAffectedChunks(), reason);
        }
    }
}
