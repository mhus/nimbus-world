package de.mhus.nimbus.world.shared.layer;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.generated.types.Area;
import de.mhus.nimbus.generated.types.AreaData;
import de.mhus.nimbus.generated.types.Block;
import de.mhus.nimbus.generated.types.BlockTypeType;
import de.mhus.nimbus.generated.types.ChunkData;
import de.mhus.nimbus.generated.types.Vector3Int;
import de.mhus.nimbus.shared.storage.StorageService;
import de.mhus.nimbus.shared.utils.TypeUtil;
import de.mhus.nimbus.world.shared.world.HexMathUtil;
import de.mhus.nimbus.world.shared.world.WBlockType;
import de.mhus.nimbus.world.shared.world.WHexGridService;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

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
    private final WHexGridService hexGridService;

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

        // Initialize canvas: Map<"x,y,z", Block>
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

        // Apply face visibility optimization before converting to ChunkData
        calculateAndApplyFaceVisibility(blockMap, cx, cz, chunkSize, worldId);

        // Convert map to ChunkData
        ChunkData result = new ChunkData();
        result.setCx(cx);
        result.setCz(cz);
        result.setSize(chunkSize);
        result.setBlocks(new ArrayList<>(blockMap.values()));

        // Calculate height data
        Map<String, int[]> heightData = calculateHeightData(worldId, chunkSize, blockMap.values(), layers);
        result.setHeightData(heightData);

        List<AreaData> areaData = calculateAreaData(world, cx, cz);
        result.setA(areaData);

        log.debug("Generated chunk {} from {} layers, {} blocks",
                chunkKey, layers.size(), blockMap.size());

        return Optional.of(result);
    }

    private List<AreaData> calculateAreaData(WWorld world, int cx, int cz) {
        var hexes = HexMathUtil.getHexesForChunk(world, cx, cz);
        var mainHex = HexMathUtil.getDominantHexForChunk(world, cx, cz);
        var mainGridOpt = hexGridService.findByWorldIdAndPosition(
                world.getWorldId(),
                mainHex
        );
        var chunkSize = world.getPublicData().getChunkSize();
        var minX = cx * chunkSize;
        var minZ = cz * chunkSize;
        var maxX = minX + chunkSize - 1;
        var maxZ = minZ + chunkSize - 1;

        List<AreaData> areaDataList = new ArrayList<>();
        for (var hex : hexes) {
            var gridOpt = hexGridService.findByWorldIdAndPosition(
                    world.getWorldId(),
                    hex
            );
            if (gridOpt.isEmpty()) continue;
            var grid = gridOpt.get();
            if (grid.getAreas() == null) continue;
            for (var areaEntry : grid.getAreas().entrySet()) {
                try {
                    var area = TypeUtil.parseArea(areaEntry.getKey());
                    var areaData = deltaArea(area, minX, minZ, maxX, maxZ);
                    if (areaData == null) continue;
                    HashMap<String,String> p = grid.getParameters().entrySet().stream().filter(e -> e.getKey().startsWith("e_"))
                            .map(e -> new AreaEntry(e.getKey().substring(2), e.getValue()))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a,b) -> b, HashMap::new));
                    if (p.isEmpty()) continue;
                    p.put("grid", grid.getPosition());
                    areaData.setP(p);
                    areaDataList.add(areaData);
                } catch (Exception e) {
                    log.warn("Invalid area key in hex grid {}: {}", grid.getPosition(), areaEntry.getKey());
                    continue;
                }
            }
        }
        if (mainGridOpt.isPresent()) {
            var mainGrid = mainGridOpt.get();
            var areaData = new AreaData();
            areaData.setA(TypeUtil.vector3(minX, 0, minZ));
            areaData.setB(TypeUtil.vector3(maxX, 0, maxZ));
            HashMap<String,String> p = new HashMap<>();
            p.put("grid", mainGrid.getPosition());
            if (mainGrid.getPublicData().getTitle() != null)
                p.put("title", mainGrid.getPublicData().getTitle());
            areaData.setP(p);
            if (p.size() > 1) // more then grid
                areaDataList.add(areaData);
        }
        return areaDataList;
    }

    private AreaData deltaArea(Area area, int minX, int minZ, int maxX, int maxZ) {
        var areaMin = area.getPosition();
        var areaSize = area.getSize();
        var areaMaxX = areaMin.getX() + areaSize.getX() - 1;
        var areaMaxZ = areaMin.getZ() + areaSize.getZ() - 1;

        var deltaMinX = Math.max(areaMin.getX(), minX);
        var deltaMinZ = Math.max(areaMin.getZ(), minZ);
        var deltaMaxX = Math.min(areaMaxX, maxX);
        var deltaMaxZ = Math.min(areaMaxZ, maxZ);

        if (deltaMinX > deltaMaxX || deltaMinZ > deltaMaxZ) {
            return null; // No overlap
        }

        AreaData deltaArea = new AreaData();
        // point A
        Vector3Int positionA = new Vector3Int();
        positionA.setX(deltaMinX);
        positionA.setY(0);
        positionA.setZ(deltaMinZ);
        deltaArea.setA(positionA);

        Vector3Int positionB = new Vector3Int();
        positionB.setX(deltaMaxX);
        positionB.setY(0);
        positionB.setZ(deltaMaxZ);
        deltaArea.setB(positionB);

        return deltaArea;
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
                .findByWorldIdAndLayerDataIdAndChunkKey(layer.getWorldId(), layer.getLayerDataId(), chunkKey);

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
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
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
    private Map<String, int[]> calculateHeightData(String worldId, int chunkSize, Collection<Block> blocks, List<WLayer> layers) {
        var worldIdObj = de.mhus.nimbus.shared.types.WorldId.of(worldId).orElseThrow();
        var world = worldService.getByWorldId(worldId).orElseThrow();
        int maxHeight = (int) world.getPublicData().getStop().getY();
        int minHeight = (int) world.getPublicData().getStart().getY();

        // Find base ground layer
        WLayer baseGroundLayer = null;
        for (WLayer layer : layers) {
            if (layer.isBaseGround() && layer.isEnabled()) {
                baseGroundLayer = layer;
                break;
            }
        }

        // Group blocks by column (x, z) using world coordinates
        Map<String, ColumnData> columns = new HashMap<>();

        for (Block block : blocks) {
            if (block.getPosition() == null) continue;

            int worldX = (int) block.getPosition().getX();
            int worldZ = (int) block.getPosition().getZ();
            String columnKey = worldX + "," + worldZ;

            ColumnData column = columns.computeIfAbsent(columnKey, k -> new ColumnData(worldX, worldZ, minHeight, maxHeight));
            column.addBlock(block);

            // Check if this block is in baseGround layer and not water
            // TODO what when more then one groundLayers ... and check for blockTypeType == GROUND ?! is baseGroundLayer obsolate?
            if (baseGroundLayer != null && block.getBlockTypeId() != null) {
                var blockType = blockTypeService.findByBlockId(worldIdObj, block.getBlockTypeId());
                if (blockType.isPresent() && blockType.get().getPublicData() != null) {
                    Integer shapeInt = getShapeFromBlockType(blockType.get().getPublicData()); // TODO deprecated !!!! use blockTypeType == GROUND
                    boolean isWater = isWaterShape(shapeInt, blockType.get().getPublicData().getType());

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

        // Convert to Map with key format "worldX,worldZ" (world coordinates)
        // Format: [maxHeight, minHeight, groundLevel, waterLevel?]
        Map<String, int[]> heightDataMap = new HashMap<>();
        for (ColumnData column : columns.values()) {
            String key = column.x + "," + column.z;
            if (column.waterLevel != null) {
                heightDataMap.put(key, new int[]{column.maxHeight, column.minHeight, column.groundLevel != null ? column.groundLevel : -1, column.waterLevel});
            } else {
                heightDataMap.put(key, new int[]{column.maxHeight, column.minHeight, column.groundLevel != null ? column.groundLevel : -1});
            }
        }

        return heightDataMap;
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
    private boolean isWaterShape(Integer shapeInt, BlockTypeType type) {
        if (shapeInt == null) return false;
        if (type == BlockTypeType.WATER || type == BlockTypeType.LAVA) return true; // lava is water too :)
        // Check against Shape enum values
        // TODO deprecated
        return shapeInt == de.mhus.nimbus.generated.types.Shape.OCEAN.getTsIndex() ||
               shapeInt == de.mhus.nimbus.generated.types.Shape.WATER.getTsIndex() ||
               shapeInt == de.mhus.nimbus.generated.types.Shape.RIVER.getTsIndex() ||
               shapeInt == de.mhus.nimbus.generated.types.Shape.OCEAN_MAELSTROM.getTsIndex() ||
               shapeInt == de.mhus.nimbus.generated.types.Shape.OCEAN_COAST.getTsIndex();
    }

    /**
     * Calculate and apply face visibility for GROUND, PATH and BLOCK type blocks in the chunk.
     * Sets faceVisibility to hide non-visible faces (faces that have GROUND, PATH or BLOCK type neighbors).
     * Only GROUND, PATH and BLOCK type blocks are processed - other block types are skipped.
     * Only checks within the chunk - chunk boundaries are treated as missing blocks (faces visible).
     * Skips blocks that already have faceVisibility set with the FIXED bit (64).
     *
     * Rules:
     * - Only GROUND, PATH and BLOCK blocks are optimized (checked via WBlockType.publicData.type)
     * - A face is hidden only if there is a GROUND, PATH or BLOCK neighbor block at that position
     * - Other block types (trees, items, etc.) remain unchanged
     *
     * FaceFlag bits (set bit = visible face):
     * TOP = 1, BOTTOM = 2, LEFT = 4, RIGHT = 8, FRONT = 16, BACK = 32, FIXED = 64
     *
     * @param blockMap Map of all blocks in the chunk (key: "x,y,z", value: Block)
     * @param cx Chunk X coordinate
     * @param cz Chunk Z coordinate
     * @param chunkSize Chunk size
     * @param worldId World identifier for block type lookup
     */
    private void calculateAndApplyFaceVisibility(Map<String, Block> blockMap, int cx, int cz, int chunkSize, String worldId) {
        int chunkMinX = cx * chunkSize;
        int chunkMaxX = chunkMinX + chunkSize - 1;
        int chunkMinZ = cz * chunkSize;
        int chunkMaxZ = chunkMinZ + chunkSize - 1;

        // BlockType cache for this chunk to avoid repeated lookups
        Map<String, WBlockType> blockTypeCache = new HashMap<>();

        // Parse worldId for block type lookup
        de.mhus.nimbus.shared.types.WorldId wid = de.mhus.nimbus.shared.types.WorldId.of(worldId).orElse(null);
        if (wid == null) {
            log.warn("Invalid worldId for face visibility calculation: {}", worldId);
            return;
        }

        int processedCount = 0;
        int skippedFixedCount = 0;
        int skippedNonGroundCount = 0;

        for (Block block : blockMap.values()) {
            if (block == null || block.getPosition() == null || block.getBlockTypeId() == null) {
                continue;
            }

            // Check if faceVisibility is already set with FIXED bit (64)
            Integer existingFaceVis = block.getFaceVisibility();
            if (existingFaceVis != null && (existingFaceVis & 64) != 0) {
                // FIXED bit is set, don't modify
                skippedFixedCount++;
                continue;
            }

            // Only process GROUND, PATH or BLOCK type blocks - get or cache block type
            String blockTypeId = block.getBlockTypeId();
            WBlockType blockType = blockTypeCache.computeIfAbsent(blockTypeId, id ->
                    blockTypeService.findByBlockId(wid, id).orElse(null)
            );

            if (blockType == null || blockType.getPublicData() == null || blockType.getPublicData().getType() == null) {
                // Can't determine type, skip
                skippedNonGroundCount++;
                continue;
            }

            // Only process GROUND, PATH or BLOCK blocks
            de.mhus.nimbus.generated.types.BlockTypeType blockTypeType = blockType.getPublicData().getType();
            if (blockTypeType != de.mhus.nimbus.generated.types.BlockTypeType.GROUND &&
                blockTypeType != de.mhus.nimbus.generated.types.BlockTypeType.PATH &&
                blockTypeType != de.mhus.nimbus.generated.types.BlockTypeType.BLOCK) {
                skippedNonGroundCount++;
                continue;
            }

            int x = block.getPosition().getX();
            int y = block.getPosition().getY();
            int z = block.getPosition().getZ();

            // FaceFlag bits (set bit = visible face):
            // TOP = 1, BOTTOM = 2, LEFT = 4, RIGHT = 8, FRONT = 16, BACK = 32
            int faceVisibility = 0;

            // Check each face for GROUND, PATH or BLOCK type neighbors
            // TOP (y+1): visible if no GROUND/PATH/BLOCK neighbor above
            String topKey = blockKey(x, y + 1, z);
            if (!hasGroundBlockAt(blockMap, topKey, blockTypeCache, wid)) {
                faceVisibility |= 1;  // TOP visible
            }

            // BOTTOM (y-1): visible if no GROUND/PATH/BLOCK neighbor below
            String bottomKey = blockKey(x, y - 1, z);
            if (!hasGroundBlockAt(blockMap, bottomKey, blockTypeCache, wid)) {
                faceVisibility |= 2;  // BOTTOM visible
            }

            // LEFT / West (x-1): visible if no GROUND/PATH/BLOCK neighbor or at chunk boundary
            String leftKey = blockKey(x - 1, y, z);
            if (!hasGroundBlockAt(blockMap, leftKey, blockTypeCache, wid) || x == chunkMinX) {
                faceVisibility |= 4;  // LEFT visible
            }

            // RIGHT / East (x+1): visible if no GROUND/PATH/BLOCK neighbor or at chunk boundary
            String rightKey = blockKey(x + 1, y, z);
            if (!hasGroundBlockAt(blockMap, rightKey, blockTypeCache, wid) || x == chunkMaxX) {
                faceVisibility |= 8;  // RIGHT visible
            }

            // FRONT (South): visible if no GROUND/PATH/BLOCK neighbor at North (z+1) or at chunk boundary (swapped)
            String northKey = blockKey(x, y, z + 1);
            if (!hasGroundBlockAt(blockMap, northKey, blockTypeCache, wid) || z == chunkMaxZ) {
                faceVisibility |= 16;  // FRONT visible
            }

            // BACK (North): visible if no GROUND/PATH/BLOCK neighbor at South (z-1) or at chunk boundary (swapped)
            String southKey = blockKey(x, y, z - 1);
            if (!hasGroundBlockAt(blockMap, southKey, blockTypeCache, wid) || z == chunkMinZ) {
                faceVisibility |= 32;  // BACK visible
            }

            // Set faceVisibility on block
            block.setFaceVisibility(faceVisibility);
            processedCount++;
        }

        log.debug("Applied face visibility to chunk {}:{} - processed: {}, skipped (fixed): {}, skipped (non-ground): {}, cached types: {}",
                cx, cz, processedCount, skippedFixedCount, skippedNonGroundCount, blockTypeCache.size());
    }

    /**
     * Check if there is a GROUND, PATH or BLOCK type block at the given position.
     * Uses cache to avoid repeated block type lookups.
     *
     * @param blockMap Map of all blocks in the chunk
     * @param blockKey Position key "x,y,z"
     * @param blockTypeCache Cache for block types
     * @param wid World identifier
     * @return true if a GROUND, PATH or BLOCK type block exists at this position, false otherwise
     */
    private boolean hasGroundBlockAt(Map<String, Block> blockMap, String blockKey,
                                      Map<String, WBlockType> blockTypeCache,
                                      de.mhus.nimbus.shared.types.WorldId wid) {
        Block neighbor = blockMap.get(blockKey);
        if (neighbor == null || neighbor.getBlockTypeId() == null) {
            return false;
        }

        String blockTypeId = neighbor.getBlockTypeId();

        // Get or cache block type
        WBlockType blockType = blockTypeCache.computeIfAbsent(blockTypeId, id ->
                blockTypeService.findByBlockId(wid, id).orElse(null)
        );

        if (blockType == null || blockType.getPublicData() == null || blockType.getPublicData().getType() == null) {
            return false;
        }

        // Check if block type is GROUND, PATH or BLOCK
        de.mhus.nimbus.generated.types.BlockTypeType blockTypeType = blockType.getPublicData().getType();
        return blockTypeType == de.mhus.nimbus.generated.types.BlockTypeType.GROUND ||
               blockTypeType == de.mhus.nimbus.generated.types.BlockTypeType.PATH ||
               blockTypeType == de.mhus.nimbus.generated.types.BlockTypeType.BLOCK;
    }

    /**
     * Generate block key from coordinates.
     */
    private String blockKey(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    /**
     * Helper class to track column data during height calculation.
     */
    private static class ColumnData {
        final int x;
        final int z;
        final int maxHeight;
        final int minHeight;
        Integer groundLevel;
        Integer waterLevel;

        ColumnData(int x, int z, int minHeight, int maxHeight) {
            this.x = x;
            this.z = z;
            this.minHeight = minHeight;
            this.maxHeight = maxHeight;
        }

        void addBlock(Block block) {
            // Track blocks for future use if needed
        }
    }

    private static class AreaEntry implements Map.Entry<String,String> {
        private final String key;
        private String value;

        public AreaEntry(String key, String value) {
            this.key = key;
            this.value = value;
        }

        @Override
        public String getKey() {
            return key;
        }

        @Override
        public String getValue() {
            return value;
        }

        @Override
        public String setValue(String value) {
            String old = this.value;
            this.value = value;
            return old;
        }
    }

}
