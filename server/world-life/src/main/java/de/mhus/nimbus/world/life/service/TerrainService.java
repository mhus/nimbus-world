package de.mhus.nimbus.world.life.service;

import de.mhus.nimbus.generated.types.Block;
import de.mhus.nimbus.generated.types.ChunkData;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.world.ChunksCache;
import de.mhus.nimbus.world.shared.world.WChunkService;
import de.mhus.nimbus.world.shared.world.WWorldInstanceService;
import de.mhus.nimbus.world.shared.world.WWorldService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.lang.ref.SoftReference;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for accessing terrain data (chunk blocks) for entity positioning.
 * Provides ground height lookup and block queries for terrain-aware movement.
 * Uses ChunksCache internally so repeated lookups in the same chunk area are fast.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TerrainService {

    private final WChunkService chunkService;
    private final WWorldService worldService;
    private final WWorldInstanceService instanceService;

    /** WorldId string → SoftReference<ChunksCache> — one cache per world/instance. */
    private final Map<String, SoftReference<ChunksCache>> cacheMap = new ConcurrentHashMap<>();

    /**
     * Get or create a ChunksCache for the given worldId.
     * Uses SoftReferences so caches can be GC'd under memory pressure.
     */
    public ChunksCache getChunksCache(WorldId worldId) {
        String key = worldId.getId();
        SoftReference<ChunksCache> ref = cacheMap.get(key);
        if (ref != null) {
            ChunksCache cached = ref.get();
            if (cached != null) {
                return cached;
            }
            cacheMap.remove(key);
        }

        ChunksCache chunksCache = ChunksCache.builder()
                .chunkService(chunkService)
                .worldService(worldService)
                .instanceService(instanceService)
                .worldId(worldId)
                .build();
        cacheMap.put(key, new SoftReference<>(chunksCache));
        return chunksCache;
    }

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
        return getGroundHeight(worldId, x, z, startY, false, 0);
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
     * @param epoch Current world epoch
     * @return Y coordinate of ground surface (top of highest solid block), or 64 if not found
     */
    public int getGroundHeight(WorldId worldId, int x, int z, int startY, boolean canWalkOnWater, int epoch) {
        try {
            ChunksCache cache = getChunksCache(worldId);
            ChunkData chunkData = cache.getChunkData(x, z);

            if (chunkData == null) {
                log.trace("Chunk not found for ground height lookup: world={}, pos=({}, {})", worldId, x, z);
                return 64; // Default ground level
            }

            // Try to use HeightData first (if available) for better performance
            if (chunkData.getHeightData() != null) {
                var heightDataDto = cache.getHeightDataAt(x, z);
                if (heightDataDto != null) {
                    // Check if there's water at this position
                    if (heightDataDto.hasWater() && !canWalkOnWater) {
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
                        log.trace("Ground height found at ({}, {}, {}): y={}", x, y, z, y + 1);
                        return y + 1;
                    }
                }
            }

            log.trace("No solid block found at ({}, {}), using default ground level", x, z);
            return 64;

        } catch (Exception e) {
            log.error("Error getting ground height at ({}, {})", x, z, e);
            return 64; // Fallback to default
        }
    }

    /**
     * Get block at specific world coordinates.
     */
    private Optional<Block> getBlockAt(ChunkData chunkData, int worldX, int worldY, int worldZ) {
        if (chunkData.getBlocks() == null) {
            return Optional.empty();
        }

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
     */
    public boolean isSolidBlock(String blockTypeId) {
        return blockTypeId != null && !blockTypeId.equals("0") && !blockTypeId.isBlank();
    }

    /**
     * Check if position is valid (within world bounds).
     */
    public boolean isValidHeight(int y) {
        return y >= 0 && y <= 255;
    }

    /**
     * Get water position at world position (x, z).
     * Returns a valid Y position for water-based entities (fish, etc.).
     *
     * @param worldId World identifier
     * @param x X coordinate (world space)
     * @param z Z coordinate (world space)
     * @param epoch Current world epoch
     * @return Y coordinate within water bounds, or -1 if no water at this position
     */
    public int getWaterPosition(WorldId worldId, int x, int z, int epoch) {
        try {
            ChunksCache cache = getChunksCache(worldId);
            var heightDataDto = cache.getHeightDataAt(x, z);

            if (heightDataDto != null && heightDataDto.hasWater()) {
                int groundLevel = heightDataDto.groundLevel();
                int waterLevel = heightDataDto.waterLevel();

                int waterY = (groundLevel + waterLevel) / 2;
                log.trace("Water position at ({}, {}): y={} (ground={}, water={})",
                        x, z, waterY, groundLevel, waterLevel);
                return waterY;
            }

            log.trace("No water at position ({}, {})", x, z);
            return -1;

        } catch (Exception e) {
            log.error("Error getting water position at ({}, {})", x, z, e);
            return -1;
        }
    }
}
