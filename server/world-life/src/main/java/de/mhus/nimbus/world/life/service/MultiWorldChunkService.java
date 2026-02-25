package de.mhus.nimbus.world.life.service;

import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.life.model.ChunkCoordinate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service that manages ChunkAliveService and ChunkTTLTracker instances per world.
 * Provides world-specific chunk tracking for multi-world support.
 */
@Service
@Slf4j
public class MultiWorldChunkService {

    /**
     * Listener for world-scoped chunk activation/deactivation events.
     */
    public interface WorldChunkChangeListener {
        void onChunksActivated(WorldId worldId, Set<ChunkCoordinate> added);
        void onChunksDeactivated(WorldId worldId, Set<ChunkCoordinate> removed);
    }

    /**
     * ChunkAliveService instances per world.
     * Maps worldId → ChunkAliveService
     */
    private final Map<String, ChunkAliveService> chunkAliveServices = new ConcurrentHashMap<>();

    /**
     * ChunkTTLTracker instances per world.
     * Maps worldId → ChunkTTLTracker
     */
    private final Map<String, ChunkTTLTracker> ttlTrackers = new ConcurrentHashMap<>();

    /**
     * Registered world-scoped chunk change listeners.
     */
    private final List<WorldChunkChangeListener> worldChunkChangeListeners = new CopyOnWriteArrayList<>();

    /**
     * Register a world chunk change listener.
     */
    public void addWorldChunkChangeListener(WorldChunkChangeListener listener) {
        if (listener != null) {
            worldChunkChangeListeners.add(listener);
            log.debug("Added WorldChunkChangeListener, total: {}", worldChunkChangeListeners.size());
        }
    }

    /**
     * Get or create ChunkAliveService for a world.
     *
     * @param worldId World ID
     * @return ChunkAliveService instance
     */
    public ChunkAliveService getChunkAliveService(WorldId worldId) {
        return chunkAliveServices.computeIfAbsent(worldId.getId(), wid -> {
            log.info("Creating ChunkAliveService for world: {}", wid);
            ChunkAliveService service = new ChunkAliveService();
            service.addDeltaListener(new ChunkAliveService.ChunkChangeListener() {
                @Override
                public void onChunksAdded(Set<ChunkCoordinate> added) {
                    for (WorldChunkChangeListener l : worldChunkChangeListeners) {
                        try {
                            l.onChunksActivated(worldId, added);
                        } catch (Exception e) {
                            log.error("Error notifying WorldChunkChangeListener (activated) for world {}", worldId, e);
                        }
                    }
                }

                @Override
                public void onChunksRemoved(Set<ChunkCoordinate> removed) {
                    for (WorldChunkChangeListener l : worldChunkChangeListeners) {
                        try {
                            l.onChunksDeactivated(worldId, removed);
                        } catch (Exception e) {
                            log.error("Error notifying WorldChunkChangeListener (deactivated) for world {}", worldId, e);
                        }
                    }
                }
            });
            return service;
        });
    }

    /**
     * Get or create ChunkTTLTracker for a world.
     *
     * @param worldId World ID
     * @return ChunkTTLTracker instance
     */
    public ChunkTTLTracker getTTLTracker(WorldId worldId) {
        return ttlTrackers.computeIfAbsent(worldId.getId(), wid -> {
            log.info("Creating ChunkTTLTracker for world: {}", wid);
            return new ChunkTTLTracker();
        });
    }

    /**
     * Add chunks to a world's active set.
     *
     * @param worldId World ID
     * @param chunks Chunks to add
     */
    public void addChunks(WorldId worldId, List<ChunkCoordinate> chunks) {
        ChunkAliveService aliveService = getChunkAliveService(worldId);
        ChunkTTLTracker ttlTracker = getTTLTracker(worldId);

        aliveService.addChunks(chunks);
        chunks.forEach(ttlTracker::touch);
    }

    /**
     * Remove chunks from a world's active set.
     *
     * @param worldId World ID
     * @param chunks Chunks to remove
     */
    public void removeChunks(WorldId worldId, List<ChunkCoordinate> chunks) {
        ChunkAliveService aliveService = getChunkAliveService(worldId);
        ChunkTTLTracker ttlTracker = getTTLTracker(worldId);

        aliveService.removeChunks(chunks);
        ttlTracker.removeChunks(chunks);
    }

    /**
     * Replace all chunks for a world.
     *
     * @param worldId World ID
     * @param chunks New chunk set
     */
    public void replaceChunks(WorldId worldId, Set<ChunkCoordinate> chunks) {
        ChunkAliveService aliveService = getChunkAliveService(worldId);
        ChunkTTLTracker ttlTracker = getTTLTracker(worldId);

        aliveService.replaceChunks(chunks);
        chunks.forEach(ttlTracker::touch);
    }

    /**
     * Get active chunks for a world.
     *
     * @param worldId World ID
     * @return Set of active chunks
     */
    public Set<ChunkCoordinate> getActiveChunks(WorldId worldId) {
        return getChunkAliveService(worldId).getActiveChunks();
    }

    /**
     * Check if a chunk is active in a world.
     *
     * @param worldId World ID
     * @param chunkKey Chunk key (format "cx:cz")
     * @return True if chunk is active
     */
    public boolean isChunkActive(WorldId worldId, String chunkKey) {
        return getChunkAliveService(worldId).isChunkActive(chunkKey);
    }

    /**
     * Get all world IDs with chunk tracking.
     *
     * @return Set of world IDs
     */
    public Set<String> getTrackedWorldIds() {
        return Set.copyOf(chunkAliveServices.keySet());
    }

    /**
     * Remove tracking for a world (when world is disabled).
     *
     * @param worldId World ID
     */
    public void removeWorld(WorldId worldId) {
        ChunkAliveService removed = chunkAliveServices.remove(worldId.getId());
        ttlTrackers.remove(worldId.getId());

        if (removed != null) {
            log.info("Removed chunk tracking for world: {}", worldId);
        }
    }
}
