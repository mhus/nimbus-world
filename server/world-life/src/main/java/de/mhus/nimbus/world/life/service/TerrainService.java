package de.mhus.nimbus.world.life.service;

import de.mhus.nimbus.generated.types.Block;
import de.mhus.nimbus.generated.types.ChunkData;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.world.BlockUtil;
import de.mhus.nimbus.world.shared.world.WChunkService;
import de.mhus.nimbus.world.shared.world.WWorldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service for accessing terrain data (chunk blocks) for entity positioning.
 * Provides ground height lookup and block queries for terrain-aware movement.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TerrainService {

    private final WChunkService chunkService;
    private final WWorldService worldService;

    /**
     * Get ground height at world position (x, z).
     * First tries to use HeightData if available, otherwise searches downward.
     *
     * @param worldId World identifier
     * @param x X coordinate (world space)
     * @param z Z coordinate (world space)
     * @param startY Starting Y coordinate for downward search
     * @return Y coordinate of ground surface (top of highest solid block), or 64 if not found
     */
    public int getGroundHeight(WorldId worldId, int x, int z, int startY) {
        return getGroundHeight(worldId, x, z, startY, false);
    }

    /**
     * Get ground height at world position (x, z).
     * First tries to use HeightData if available, otherwise searches downward.
     *
     * @param worldId World identifier
     * @param x X coordinate (world space)
     * @param z Z coordinate (world space)
     * @param startY Starting Y coordinate for downward search
     * @param canWalkOnWater If true, allows walking on water. If false, skips positions with water.
     * @return Y coordinate of ground surface (top of highest solid block), or 64 if not found
     */
    public int getGroundHeight(WorldId worldId, int x, int z, int startY, boolean canWalkOnWater) {
        try {
            var world = worldService.getByWorldId(worldId).orElseThrow();
            // Calculate chunk coordinates
            int chunkX = world.getChunkX(x);
            int chunkZ = world.getChunkZ(z);
            String chunkKey = BlockUtil.toChunkKey(chunkX, chunkZ);

            // Load chunk from database (regionId = worldId for main world, create=false)
            Optional<ChunkData> chunkDataOpt = chunkService.loadChunkData(worldId, chunkKey, false);

            if (chunkDataOpt.isEmpty()) {
                log.trace("Chunk not found for ground height lookup: world={}, chunk={}", worldId, chunkKey);
                return 64; // Default ground level
            }

            var chunkSize = world.getPublicData().getChunkSize();
            ChunkData chunkData = chunkDataOpt.get();

            // Try to use HeightData first (if available) for better performance
            if (chunkData.getHeightData() != null) {
                int localX = ((x % chunkSize) + chunkSize) % chunkSize;
                int localZ = ((z % chunkSize) + chunkSize) % chunkSize;

                var heightDataDto = chunkService.getHeightDataForColumn(chunkData, localX, localZ);
                if (heightDataDto != null) {
                    // Check if there's water at this position
                    if (heightDataDto.waterLevel() != null && !canWalkOnWater) {
                        // Position has water and entity cannot walk on water
                        log.trace("Skipping position with water: ({}, {}), waterLevel={}", x, z, heightDataDto.waterLevel());
                        return -1; // Indicate invalid position (has water)
                    }

                    // Return ground level from height data (already calculated)
                    int groundLevel = heightDataDto.groundLevel();
                    if (groundLevel >= 0) {
                        log.trace("Ground height from heightData at ({}, {}): y={}", x, z, groundLevel + 1);
                        return groundLevel + 1; // +1 to stand on top of block
                    }
                }
            }

            // Search downward from startY to find highest solid block
            for (int y = startY; y >= 0; y--) {
                Optional<Block> blockOpt = getBlockAt(chunkData, x, y, z);

                if (blockOpt.isPresent()) {
                    Block block = blockOpt.get();
                    String blockTypeId = block.getBlockTypeId();

                    if (isSolidBlock(blockTypeId)) {
                        // Found solid block, return Y + 1 (stand on top)
                        log.trace("Ground height found at ({}, {}, {}): y={}", x, y, z, y + 1);
                        return y + 1;
                    }
                }
            }

            // No solid block found, use default ground level
            log.trace("No solid block found at ({}, {}), using default ground level", x, z);
            return 64;

        } catch (Exception e) {
            log.error("Error getting ground height at ({}, {})", x, z, e);
            return 64; // Fallback to default
        }
    }

    /**
     * Get block at specific world coordinates.
     *
     * @param chunkData Chunk data
     * @param worldX World X coordinate
     * @param worldY World Y coordinate
     * @param worldZ World Z coordinate
     * @return Optional containing the block if found
     */
    private Optional<Block> getBlockAt(ChunkData chunkData, int worldX, int worldY, int worldZ) {
        if (chunkData.getBlocks() == null) {
            return Optional.empty();
        }

        // Search for block with matching position
        for (Block block : chunkData.getBlocks()) {
            if (block.getPosition() == null) continue;

            var pos = block.getPosition();
            if (pos.getX() == worldX && pos.getY() == worldY && pos.getZ() == worldZ) {
                return Optional.of(block);
            }
        }

        return Optional.empty();
    }

    /**
     * Check if block type ID represents a solid block.
     *
     * @param blockTypeId Block type identifier
     * @return True if block is solid (not air or null)
     */
    public boolean isSolidBlock(String blockTypeId) {
        // "0" is air block, null is also air
        return blockTypeId != null && !blockTypeId.equals("0") && !blockTypeId.isBlank();
    }

    /**
     * Check if position is valid (within world bounds).
     *
     * @param y Y coordinate
     * @return True if within valid range
     */
    public boolean isValidHeight(int y) {
        return y >= 0 && y <= 255;
    }

    /**
     * Get water position at world position (x, z).
     * Returns a valid Y position for water-based entities (fish, etc.).
     * Position must be between groundLevel and waterLevel.
     *
     * @param worldId World identifier
     * @param x X coordinate (world space)
     * @param z Z coordinate (world space)
     * @return Y coordinate within water bounds, or -1 if no water at this position
     */
    public int getWaterPosition(WorldId worldId, int x, int z) {
        try {
            var world = worldService.getByWorldId(worldId).orElseThrow();
            // Calculate chunk coordinates
            int chunkX = world.getChunkX(x);
            int chunkZ = world.getChunkZ(z);
            String chunkKey = BlockUtil.toChunkKey(chunkX, chunkZ);

            // Load chunk from database
            Optional<ChunkData> chunkDataOpt = chunkService.loadChunkData(worldId, chunkKey, false);

            if (chunkDataOpt.isEmpty()) {
                log.trace("Chunk not found for water position lookup: world={}, chunk={}", worldId, chunkKey);
                return -1;
            }

            var chunkSize = world.getPublicData().getChunkSize();
            ChunkData chunkData = chunkDataOpt.get();

            // Use HeightData to find water bounds
            if (chunkData.getHeightData() != null) {
                int localX = ((x % chunkSize) + chunkSize) % chunkSize;
                int localZ = ((z % chunkSize) + chunkSize) % chunkSize;

                var heightDataDto = chunkService.getHeightDataForColumn(chunkData, localX, localZ);
                if (heightDataDto != null && heightDataDto.waterLevel() != null) {
                    // Water exists at this position
                    int groundLevel = heightDataDto.groundLevel();
                    int waterLevel = heightDataDto.waterLevel();

                    // Return mid-point between ground and water (where fish swim)
                    int waterY = (groundLevel + waterLevel) / 2;
                    log.trace("Water position at ({}, {}): y={} (ground={}, water={})",
                            x, z, waterY, groundLevel, waterLevel);
                    return waterY;
                }
            }

            // No water at this position
            log.trace("No water at position ({}, {})", x, z);
            return -1;

        } catch (Exception e) {
            log.error("Error getting water position at ({}, {})", x, z, e);
            return -1;
        }
    }
}
