package de.mhus.nimbus.world.life.service;

import de.mhus.nimbus.world.life.model.ChunkCoordinate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Service for tracking which chunks are currently active (viewed by clients).
 * Receives chunk registration updates from world-player pods via Redis.
 * Notifies listeners when active chunks change.
 */
@Service
@Slf4j
public class ChunkAliveService {

    /**
     * Currently active chunks (being viewed by at least one client).
     * Thread-safe set backed by ConcurrentHashMap.
     */
    private final Set<ChunkCoordinate> activeChunks = ConcurrentHashMap.newKeySet();

    /**
     * Listeners notified when active chunks change.
     */
    private final List<Consumer<Set<ChunkCoordinate>>> changeListeners = new CopyOnWriteArrayList<>();

    /**
     * Add chunks to active set.
     * Notifies listeners if any new chunks were added.
     *
     * @param chunks Chunks to add
     */
    public void addChunks(List<ChunkCoordinate> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }

        Set<ChunkCoordinate> added = new HashSet<>();
        for (ChunkCoordinate chunk : chunks) {
            if (activeChunks.add(chunk)) {
                added.add(chunk);
            }
        }

        if (!added.isEmpty()) {
            log.debug("Added {} chunks to active set, total active: {}", added.size(), activeChunks.size());
            notifyListeners();
        }
    }

    /**
     * Remove chunks from active set.
     * Notifies listeners if any chunks were removed.
     *
     * @param chunks Chunks to remove
     */
    public void removeChunks(List<ChunkCoordinate> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }

        Set<ChunkCoordinate> removed = new HashSet<>();
        for (ChunkCoordinate chunk : chunks) {
            if (activeChunks.remove(chunk)) {
                removed.add(chunk);
            }
        }

        if (!removed.isEmpty()) {
            log.debug("Removed {} chunks from active set, total active: {}", removed.size(), activeChunks.size());
            notifyListeners();
        }
    }

    /**
     * Replace all active chunks with new set.
     * Used during periodic chunk refresh to ensure accuracy.
     * Notifies listeners if any changes occurred.
     *
     * @param newChunks New set of active chunks
     */
    public void replaceChunks(Set<ChunkCoordinate> newChunks) {
        if (newChunks == null) {
            newChunks = new HashSet<>();
        }

        Set<ChunkCoordinate> added = new HashSet<>(newChunks);
        added.removeAll(activeChunks);

        Set<ChunkCoordinate> removed = new HashSet<>(activeChunks);
        removed.removeAll(newChunks);

        activeChunks.clear();
        activeChunks.addAll(newChunks);

        log.info("Chunk refresh: {} active, {} added, {} removed",
                activeChunks.size(), added.size(), removed.size());

        if (!added.isEmpty() || !removed.isEmpty()) {
            notifyListeners();
        }
    }

    /**
     * Get snapshot of currently active chunks.
     *
     * @return Immutable copy of active chunks
     */
    public Set<ChunkCoordinate> getActiveChunks() {
        return new HashSet<>(activeChunks);
    }

    /**
     * Check if a specific chunk is active.
     *
     * @param cx Chunk X coordinate
     * @param cz Chunk Z coordinate
     * @return True if chunk is active
     */
    public boolean isChunkActive(int cx, int cz) {
        return activeChunks.contains(new ChunkCoordinate(cx, cz));
    }

    /**
     * Check if a chunk (by key string) is active.
     *
     * @param chunkKey Chunk key in format "cx:cz"
     * @return True if chunk is active
     */
    public boolean isChunkActive(String chunkKey) {
        if (chunkKey == null || chunkKey.isBlank()) {
            return false;
        }

        try {
            ChunkCoordinate coord = ChunkCoordinate.fromString(chunkKey);
            return activeChunks.contains(coord);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid chunk key format: {}", chunkKey);
            return false;
        }
    }

    /**
     * Get number of active chunks.
     *
     * @return Count of active chunks
     */
    public int getActiveChunkCount() {
        return activeChunks.size();
    }

    /**
     * Register a listener to be notified when active chunks change.
     *
     * @param listener Consumer that receives updated chunk set
     */
    public void addChangeListener(Consumer<Set<ChunkCoordinate>> listener) {
        if (listener != null) {
            changeListeners.add(listener);
            log.debug("Added chunk change listener, total listeners: {}", changeListeners.size());
        }
    }

    /**
     * Remove a change listener.
     *
     * @param listener Listener to remove
     */
    public void removeChangeListener(Consumer<Set<ChunkCoordinate>> listener) {
        if (changeListeners.remove(listener)) {
            log.debug("Removed chunk change listener, total listeners: {}", changeListeners.size());
        }
    }

    /**
     * Notify all registered listeners of chunk changes.
     */
    private void notifyListeners() {
        Set<ChunkCoordinate> snapshot = getActiveChunks();

        for (Consumer<Set<ChunkCoordinate>> listener : changeListeners) {
            try {
                listener.accept(snapshot);
            } catch (Exception e) {
                log.error("Error notifying chunk change listener", e);
            }
        }

        log.trace("Notified {} chunk change listeners", changeListeners.size());
    }
}
