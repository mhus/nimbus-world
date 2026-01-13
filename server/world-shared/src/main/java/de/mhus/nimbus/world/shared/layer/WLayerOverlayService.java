package de.mhus.nimbus.world.shared.layer;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.generated.types.Block;
import de.mhus.nimbus.generated.types.ChunkData;
import de.mhus.nimbus.generated.types.Vector3;
import de.mhus.nimbus.generated.types.Vector3Int;
import de.mhus.nimbus.shared.storage.StorageService;
import de.mhus.nimbus.world.shared.world.WWorldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.*;

/**
 * Service for layer overlay algorithm.
 * Merges multiple layers into a single chunk.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WLayerOverlayService {

    private final WLayerService layerService;
    private final WLayerTerrainRepository terrainRepository;
    private final WLayerModelRepository modelRepository;
    private final StorageService storageService;
    private final ObjectMapper objectMapper;
    private final WWorldService worldService;
    private final de.mhus.nimbus.world.shared.world.WBlockTypeService blockTypeService;

    /**
     * Generate final chunk by overlaying all enabled layers.
     *
     * NEW CONCEPT:
     * - All layers now have a WLayerTerrain
     * - GROUND layers: Direct terrain data, can be edited
     * - MODEL layers: Terrain data is generated from multiple WLayerModel documents
     *
     * @param worldId  World identifier
     * @param chunkKey Chunk key (format: "cx:cz")
     * @return Merged ChunkData or empty Optional if no layers
     */
    @Transactional(readOnly = true)
    public Optional<ChunkData> generateChunk(String worldId, String chunkKey) {

        var world = worldService.getByWorldId(worldId).orElseThrow(
                () -> new IllegalArgumentException("World not found: " + worldId)
        );
        var chunkSize = (byte) world.getPublicData().getChunkSize();

        // Parse chunk coordinates
        String[] parts = chunkKey.split(":");
        if (parts.length != 2) {
            log.warn("Invalid chunk key format: {}", chunkKey);
            return Optional.empty();
        }

        int cx;
        int cz;
        try {
            cx = Integer.parseInt(parts[0]);
            cz = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            log.warn("Invalid chunk coordinates: {}", chunkKey, e);
            return Optional.empty();
        }

        // Get all layers affecting this chunk (sorted by order)
        List<WLayer> layers = layerService.getLayersAffectingChunk(worldId, chunkKey);

        if (layers.isEmpty()) {
            log.debug("No layers affecting chunk {}, returning empty", chunkKey);
            return Optional.empty();
        }

        // Initialize canvas: Map<"x:y:z", Block>
        Map<String, Block> blockMap = new HashMap<>();

        // Overlay each layer (bottom to top)
        // All layers are now terrain-based
        for (WLayer layer : layers) {
            if (!layer.isEnabled()) {
                continue;
            }

            try {
                // All layers are now processed as terrain layers
                overlayTerrainLayer(layer, chunkKey, cx, cz, blockMap);
            } catch (Exception e) {
                log.error("Failed to overlay layer {} on chunk {}", layer.getName(), chunkKey, e);
                // Continue with other layers
            }
        }


        // Convert map to ChunkData
        ChunkData result = new ChunkData();
        result.setCx(cx);
        result.setCz(cz);
        result.setSize(chunkSize);
        result.setBlocks(new ArrayList<>(blockMap.values()));

        // Calculate height data
        int[][] heightData = calculateHeightData(worldId, chunkSize, blockMap.values(), layers);
        result.setHeightData(heightData);

        log.debug("Generated chunk {} from {} layers, {} blocks",
                chunkKey, layers.size(), blockMap.size());

        return Optional.of(result);
    }

    /**
     * Overlay terrain layer onto block map.
     *
     * NEW CONCEPT:
     * - For GROUND layers: Load directly from WLayerTerrain storage
     * - For MODEL layers: First merge all WLayerModel documents into terrain data,
     *   then overlay the merged terrain data
     */
    private void overlayTerrainLayer(WLayer layer, String chunkKey, int cx, int cz, Map<String, Block> blockMap) {
        // Load terrain chunk from storage
        Optional<WLayerTerrain> terrainOpt = terrainRepository
                .findByLayerDataIdAndChunkKey(layer.getLayerDataId(), chunkKey);

        if (terrainOpt.isEmpty()) {
            // For MODEL layers, terrain might not exist yet - need to generate from models
            if (layer.getLayerType() == LayerType.MODEL) {
                overlayModelLayersToTerrain(layer, chunkKey, cx, cz, blockMap);
            } else {
                log.trace("No terrain data for layer {} chunk {}", layer.getName(), chunkKey);
            }
            return;
        }

        WLayerTerrain terrain = terrainOpt.get();
        if (terrain.getStorageId() == null) {
            log.warn("Terrain chunk has no storageId: layer={} chunk={}", layer.getName(), chunkKey);
            return;
        }

        // Load chunk data from storage
        try (InputStream stream = storageService.load(terrain.getStorageId())) {
            if (stream == null) {
                log.warn("Failed to load terrain storage: {}", terrain.getStorageId());
                return;
            }

            // Decompress if needed
            InputStream dataStream = stream;
            if (terrain.isCompressed()) {
                dataStream = new java.util.zip.GZIPInputStream(stream);
            }

            LayerChunkData chunkData = objectMapper.readValue(dataStream, LayerChunkData.class);
            dataStream.close();

            // Overlay blocks (later blocks overwrite earlier ones)
            if (chunkData.getBlocks() != null) {
                for (LayerBlock layerBlock : chunkData.getBlocks()) {
                    if (layerBlock.getBlock() == null || layerBlock.getBlock().getPosition() == null) {
                        continue;
                    }

                    Block block = layerBlock.getBlock();
                    String key = blockKey(block.getPosition());

                    blockMap.put(key, block); // Overwrite previous layer
                }
            }

            log.trace("Overlaid terrain layer {}: {} blocks",
                    layer.getName(),
                    chunkData.getBlocks() != null ? chunkData.getBlocks().size() : 0);

        } catch (Exception e) {
            log.error("Failed to load terrain layer {} chunk {}", layer.getName(), chunkKey, e);
        }
    }

    /**
     * Overlay all model layers (WLayerModel documents) into terrain for a chunk.
     *
     * NEW CONCEPT:
     * - All WLayerModel documents with the same layerDataId are merged
     * - Each WLayerModel has its own mount point
     * - Result is overlaid onto the blockMap
     */
    private void overlayModelLayersToTerrain(WLayer layer, String chunkKey, int cx, int cz, Map<String, Block> blockMap) {
        // Load all models for this layerDataId (already sorted by order)
        List<WLayerModel> models = modelRepository.findByLayerDataIdOrderByOrder(layer.getLayerDataId());

        if (models.isEmpty()) {
            log.trace("No model data for layer {}", layer.getName());
            return;
        }

        var worldId = layer.getWorldId();
        var world = worldService.getByWorldId(worldId).orElseThrow(
                () -> new IllegalArgumentException("World not found: " + worldId)
        );
        var chunkSize = (byte) world.getPublicData().getChunkSize();


        // Calculate chunk bounds
        int chunkMinX = cx * chunkSize;
        int chunkMaxX = chunkMinX + chunkSize - 1;
        int chunkMinZ = cz * chunkSize;
        int chunkMaxZ = chunkMinZ + chunkSize - 1;

        int totalOverlaidCount = 0;

        // Process each model (already sorted by order)
        for (WLayerModel model : models) {
            if (model.getContent() == null || model.getContent().isEmpty()) {
                continue;
            }

            // Get mount point from model
            int mountX = model.getMountX();
            int mountY = model.getMountY();
            int mountZ = model.getMountZ();

            // Overlay blocks that fall within chunk bounds
            int overlaidCount = 0;
            for (LayerBlock layerBlock : model.getContent()) {
                if (layerBlock.getBlock() == null || layerBlock.getBlock().getPosition() == null) {
                    continue;
                }

                Block relativeBlock = layerBlock.getBlock();

                // Calculate world position
                int worldX = mountX + (int) relativeBlock.getPosition().getX();
                int worldY = mountY + (int) relativeBlock.getPosition().getY();
                int worldZ = mountZ + (int) relativeBlock.getPosition().getZ();

                // Check if within chunk bounds
                if (worldX >= chunkMinX && worldX <= chunkMaxX &&
                        worldZ >= chunkMinZ && worldZ <= chunkMaxZ) {

                    Vector3Int worldPos = new Vector3Int();
                    worldPos.setX(worldX);
                    worldPos.setY(worldY);
                    worldPos.setZ(worldZ);
                    String key = blockKey(worldPos);

                    // Create block with world coordinates
                    Block worldBlock = cloneBlock(relativeBlock);
                    worldBlock.setPosition(worldPos);

                    blockMap.put(key, worldBlock);
                    overlaidCount++;
                }
            }

            totalOverlaidCount += overlaidCount;
            log.trace("Overlaid model {} (order={}): {} blocks", model.getName(), model.getOrder(), overlaidCount);
        }

        log.trace("Overlaid {} model layers for layer {}: {} total blocks", models.size(), layer.getName(), totalOverlaidCount);
    }

    /**
     * Generate block key from position.
     */
    private String blockKey(Vector3Int pos) {
        return pos.getX() + ":" + pos.getY() + ":" + pos.getZ();
    }

    /**
     * Deep copy block using Jackson.
     */
    private Block cloneBlock(Block source) {
        try {
            return objectMapper.readValue(
                    objectMapper.writeValueAsString(source), Block.class);
        } catch (Exception e) {
            log.error("Failed to clone block", e);
            return source; // Fallback
        }
    }

    /**
     * Calculate height data for chunk.
     * Format: int[chunkSize * chunkSize][3 or 4]
     * Each entry: [x, z, maxHeight, groundLevel, waterLevel?]
     *
     * @param worldId World identifier for block type lookups
     * @param chunkSize Chunk size
     * @param blocks All blocks in the chunk
     * @param layers All layers (to find baseGround layer)
     * @return Height data array
     */
    private int[][] calculateHeightData(String worldId, int chunkSize, Collection<Block> blocks, List<WLayer> layers) {
        var worldIdObj = de.mhus.nimbus.shared.types.WorldId.of(worldId).orElseThrow();
        var world = worldService.getByWorldId(worldId).orElseThrow();
        int maxHeight = (int) world.getPublicData().getStop().getY();

        // Find base ground layer
        WLayer baseGroundLayer = null;
        for (WLayer layer : layers) {
            if (layer.isBaseGround() && layer.isEnabled()) {
                baseGroundLayer = layer;
                break;
            }
        }

        // Group blocks by column (x, z)
        Map<String, ColumnData> columns = new HashMap<>();

        for (Block block : blocks) {
            if (block.getPosition() == null) continue;

            int localX = ((int) block.getPosition().getX() % chunkSize + chunkSize) % chunkSize;
            int localZ = ((int) block.getPosition().getZ() % chunkSize + chunkSize) % chunkSize;
            String columnKey = localX + "," + localZ;

            ColumnData column = columns.computeIfAbsent(columnKey, k -> new ColumnData(localX, localZ, maxHeight));
            column.addBlock(block);

            // Check if this block is in baseGround layer and not water
            if (baseGroundLayer != null && block.getBlockTypeId() != null) {
                var blockType = blockTypeService.findByBlockId(worldIdObj, block.getBlockTypeId());
                if (blockType.isPresent() && blockType.get().getPublicData() != null) {
                    Integer shapeInt = getShapeFromBlockType(blockType.get().getPublicData());
                    boolean isWater = isWaterShape(shapeInt);

                    if (!isWater) {
                        int y = (int) block.getPosition().getY();
                        if (column.groundLevel == null || y > column.groundLevel) {
                            column.groundLevel = y;
                        }
                    } else {
                        int y = (int) block.getPosition().getY();
                        if (column.waterLevel == null || y > column.waterLevel) {
                            column.waterLevel = y;
                        }
                    }
                }
            }
        }

        // Convert to array
        List<int[]> heightDataList = new ArrayList<>();
        for (ColumnData column : columns.values()) {
            if (column.waterLevel != null) {
                heightDataList.add(new int[]{column.x, column.z, column.maxHeight, column.groundLevel != null ? column.groundLevel : -1, column.waterLevel});
            } else {
                heightDataList.add(new int[]{column.x, column.z, column.maxHeight, column.groundLevel != null ? column.groundLevel : -1});
            }
        }

        return heightDataList.toArray(new int[0][]);
    }

    /**
     * Get shape from BlockType (checking modifiers).
     */
    private Integer getShapeFromBlockType(de.mhus.nimbus.generated.types.BlockType blockType) {
        if (blockType.getModifiers() == null || blockType.getModifiers().isEmpty()) {
            return null;
        }
        // Get first modifier (usually status 0)
        var modifier = blockType.getModifiers().get(0);
        if (modifier == null || modifier.getVisibility() == null) {
            return null;
        }
        return modifier.getVisibility().getShape();
    }

    /**
     * Check if shape is a water type.
     */
    private boolean isWaterShape(Integer shapeInt) {
        if (shapeInt == null) return false;
        // Check against Shape enum values
        return shapeInt == de.mhus.nimbus.generated.types.Shape.OCEAN.getTsIndex() ||
               shapeInt == de.mhus.nimbus.generated.types.Shape.WATER.getTsIndex() ||
               shapeInt == de.mhus.nimbus.generated.types.Shape.RIVER.getTsIndex() ||
               shapeInt == de.mhus.nimbus.generated.types.Shape.OCEAN_MAELSTROM.getTsIndex() ||
               shapeInt == de.mhus.nimbus.generated.types.Shape.OCEAN_COAST.getTsIndex();
    }

    /**
     * Helper class to track column data during height calculation.
     */
    private static class ColumnData {
        final int x;
        final int z;
        final int maxHeight;
        Integer groundLevel;
        Integer waterLevel;

        ColumnData(int x, int z, int maxHeight) {
            this.x = x;
            this.z = z;
            this.maxHeight = maxHeight;
        }

        void addBlock(Block block) {
            // Track blocks for future use if needed
        }
    }
}
