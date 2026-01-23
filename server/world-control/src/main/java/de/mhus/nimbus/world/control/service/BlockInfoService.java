package de.mhus.nimbus.world.control.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.mhus.nimbus.generated.types.Block;
import de.mhus.nimbus.generated.types.ChunkData;
import de.mhus.nimbus.generated.types.Vector3Int;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.layer.WLayer;
import de.mhus.nimbus.world.shared.layer.WLayerService;
import de.mhus.nimbus.world.shared.redis.WorldRedisService;
import de.mhus.nimbus.world.shared.session.EditState;
import de.mhus.nimbus.world.shared.world.WChunkService;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Service for loading block information with layer metadata.
 * Handles edit mode, layer selection, and Redis overlays.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BlockInfoService {

    @Lazy
    @Autowired
    private EditService editService;
    private final WLayerService layerService;
    private final WChunkService chunkService;
    private final WorldRedisService redisService;
    private final ObjectMapper objectMapper;
    private final WWorldService worldService;
    private final de.mhus.nimbus.world.shared.layer.WEditCacheService editCacheService;

    /**
     * Load block info with layer metadata.
     *
     * @param worldId   World identifier
     * @param sessionId Session identifier (optional - if null, loads from WChunk only)
     * @param x         Block X coordinate
     * @param y         Block Y coordinate
     * @param z         Block Z coordinate
     * @return BlockInfo as Map (can be serialized to JSON directly)
     */
    @Transactional(readOnly = true)
    public Map<String, Object> loadBlockInfo(String worldId, String sessionId, int x, int y, int z) {
        Map<String, Object> blockInfo = new HashMap<>();

        // Position
        blockInfo.put("position", Map.of("x", x, "y", y, "z", z));

        // Check edit mode and layer selection
        boolean editMode = false;
        String layerName = null;
        String group = null;

        if (sessionId != null && !sessionId.isBlank()) {
            EditState state = editService.getEditState(worldId, sessionId);
            editMode = state.isEditMode();
            layerName = state.getSelectedLayer();
            group = state.getSelectedGroup();
        }

        Block block = null;
        boolean readOnly = true;
        String groupName = null;

        // Load block based on edit mode and layer selection
        if (editMode && layerName != null && !layerName.isBlank()) {
            // EDIT MODE with selected layer

            // Get layerDataId for WEditCache lookup
            Optional<WLayer> layerOpt = layerService.findLayer(worldId, layerName);
            String layerDataId = layerOpt.map(WLayer::getLayerDataId).orElse(null);

            if (layerDataId != null) {
                // 1. Check WEditCache first (edited but not committed)
                block = loadBlockFromEditCache(worldId, layerDataId, x, y, z);

                if (block != null) {
                    log.debug("Loaded block from WEditCache: pos=({},{},{})", x, y, z);
                    readOnly = false;
                }
            }

            if (block == null) {
                // 2. Load from selected layer
                block = loadBlockFromLayer(worldId, layerName, x, y, z);
                if (block != null) {
                    log.debug("Loaded block from layer: layer={} pos=({},{},{})", layerName, x, y, z);
                    readOnly = false;

                    // Get group title from WLayerModel (groups are now in model, not layer)
                    if (layerOpt.isPresent() && group != null && !group.isEmpty()) {
                        WLayer layer = layerOpt.get();
                        // For MODEL layers, would need to load WLayerModel to get groups
                        // For now, skip group title resolution (groups moved to WLayerModel)
                        // TODO: Load WLayerModel if needed for group resolution
                    }
                }
            }
        }

        // Fallback: Load from WChunk (merged result)
        if (block == null) {
            block = loadBlockFromChunk(worldId, x, y, z);
            readOnly = Strings.isBlank(layerName); // will copy it into layer
            log.debug("Loaded block from WChunk: pos=({},{},{}) readOnly={} layerName={}", x, y, z, readOnly, layerName);
        }

        // Default to air if still not found
        if (block == null) {
            block = createAirBlock(x, y, z);
            log.debug("Block not found, using air: pos=({},{},{})", x, y, z);
        }

        // Build BlockInfo response
        blockInfo.put("layer", layerName);
        blockInfo.put("group", group);
        blockInfo.put("groupName", groupName);
        blockInfo.put("block", block);
        blockInfo.put("readOnly", readOnly);

        return blockInfo;
    }

    /**
     * Load block from WEditCache (edited but not committed).
     *
     * @param worldId World identifier
     * @param layerDataId Layer data identifier
     * @param x X coordinate
     * @param y Y coordinate
     * @param z Z coordinate
     * @return Block from WEditCache or null if not found
     */
    private Block loadBlockFromEditCache(String worldId, String layerDataId, int x, int y, int z) {
        try {
            // Query WEditCache for this position
            Optional<de.mhus.nimbus.world.shared.layer.WEditCache> cacheOpt =
                    editCacheService.findByCoordinates(worldId, layerDataId, x, y, z);

            if (cacheOpt.isEmpty()) {
                return null;
            }

            de.mhus.nimbus.world.shared.layer.WEditCache cache = cacheOpt.get();
            de.mhus.nimbus.world.shared.layer.LayerBlock layerBlock = cache.getBlock();

            if (layerBlock == null || layerBlock.getBlock() == null) {
                log.warn("WEditCache entry has no block data: worldId={} layerDataId={} pos=({},{},{})",
                        worldId, layerDataId, x, y, z);
                return null;
            }

            Block block = layerBlock.getBlock();

            // Verify Y coordinate matches (WEditCache now uses X,Y,Z as key)
            if (block.getPosition() != null && block.getPosition().getY() != y) {
                log.debug("WEditCache block Y coordinate mismatch: expected={} actual={}",
                        y, block.getPosition().getY());
                return null;
            }

            log.debug("Found block in WEditCache: pos=({},{},{}) blockTypeId={}",
                    x, y, z, block.getBlockTypeId());
            return block;

        } catch (Exception e) {
            log.warn("Failed to load block from WEditCache at pos ({},{},{}): {}",
                    x, y, z, e.getMessage());
            return null;
        }
    }

    /**
     * Load block from selected layer.
     */
    private Block loadBlockFromLayer(String worldId, String layerName, int x, int y, int z) {
        try {
            // 1. Load WLayer entity
            Optional<WLayer> layerOpt = layerService.findLayer(worldId, layerName);
            if (layerOpt.isEmpty()) {
                log.debug("Layer not found: {}", layerName);
                return null;
            }

            WLayer layer = layerOpt.get();
            String layerDataId = layer.getLayerDataId();

            // 2. Load block based on layer type
            switch (layer.getLayerType()) {
                case GROUND:
                    return loadBlockFromTerrainLayer(worldId, layerDataId, x, y, z);

                case MODEL:
                    return loadBlockFromModelLayer(worldId, layerDataId, layer, x, y, z);

                default:
                    log.warn("Unknown layer type: {}", layer.getLayerType());
                    return null;
            }

        } catch (Exception e) {
            log.warn("Failed to load block from layer {}: {}", layerName, e.getMessage());
            return null;
        }
    }

    /**
     * Load block from TERRAIN layer.
     */
    private Block loadBlockFromTerrainLayer(String worldId, String layerDataId, int x, int y, int z) {
        // Get world and calculate chunk key
        WWorld world = worldService.getByWorldId(worldId).orElse(null);
        if (world == null) {
            log.warn("World not found: {}", worldId);
            return null;
        }

        String chunkKey = world.getChunkKey(x, z);

        // Load LayerChunkData
        Optional<de.mhus.nimbus.world.shared.layer.LayerChunkData> chunkDataOpt =
                layerService.loadTerrainChunk(worldId, layerDataId, chunkKey);

        if (chunkDataOpt.isEmpty()) {
            return null;
        }

        de.mhus.nimbus.world.shared.layer.LayerChunkData chunkData = chunkDataOpt.get();

        // Find block at position
        if (chunkData.getBlocks() != null) {
            for (de.mhus.nimbus.world.shared.layer.LayerBlock layerBlock : chunkData.getBlocks()) {
                Block block = layerBlock.getBlock();
                if (block != null && block.getPosition() != null) {
                    Vector3Int pos = block.getPosition();
                    if (pos.getX() == x && pos.getY() == y && pos.getZ() == z) {
                        return block;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Load block from MODEL layer.
     */
    private Block loadBlockFromModelLayer(String worldId, String layerDataId, WLayer layer, int x, int y, int z) {
        // Load model content
        Optional<de.mhus.nimbus.world.shared.layer.WLayerModel> modelOpt =
                layerService.loadModelById(layerDataId);

        if (modelOpt.isEmpty()) {
            return null;
        }

        de.mhus.nimbus.world.shared.layer.WLayerModel model = modelOpt.get();

        // Calculate relative position from mount point (now in model, not layer)
        int mountX = model.getMountX();
        int mountY = model.getMountY();
        int mountZ = model.getMountZ();

        int relativeX = x - mountX;
        int relativeY = y - mountY;
        int relativeZ = z - mountZ;

        // Find block in model content
        if (model.getContent() != null) {
            for (de.mhus.nimbus.world.shared.layer.LayerBlock layerBlock : model.getContent()) {
                Block block = layerBlock.getBlock();
                if (block != null && block.getPosition() != null) {
                    Vector3Int pos = block.getPosition();
                    if ((int) pos.getX() == relativeX && (int) pos.getY() == relativeY && (int) pos.getZ() == relativeZ) {
                        // Create new block with absolute position
                        Block absoluteBlock = Block.builder()
                                .position(Vector3Int.builder().x(x).y(y).z(z).build())
                                .blockTypeId(block.getBlockTypeId())
                                .offsets(block.getOffsets())
                                .status(block.getStatus())
                                .modifiers(block.getModifiers())
                                .metadata(block.getMetadata())
                                .build();
                        return absoluteBlock;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Load block from merged WChunk (final rendered result).
     */
    private Block loadBlockFromChunk(String worldId, int x, int y, int z) {
        // Get world and calculate chunk key
        WWorld world = worldService.getByWorldId(worldId).orElse(null);
        if (world == null) {
            log.warn("World not found: {}", worldId);
            return null;
        }

        String chunkKey = world.getChunkKey(x, z);

        WorldId wid = WorldId.of(worldId).orElse(null);
        if (wid == null) {
            log.warn("Invalid worldId: {}", worldId);
            return null;
        }

        Optional<ChunkData> chunkDataOpt = chunkService.loadChunkData(wid, chunkKey, false);
        if (chunkDataOpt.isEmpty()) {
            log.debug("Chunk not found: {}", chunkKey);
            return null;
        }

        ChunkData chunkData = chunkDataOpt.get();

        // Find block at position (Vector3 uses doubles, need int comparison)
        if (chunkData.getBlocks() != null) {
            for (Block block : chunkData.getBlocks()) {
                Vector3Int pos = block.getPosition();
                if (pos != null &&
                    pos.getX() == x &&
                    pos.getY() == y &&
                    pos.getZ() == z) {
                    log.debug("Found block in chunk: pos=({},{},{}) blockTypeId={}",
                            x, y, z, block.getBlockTypeId());
                    return block;
                }
            }
        }

        log.debug("Block not found in chunk: pos=({},{},{}) totalBlocks={}",
                x, y, z, chunkData.getBlocks() != null ? chunkData.getBlocks().size() : 0);
        return null;
    }

    /**
     * Create air block at position.
     */
    private Block createAirBlock(int x, int y, int z) {
        Vector3Int position = Vector3Int.builder()
                .x(x)
                .y(y)
                .z(z)
                .build();

        Block block = Block.builder()
                .blockTypeId("air")
                .position(position)
                .build();

        return block;
    }
}
