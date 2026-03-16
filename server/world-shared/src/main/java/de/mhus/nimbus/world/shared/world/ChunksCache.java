package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.generated.types.ChunkData;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.shared.dto.HeightDataDto;
import lombok.extern.slf4j.Slf4j;

import java.lang.ref.SoftReference;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cache for ChunkData with automatic loading via WChunkService.
 * Uses SoftReferences so cached chunks can be garbage-collected under memory pressure.
 * <p>
 * Provides direct world-coordinate access to height data without requiring
 * the caller to know about chunk keys or chunk-local coordinates.
 * <p>
 * Resolves the epoch from the world instance (via WWorldInstanceService) so that
 * epoch-correct chunk data is loaded automatically.
 * <p>
 * Not a Spring component — create per use-case via the builder, passing
 * the required services and worldId.
 */
@Slf4j
public class ChunksCache {

    private final WChunkService chunkService;
    private final WWorld world;
    private final WorldId worldId;
    private final int epoch;

    /** chunkKey → SoftReference<ChunkData> */
    private final ConcurrentHashMap<String, SoftReference<ChunkData>> cache = new ConcurrentHashMap<>();

    private ChunksCache(WChunkService chunkService, WWorld world, WorldId worldId, int epoch) {
        this.chunkService = chunkService;
        this.world = world;
        this.worldId = worldId;
        this.epoch = epoch;
    }

    /**
     * Get height data at world coordinates (x, z).
     *
     * @return HeightDataDto if available, null otherwise
     */
    public HeightDataDto getHeightDataAt(int worldX, int worldZ) {
        ChunkData chunkData = getChunkData(worldX, worldZ);
        if (chunkData == null || chunkData.getHeightData() == null) {
            return null;
        }

        String key = worldX + "," + worldZ;
        int[] columnData = chunkData.getHeightData().get(key);
        if (columnData == null || columnData.length < 2) {
            return null;
        }

        // Format: [groundLevel, waterLevel (-1=none), maxHeight?]
        return new HeightDataDto(
                columnData[0], columnData[1],
                columnData.length > 2 ? columnData[2] : null
        );
    }

    /**
     * Get ChunkData for the chunk containing the given world coordinates.
     * Loads from cache or fetches via WChunkService with the resolved epoch.
     */
    public ChunkData getChunkData(int worldX, int worldZ) {
        String chunkKey = world.getChunkKey(worldX, worldZ);
        return getChunkDataByKey(chunkKey);
    }

    /**
     * Get ChunkData by chunk key. Loads from cache or fetches via WChunkService.
     */
    public ChunkData getChunkDataByKey(String chunkKey) {
        SoftReference<ChunkData> ref = cache.get(chunkKey);
        if (ref != null) {
            ChunkData cached = ref.get();
            if (cached != null) {
                return cached;
            }
            cache.remove(chunkKey);
        }

        Optional<ChunkData> loaded = chunkService.loadChunkData(worldId, chunkKey, false, epoch);
        if (loaded.isEmpty()) {
            log.debug("[chunks-cache] Chunk not found: {} for world {} epoch {}", chunkKey, worldId.getId(), epoch);
            return null;
        }

        ChunkData chunkData = loaded.get();
        cache.put(chunkKey, new SoftReference<>(chunkData));
        return chunkData;
    }

    /**
     * Clear all cached chunks.
     */
    public void clear() {
        cache.clear();
    }

    /**
     * Number of currently cached chunk entries (some may have been GC'd).
     */
    public int size() {
        return cache.size();
    }

    public int getEpoch() {
        return epoch;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private WChunkService chunkService;
        private WWorldService worldService;
        private WWorldInstanceService instanceService;
        private WorldId worldId;

        public Builder chunkService(WChunkService chunkService) {
            this.chunkService = chunkService;
            return this;
        }

        public Builder worldService(WWorldService worldService) {
            this.worldService = worldService;
            return this;
        }

        public Builder instanceService(WWorldInstanceService instanceService) {
            this.instanceService = instanceService;
            return this;
        }

        public Builder worldId(WorldId worldId) {
            this.worldId = worldId;
            return this;
        }

        public ChunksCache build() {
            if (chunkService == null) throw new IllegalStateException("chunkService is required");
            if (worldService == null) throw new IllegalStateException("worldService is required");
            if (instanceService == null) throw new IllegalStateException("instanceService is required");
            if (worldId == null) throw new IllegalStateException("worldId is required");

            WorldId baseWorldId = worldId.toBaseWorldId();
            WWorld world = worldService.getByWorldId(baseWorldId.getId())
                    .orElseThrow(() -> new IllegalStateException("World not found: " + baseWorldId.getId()));

            int epoch = resolveEpoch();

            log.debug("[chunks-cache] Created for world {} epoch {}", worldId.getId(), epoch);
            return new ChunksCache(chunkService, world, baseWorldId, epoch);
        }

        private int resolveEpoch() {
            if (!worldId.isInstance()) {
                return 0;
            }
            return instanceService.findByInstanceIdWithValidation(worldId.getId())
                    .map(WWorldInstance::getEpoch)
                    .orElse(0);
        }
    }
}
