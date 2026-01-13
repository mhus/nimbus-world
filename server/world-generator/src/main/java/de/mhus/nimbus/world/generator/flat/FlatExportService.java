package de.mhus.nimbus.world.generator.flat;

import de.mhus.nimbus.generated.types.Block;
import de.mhus.nimbus.generated.types.BlockTypeType;
import de.mhus.nimbus.generated.types.Vector3Int;
import de.mhus.nimbus.shared.types.BlockDef;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.generator.WFlat;
import de.mhus.nimbus.world.shared.generator.WFlatService;
import de.mhus.nimbus.world.shared.layer.LayerBlock;
import de.mhus.nimbus.world.shared.layer.LayerChunkData;
import de.mhus.nimbus.world.shared.layer.LayerType;
import de.mhus.nimbus.world.shared.layer.WDirtyChunkService;
import de.mhus.nimbus.world.shared.layer.WLayer;
import de.mhus.nimbus.world.shared.layer.WLayerService;
import de.mhus.nimbus.world.shared.world.BlockUtil;
import de.mhus.nimbus.world.shared.world.WBlockType;
import de.mhus.nimbus.world.shared.world.WBlockTypeService;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service for exporting WFlat to WLayer GROUND type.
 * Handles conversion from flat terrain data to layer chunks.
 * Marks modified chunks as dirty for regeneration.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FlatExportService {

    private final WFlatService flatService;
    private final WLayerService layerService;
    private final WWorldService worldService;
    private final WDirtyChunkService dirtyChunkService;
    private final WBlockTypeService blockTypeService;

    /**
     * Export WFlat to a WLayer of type GROUND.
     * Only exports columns that are set (not 0/NOT_SET and not 255).
     * Material 255 is treated like NOT_SET (not exported, not deleted on target).
     * Fills columns down to lowest sibling level to avoid holes.
     *
     * @param flatId Flat identifier (database ID)
     * @param worldId World identifier
     * @param layerName Name of the target GROUND layer
     * @param smoothCorners If true, smooths corners of top GROUND blocks based on neighbors
     * @param optimizeFaces If true, sets faceVisibility to hide non-visible faces
     * @return Number of exported columns
     * @throws IllegalArgumentException if flat, world, or layer not found, or layer is not GROUND type
     */
    public int exportToLayer(String flatId, String worldId, String layerName, boolean smoothCorners, boolean optimizeFaces) {
        log.info("Exporting flat to layer: flatId={}, worldId={}, layerName={}, smoothCorners={}, optimizeFaces={}",
                flatId, worldId, layerName, smoothCorners, optimizeFaces);

        // Load flat
        WFlat flat = flatService.findById(flatId)
                .orElseThrow(() -> new IllegalArgumentException("Flat not found: " + flatId));

        // Load world
        WWorld world = worldService.getByWorldId(worldId)
                .orElseThrow(() -> new IllegalArgumentException("World not found: " + worldId));

        // Load layer
        WLayer layer = layerService.findByWorldIdAndName(worldId, layerName)
                .orElseThrow(() -> new IllegalArgumentException("Layer not found: " + layerName));

        // Validate layer type
        if (layer.getLayerType() != LayerType.GROUND) {
            throw new IllegalArgumentException("Layer must be of type GROUND, but is: " + layer.getLayerType());
        }

        int chunkSize = world.getPublicData().getChunkSize();
        String layerDataId = layer.getLayerDataId();

        // Map to store modified chunks: chunkKey -> LayerChunkData
        Map<String, LayerChunkData> modifiedChunks = new HashMap<>();

        // BlockType cache for this export (to avoid repeated lookups)
        Map<String, WBlockType> blockTypeCache = new HashMap<>();
        WorldId wid = WorldId.of(worldId).orElse(null);

        int exportedColumns = 0;
        int skippedColumns = 0;

        // Iterate over all columns in the flat
        for (int localX = 0; localX < flat.getSizeX(); localX++) {
            for (int localZ = 0; localZ < flat.getSizeZ(); localZ++) {

                // Calculate world coordinates
                int worldX = flat.getMountX() + localX;
                int worldZ = flat.getMountZ() + localZ;

                // Calculate chunk coordinates
                int chunkX = world.getChunkX(worldX);
                int chunkZ = world.getChunkZ(worldZ);
                String chunkKey = BlockUtil.toChunkKey(chunkX, chunkZ);

                // Get or create chunk data
                LayerChunkData chunkData = modifiedChunks.computeIfAbsent(chunkKey, key -> {
                    // Try to load existing chunk
                    Optional<LayerChunkData> existing = layerService.loadTerrainChunk(layerDataId, key);
                    if (existing.isPresent()) {
                        return existing.get();
                    } else {
                        // Create new chunk
                        return LayerChunkData.builder()
                                .cx(chunkX)
                                .cz(chunkZ)
                                .blocks(new ArrayList<>())
                                .build();
                    }
                });

                // Check if column is set or has material 255 (treated like NOT_SET)
                int columnMaterial = flat.getColumn(localX, localZ);
                if (!flat.isColumnSet(localX, localZ) || columnMaterial == 255) {
                    // NOT_SET or Material 255: Keep existing blocks, but fill down if neighbors are lower
                    handleNotSetColumn(chunkData, worldX, worldZ, flat, localX, localZ, world, worldId);
                    skippedColumns++;
                    continue;
                }

                // Column is set - process normally
                // Get level from flat
                int level = flat.getLevel(localX, localZ);

                // Get block type from column definition
                WFlat.MaterialDefinition columnDef = flat.getColumnMaterial(localX, localZ);
                if (columnDef == null) {
                    log.warn("Column definition not found for column at ({}, {}), skipping", localX, localZ);
                    skippedColumns++;
                    continue;
                }

                // Delete all existing blocks at this column
                deleteColumnBlocks(chunkData, worldX, worldZ);

                // Find lowest sibling level to avoid holes
                int lowestSiblingLevel = findLowestSiblingLevel(flat, localX, localZ, chunkData, world);

                // Fill column from level down to lowestSiblingLevel
                fillColumn(chunkData, worldX, worldZ, level, lowestSiblingLevel, columnDef, flat,
                        smoothCorners, optimizeFaces, blockTypeCache, wid, localX, localZ);

                exportedColumns++;
            }
        }

        // Save all modified chunks
        for (Map.Entry<String, LayerChunkData> entry : modifiedChunks.entrySet()) {
            String chunkKey = entry.getKey();
            LayerChunkData chunkData = entry.getValue();
            layerService.saveTerrainChunk(worldId, layerDataId, chunkKey, chunkData);
        }

        // Mark all modified chunks as dirty for regeneration
        if (!modifiedChunks.isEmpty()) {
            List<String> chunkKeys = new ArrayList<>(modifiedChunks.keySet());
            dirtyChunkService.markChunksDirty(worldId, chunkKeys, "Flat export: " + flatId);
            log.info("Marked {} chunks as dirty for regeneration", chunkKeys.size());
        }

        log.info("Export complete: flatId={}, exported={} columns, skipped={} columns, modified={} chunks",
                flatId, exportedColumns, skippedColumns, modifiedChunks.size());

        return exportedColumns;
    }

    /**
     * Handle NOT_SET column (material 0 or 255): Keep top GROUND block, delete all blocks below, fill down if neighbors are lower.
     * This prevents gaps between old high blocks and new lower blocks.
     * Only considers GROUND type blocks for filling.
     * Material 255 is treated the same as NOT_SET (material 0).
     */
    private void handleNotSetColumn(LayerChunkData chunkData, int worldX, int worldZ,
                                    WFlat flat, int localX, int localZ, WWorld world, String worldId) {
        // Find highest existing GROUND type block at this position
        String topBlockDefString = null;
        int existingLevel = findHighestGroundBlockAtPosition(chunkData, worldX, worldZ, worldId);
        if (existingLevel == -1) {
            log.debug("No GROUND type blocks found at ({},{}) for NOT_SET column", worldX, worldZ);
            // exidently no GROUND blocks fake it:
            existingLevel = flat.getLevel(localX, localZ);
            topBlockDefString = flat.getMaterial(FlatMaterialService.BEDROCK).getBlockDef();
        }

        // Get the block type from the highest existing GROUND block BEFORE deleting
        if (Strings.isBlank(topBlockDefString)) {
            topBlockDefString = getBlockDefAtPosition(chunkData, worldX, worldZ, existingLevel);
        }
        if (Strings.isBlank(topBlockDefString)) {
            topBlockDefString = flat.getMaterial(FlatMaterialService.BEDROCK).getBlockDef();
        }
        if (Strings.isBlank(topBlockDefString)) {
            log.debug("Could not determine top block definition for NOT_SET column at ({},{}), using default", worldX, worldZ);
            topBlockDefString = "n:b";
        }

        // Parse block definition
        Optional<BlockDef> topBlockDefOpt = BlockDef.of(topBlockDefString);
        if (topBlockDefOpt.isEmpty()) {
            log.warn("Invalid block definition for NOT_SET column: {}", topBlockDefString);
            return;
        }
        BlockDef topBlockDef = topBlockDefOpt.get();
        if ("5000".equals(topBlockDef.getBlockTypeId())) { // TODO hack for water
            log.debug("Top block is water for NOT_SET column at ({},{}), using bedrock instead", worldX, worldZ);
            topBlockDef = BlockDef.of(flat.getMaterial(FlatMaterialService.BEDROCK).getBlockDef()).orElseThrow();
        }

        // Delete all blocks BELOW the top block (keep only the top block)
        deleteBlocksBelowLevel(chunkData, worldX, worldZ, existingLevel);

        // Find lowest sibling level (from neighbors)
        int lowestSiblingLevel = findLowestSiblingLevel(flat, localX, localZ, chunkData, world);

        // If neighbors are lower, fill down to avoid gaps
        if (lowestSiblingLevel < existingLevel) {
            // Determine which sides need filling (which neighbors are lowest)
            int faceVisibilityForFill = calculateNotSetFaceVisibility(flat, localX, localZ, existingLevel);

            // Fill down from existingLevel-1 to lowestSiblingLevel
            for (int y = existingLevel - 1; y >= lowestSiblingLevel; y--) {
                // Create new block with same type as top block
                Block block = Block.builder()
                        .position(Vector3Int.builder()
                                .x(worldX)
                                .y(y)
                                .z(worldZ)
                                .build())
                        .build();

                topBlockDef.fillBlock(block);

                // Set face visibility: only show faces toward the flat (opposite of fill direction)
                block.setFaceVisibility(faceVisibilityForFill);

                // Add to chunk
                LayerBlock layerBlock = LayerBlock.builder()
                        .block(block)
                        .build();
                chunkData.getBlocks().add(layerBlock);
            }

            log.trace("Filled NOT_SET column at ({},{}) from {} down to {} with block type {} and faceVisibility={}",
                     worldX, worldZ, existingLevel - 1, lowestSiblingLevel, topBlockDefString, faceVisibilityForFill);
        }
    }

    /**
     * Get block definition string at a specific position.
     */
    private String getBlockDefAtPosition(LayerChunkData chunkData, int worldX, int worldZ, int y) {
        for (LayerBlock layerBlock : chunkData.getBlocks()) {
            Block block = layerBlock.getBlock();
            if (block == null || block.getPosition() == null) {
                continue;
            }
            Vector3Int pos = block.getPosition();
            if (pos.getX() == worldX && pos.getY() == y && pos.getZ() == worldZ) {
                // Reconstruct blockDef string from block
                return reconstructBlockDef(block);
            }
        }
        return null;
    }

    /**
     * Reconstruct a blockDef string from a Block.
     * Format: blockTypeId@s:state@r:rx,ry@l:level@f:faceVisibility
     */
    private String reconstructBlockDef(Block block) {
        StringBuilder sb = new StringBuilder();
        sb.append(block.getBlockTypeId());

        Integer status = block.getStatus();
        if (status != null && status != 0) {
            sb.append("@s:").append(status);
        }

        if (block.getRotation() != null) {
            sb.append("@r:").append(block.getRotation().getX()).append(",").append(block.getRotation().getY());
        }

        if (block.getLevel() != null) {
            sb.append("@l:").append(block.getLevel());
        }

        if (block.getFaceVisibility() != null) {
            sb.append("@f:").append(block.getFaceVisibility());
        }

        return sb.toString();
    }

    /**
     * Check if a block exists at a specific position.
     */
    private boolean hasBlockAtPosition(LayerChunkData chunkData, int worldX, int y, int worldZ) {
        for (LayerBlock layerBlock : chunkData.getBlocks()) {
            Block block = layerBlock.getBlock();
            if (block == null || block.getPosition() == null) {
                continue;
            }
            Vector3Int pos = block.getPosition();
            if (pos.getX() == worldX && pos.getY() == y && pos.getZ() == worldZ) {
                return true;
            }
        }
        return false;
    }

    /**
     * Delete all blocks below a specific level in a column.
     * Keeps blocks at and above the specified level.
     */
    private void deleteBlocksBelowLevel(LayerChunkData chunkData, int worldX, int worldZ, int keepLevel) {
        chunkData.getBlocks().removeIf(layerBlock -> {
            Block block = layerBlock.getBlock();
            if (block == null || block.getPosition() == null) {
                return false;
            }
            Vector3Int pos = block.getPosition();
            // Remove if same X,Z and Y < keepLevel
            return pos.getX() == worldX && pos.getZ() == worldZ && pos.getY() < keepLevel;
        });
    }

    /**
     * Delete all blocks in a column (at specific X,Z for all Y levels).
     */
    private void deleteColumnBlocks(LayerChunkData chunkData, int worldX, int worldZ) {
        chunkData.getBlocks().removeIf(layerBlock -> {
            Block block = layerBlock.getBlock();
            if (block == null || block.getPosition() == null) {
                return false;
            }
            Vector3Int pos = block.getPosition();
            return pos.getX() == worldX && pos.getZ() == worldZ;
        });
    }

    /**
     * Find lowest level of sibling (neighboring) columns to avoid creating holes.
     * Checks all 8 neighboring positions in the flat.
     *
     * @return Lowest sibling level, or 0 if no neighbors found
     */
    private int findLowestSiblingLevel(WFlat flat, int localX, int localZ,
                                       LayerChunkData chunkData, WWorld world) {
        int lowestLevel = Integer.MAX_VALUE;
        boolean foundSibling = false;

        // Check all 8 neighbors
        int[][] offsets = {
                {-1, -1}, {0, -1}, {1, -1},
                {-1, 0},           {1, 0},
                {-1, 1},  {0, 1},  {1, 1}
        };

        for (int[] offset : offsets) {
            int neighborX = localX + offset[0];
            int neighborZ = localZ + offset[1];

            // Check if neighbor is within flat bounds
            if (neighborX >= 0 && neighborX < flat.getSizeX() &&
                neighborZ >= 0 && neighborZ < flat.getSizeZ()) {

                // Check if neighbor column is defined
                if (flat.isColumnSet(neighborX, neighborZ)) {
                    int neighborLevel = flat.getLevel(neighborX, neighborZ);
                    if (neighborLevel < lowestLevel) {
                        lowestLevel = neighborLevel;
                        foundSibling = true;
                    }
                }
            } else {
                // Check in existing chunk data (outside flat bounds)
                int worldX = flat.getMountX() + neighborX;
                int worldZ = flat.getMountZ() + neighborZ;
                int existingLevel = findHighestBlockAtPosition(chunkData, worldX, worldZ);
                if (existingLevel != -1 && existingLevel < lowestLevel) {
                    lowestLevel = existingLevel;
                    foundSibling = true;
                }
            }
        }

        return foundSibling ? lowestLevel : 0;
    }

    /**
     * Find highest block Y at a specific X,Z position in chunk data.
     *
     * @return Highest Y coordinate, or -1 if no block found
     */
    private int findHighestBlockAtPosition(LayerChunkData chunkData, int worldX, int worldZ) {
        int highestY = -1;

        for (LayerBlock layerBlock : chunkData.getBlocks()) {
            Block block = layerBlock.getBlock();
            if (block == null || block.getPosition() == null) {
                continue;
            }

            Vector3Int pos = block.getPosition();
            if (pos.getX() == worldX && pos.getZ() == worldZ) {
                if (pos.getY() > highestY) {
                    highestY = pos.getY();
                }
            }
        }

        return highestY;
    }

    /**
     * Find highest GROUND type block Y at a specific X,Z position in chunk data.
     * Only considers blocks with BlockTypeType.GROUND.
     *
     * @param chunkData Chunk data to search
     * @param worldX World X coordinate
     * @param worldZ World Z coordinate
     * @param worldId World identifier for block type lookup
     * @return Highest Y coordinate of GROUND block, or -1 if no GROUND block found
     */
    private int findHighestGroundBlockAtPosition(LayerChunkData chunkData, int worldX, int worldZ, String worldId) {
        int highestY = -1;

        // Parse worldId for block type lookup
        WorldId wid = WorldId.of(worldId).orElse(null);
        if (wid == null) {
            log.warn("Invalid worldId for block type lookup: {}", worldId);
            return -1;
        }

        for (LayerBlock layerBlock : chunkData.getBlocks()) {
            Block block = layerBlock.getBlock();
            if (block == null || block.getPosition() == null) {
                continue;
            }

            Vector3Int pos = block.getPosition();
            if (pos.getX() == worldX && pos.getZ() == worldZ) {
                // Check if this block is GROUND type
                String blockTypeId = block.getBlockTypeId();
                if (blockTypeId != null && !blockTypeId.isBlank()) {
                    // Look up block type
                    Optional<WBlockType> blockTypeOpt = blockTypeService.findByBlockId(wid, blockTypeId);
                    if (blockTypeOpt.isPresent()) {
                        WBlockType blockType = blockTypeOpt.get();
                        if (blockType.getPublicData() == null || blockType.getPublicData().getType() == null ) {
                            log.warn("Block type has no type defined: {}", blockTypeId);
                            continue;
                        }
                        if (blockType.getPublicData() != null &&
                            blockType.getPublicData().getType() == BlockTypeType.GROUND) {
                            // This is a GROUND block
                            if (pos.getY() > highestY) {
                                highestY = pos.getY();
                            }
                        }
                    }
                }
            }
        }

        return highestY;
    }

    /**
     * Fill a column with blocks from level down to lowestSiblingLevel.
     * Uses column definition to determine block types.
     * Applies corner smoothing and face visibility optimization if enabled.
     */
    private void fillColumn(LayerChunkData chunkData, int worldX, int worldZ,
                            int level, int lowestSiblingLevel,
                            WFlat.MaterialDefinition columnDef, WFlat flat,
                            boolean smoothCorners, boolean optimizeFaces,
                            Map<String, WBlockType> blockTypeCache,
                            WorldId wid, int localX, int localZ) {
        // Get extra blocks for this column
        String[] extraBlocks = flat.getExtraBlocksForColumn(
                worldX - flat.getMountX(),
                worldZ - flat.getMountZ()
        );

        // Fill from level down to lowestSiblingLevel
        // do not start at level, start at 255 - there could be extra blocks above
        for (int y = 255; y >= lowestSiblingLevel; y--) {
            // Get block definition for this Y level
            String blockDefString = columnDef.getBlockAt(flat, level, y, extraBlocks);

            if (Strings.isBlank(blockDefString)) {
                // Air block, skip
                continue;
            }

            if ("core:water".equals(blockDefString) || "w:5000".equals(blockDefString)) {
                if (y != 60) {
                    log.warn("Filling block at ({},{},{}) with definition: {}", worldX, y, worldZ, blockDefString);
                }
                blockDefString = "w:5000";
            }

            // Create block with position
            Block block = Block.builder()
                    .position(Vector3Int.builder()
                            .x(worldX)
                            .y(y)
                            .z(worldZ)
                            .build())
                    .build();

            // Parse and apply block definition
            Optional<BlockDef> blockDefOpt = BlockDef.of(blockDefString);
            if (blockDefOpt.isEmpty()) {
                log.warn("Invalid block definition: {} at ({},{},{})", blockDefString, worldX, y, worldZ);
                continue;
            }
            blockDefOpt.get().fillBlock(block);

            // Apply block optimizations (corner smoothing and/or face visibility) if enabled
            if ((smoothCorners || optimizeFaces) && y <= level) {
                boolean shouldExport = applyBlockOptimizations(block, flat, localX, localZ, level, y, blockTypeCache, wid, smoothCorners, optimizeFaces);
                if (!shouldExport) {
                    // Block has no visible faces, skip export
                    continue;
                }
            }

            // Wrap in LayerBlock and add to chunk
            LayerBlock layerBlock = LayerBlock.builder()
                    .block(block)
                    .build();

            chunkData.getBlocks().add(layerBlock);
        }
    }

    /**
     * Apply block optimizations: corner smoothing and/or face visibility.
     * Only applies to GROUND type blocks with modifier 0 and shape 1 (CUBE).
     * - Corner smoothing: Adjusts Y offsets of 4 top corners based on neighbor heights
     * - Face visibility: Sets faceVisibility to hide non-visible block faces
     *
     * @return true if block should be exported, false if block should be skipped (no visible faces)
     */
    private boolean applyBlockOptimizations(Block block, WFlat flat, int localX, int localZ,
                                        int level, int y,
                                        Map<String, WBlockType> blockTypeCache, WorldId wid,
                                        boolean smoothCorners, boolean optimizeFaces) {
        if (block == null || block.getBlockTypeId() == null || wid == null) {
            return true;  // No optimization, export block
        }

        // Get or cache block type
        String blockTypeId = block.getBlockTypeId();
        WBlockType blockType = blockTypeCache.computeIfAbsent(blockTypeId, id ->
                blockTypeService.findByBlockId(wid, id).orElse(null)
        );

        if (blockType == null || blockType.getPublicData() == null) {
            return true;  // Not optimizable, export block
        }

        var publicData = blockType.getPublicData();

        // Check if GROUND type
        if (publicData.getType() != BlockTypeType.GROUND) {
            return true;  // Not GROUND, export block
        }

        // Check if modifier (status) is 0 or null
        Integer status = block.getStatus();
        if (status != null && status != 0) {
            return true;  // Modified block, export block
        }

        // Check if shape is CUBE (shape 1)
        // Shape is stored in modifiers map, get modifier 0
        if (publicData.getModifiers() == null) {
            return true;  // No modifiers, export block
        }
        var modifier0 = publicData.getModifiers().get(0);
        if (modifier0 == null || modifier0.getVisibility() == null || modifier0.getVisibility().getShape() == null || modifier0.getVisibility().getShape() != 1) {
            return true;  // Not CUBE, export block
        }

        // Get current level
        int myLevel = flat.getLevel(localX, localZ);

        // === CORNER SMOOTHING (only for y == level) ===
        if (smoothCorners && y == level) {
            // Initialize offsets array (24 values for 8 corners × XYZ)
            // Default all to 0
            List<Float> offsets = new ArrayList<>(24);
            for (int i = 0; i < 24; i++) {
                offsets.add(0.0f);
            }

            // Calculate all corner offsets
            // Top Front Left (SW) - neighbors: West(-X,0), South(0,-Z), SW(-X,-Z)
            float swOffset = calculateCornerOffset(flat, localX, localZ, myLevel, -1, 0, 0, -1, -1, -1);

            // Top Front Right (SE) - neighbors: East(+X,0), South(0,-Z), SE(+X,-Z)
            float seOffset = calculateCornerOffset(flat, localX, localZ, myLevel, 1, 0, 0, -1, 1, -1);

            // Top Back Left (NW) - neighbors: West(-X,0), North(0,+Z), NW(-X,+Z)
            float nwOffset = calculateCornerOffset(flat, localX, localZ, myLevel, -1, 0, 0, 1, -1, 1);

            // Top Back Right (NE) - neighbors: East(+X,0), North(0,+Z), NE(+X,+Z)
            float neOffset = calculateCornerOffset(flat, localX, localZ, myLevel, 1, 0, 0, 1, 1, 1);

            // Set offsets (NW and NE are swapped to fix north-facing direction)
            offsets.set(13, swOffset);  // SW - indices 12,13,14
            offsets.set(16, seOffset);  // SE - indices 15,16,17
            offsets.set(19, neOffset);  // NW - indices 18,19,20 (swapped: uses NE offset)
            offsets.set(22, nwOffset);  // NE - indices 21,22,23 (swapped: uses NW offset)

            // Set offsets on block
            block.setOffsets(offsets);

            log.trace("Applied corner smoothing to block at ({},{}) with offsets SW:{}, SE:{}, NW:{}, NE:{}",
                    localX, localZ, swOffset, seOffset, nwOffset, neOffset);
        }

        // === FACE VISIBILITY OPTIMIZATION ===
        if (optimizeFaces) {
            // Check if already fixed (FIXED bit set = 64)
            Integer existingFaceVis = block.getFaceVisibility();
            if (existingFaceVis != null && (existingFaceVis & 64) != 0) {
                // FIXED bit is set, don't modify
                return false;
            }

            // FaceFlag bits (set bit = visible face):
            // TOP = 1, BOTTOM = 2, LEFT = 4, RIGHT = 8, FRONT = 16, BACK = 32
            int faceVisibility = 0;

            // TOP: visible if y == level (not visible if below level / nextBlock)
            if (y == level) {
                faceVisibility |= 1;  // TOP visible
            }
            // BOTTOM never visible (y >= lowestSiblingLevel means block below)
            // Don't set BOTTOM bit (2)

            // Check neighbors for side visibility
            // Get neighbor levels
            int westLevel = getNeighborLevel(flat, localX - 1, localZ, myLevel);
            int eastLevel = getNeighborLevel(flat, localX + 1, localZ, myLevel);
            int southLevel = getNeighborLevel(flat, localX, localZ - 1, myLevel);
            int northLevel = getNeighborLevel(flat, localX, localZ + 1, myLevel);

            // LEFT (West): visible if neighbor is lower
            if (westLevel < y) {
                faceVisibility |= 4;  // LEFT visible
            }

            // RIGHT (East): visible if neighbor is lower
            if (eastLevel < y) {
                faceVisibility |= 8;  // RIGHT visible
            }

            // FRONT (South): visible if neighbor is lower (swapped: uses northLevel)
            if (northLevel < y) {
                faceVisibility |= 16;  // FRONT visible
            }

            // BACK (North): visible if neighbor is lower (swapped: uses southLevel)
            if (southLevel < y) {
                faceVisibility |= 32;  // BACK visible
            }

            // Don't export block if no faces are visible
            if (faceVisibility == 0) {
                log.debug("Block at ({},{},{}) has no visible faces (faceVisibility=0), skipping export",
                        block.getPosition().getX(), y, block.getPosition().getZ());
                return false;  // Signal to skip this block
            }

            // Set faceVisibility on block
            block.setFaceVisibility(faceVisibility);

            log.trace("Applied face visibility to block at ({},{},{}): visibility={} (binary: {})",
                    localX, y, localZ, faceVisibility, Integer.toBinaryString(faceVisibility));
        }

        return true;  // Block should be exported
    }

    /**
     * Calculate Y offset for a corner based on three neighbor heights (2 orthogonal + 1 diagonal).
     * Rules (in priority order):
     * - All 3 neighbors at least 2 levels higher → +1.0
     * - All 3 neighbors at least 2 levels lower → -1.0
     * - Mixed (some higher, some lower) → 0.0
     * - At least one lower (and none higher) → -0.5
     * - At least one same (and none lower) → 0.5
     * - All higher (but not all >= 2) → 0.5
     *
     * @param flat The flat terrain data
     * @param localX Current column X
     * @param localZ Current column Z
     * @param myLevel Current column level
     * @param dx1 X offset for first neighbor
     * @param dz1 Z offset for first neighbor
     * @param dx2 X offset for second neighbor
     * @param dz2 Z offset for second neighbor
     * @param dx3 X offset for third neighbor (diagonal)
     * @param dz3 Z offset for third neighbor (diagonal)
     * @return Y offset value
     */
    private float calculateCornerOffset(WFlat flat, int localX, int localZ, int myLevel,
                                       int dx1, int dz1, int dx2, int dz2, int dx3, int dz3) {
        // Get neighbor levels (use myLevel if out of bounds)
        int neighbor1Level = getNeighborLevel(flat, localX + dx1, localZ + dz1, myLevel);
        int neighbor2Level = getNeighborLevel(flat, localX + dx2, localZ + dz2, myLevel);
        int neighbor3Level = getNeighborLevel(flat, localX + dx3, localZ + dz3, myLevel);

        int diff1 = neighbor1Level - myLevel;
        int diff2 = neighbor2Level - myLevel;
        int diff3 = neighbor3Level - myLevel;

        boolean n1Lower = diff1 < 0;
        boolean n1Same = diff1 == 0;
        boolean n1Higher = diff1 > 0;

        boolean n2Lower = diff2 < 0;
        boolean n2Same = diff2 == 0;
        boolean n2Higher = diff2 > 0;

        boolean n3Lower = diff3 < 0;
        boolean n3Same = diff3 == 0;
        boolean n3Higher = diff3 > 0;

        // Apply rules (priority order)
        // All 3 at least 2 levels higher → +1.0
        if (diff1 >= 2 && diff2 >= 2 && diff3 >= 2) {
            return 1.0f;
        }

        // All 3 at least 2 levels lower → -1.0
        if (diff1 <= -2 && diff2 <= -2 && diff3 <= -2) {
            return -1.0f;
        }

        // Mixed: at least one higher AND at least one lower → 0.0
        boolean anyHigher = n1Higher || n2Higher || n3Higher;
        boolean anyLower = n1Lower || n2Lower || n3Lower;
        if (anyHigher && anyLower) {
            return 0.0f;
        }

        // At least one lower (and none higher) → -0.5
        if (anyLower && !anyHigher) {
            return -0.5f;
        }

        // At least one same (and none lower) → 0.5
        boolean anySame = n1Same || n2Same || n3Same;
        if (anySame && !anyLower) {
            return 0.5f;
        }

        // All higher (but not all >= 2 higher) → 0.5
        return 0.5f;
    }

    /**
     * Get neighbor level from flat, or myLevel if out of bounds.
     */
    private int getNeighborLevel(WFlat flat, int x, int z, int myLevel) {
        if (x < 0 || z < 0 || x >= flat.getSizeX() || z >= flat.getSizeZ()) {
            return myLevel;
        }
        return flat.getLevel(x, z);
    }

    /**
     * Calculate faceVisibility for NOT_SET column fill blocks.
     * Shows ONLY ONE face - the one pointing outward from the flat edge.
     *
     * Logic: Determine which edge of the flat this block is on and show the outward-facing side:
     * - West edge (localX = 0) → show LEFT face (4)
     * - East edge (localX = sizeX-1) → show RIGHT face (8)
     * - South edge (localZ = 0) → show FRONT face (16)
     * - North edge (localZ = sizeZ-1) → show BACK face (32)
     *
     * @param flat The flat terrain data
     * @param localX Column X coordinate (within flat)
     * @param localZ Column Z coordinate (within flat)
     * @param myLevel This column's level (unused but kept for consistency)
     * @return Face visibility bitmask (only one bit set)
     */
    private int calculateNotSetFaceVisibility(WFlat flat, int localX, int localZ, int myLevel) {
        int sizeX = flat.getSizeX();
        int sizeZ = flat.getSizeZ();

        // Determine which edge of the flat this block is on
        // Priority: West, East, South, North if on multiple edges (corners)

        if (localX == 0) {
            // West edge → show RIGHT face (outward)
            return 8;
        } else if (localX == sizeX - 1) {
            // East edge → show LEFT face (outward)
            return 4;
        } else if (localZ == 0) {
            // South edge → show FRONT face (outward)
            return 16;
        } else if (localZ == sizeZ - 1) {
            // North edge → show BACK face (outward)
            return 32;
        }

        // Not on any edge → show all side faces (LEFT | RIGHT | FRONT | BACK)
        return 4 | 8 | 16 | 32;  // = 60
    }
}
