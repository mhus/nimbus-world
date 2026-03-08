package de.mhus.nimbus.world.life.service;

import de.mhus.nimbus.shared.types.WorldId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Service that tracks active worlds for simulation.
 * Worlds are registered dynamically via Redis notifications (world:global:active)
 * when players log in. No upfront MongoDB discovery needed.
 * Worlds are removed when no active chunks remain (via ChunkTTLCleanupTask).
 */
@Service
@Slf4j
public class WorldDiscoveryService {

    /**
     * Listener for world activation events.
     * Called immediately when a new world is registered, so subscribers
     * can set up per-world Redis subscriptions without polling delay.
     */
    public interface WorldActivationListener {
        void onWorldActivated(WorldId worldId);
    }

    private final Set<WorldId> activeWorldIds = ConcurrentHashMap.newKeySet();
    private final List<WorldActivationListener> activationListeners = new CopyOnWriteArrayList<>();

    /**
     * Register a listener that will be called immediately when a new world is activated.
     */
    public void addWorldActivationListener(WorldActivationListener listener) {
        if (listener != null) {
            activationListeners.add(listener);
        }
    }

    /**
     * Register a world dynamically (notified via Redis when a player logs in).
     * Returns true if this is a newly registered world.
     * Immediately notifies all registered WorldActivationListeners.
     *
     * @param worldId The world ID to register
     * @return true if newly added
     */
    public boolean registerDynamicWorld(String worldId) {
        WorldId wid = WorldId.unchecked(worldId);
        if (activeWorldIds.add(wid)) {
            log.info("World registered: {}", worldId);
            notifyActivationListeners(wid);
            return true;
        }
        return false;
    }

    private void notifyActivationListeners(WorldId worldId) {
        for (WorldActivationListener listener : activationListeners) {
            try {
                listener.onWorldActivated(worldId);
            } catch (Exception e) {
                log.error("Error notifying WorldActivationListener for world {}: {}", worldId, e.getMessage(), e);
            }
        }
    }

    /**
     * Remove a world registration (no active chunks remaining).
     *
     * @param worldId The world ID to remove
     */
    public void removeDynamicWorld(String worldId) {
        WorldId wid = WorldId.unchecked(worldId);
        if (activeWorldIds.remove(wid)) {
            log.info("World removed: {}", worldId);
        }
    }

    /**
     * Get all currently active world IDs.
     *
     * @return Set of world IDs
     */
    public Set<WorldId> getKnownWorldIds() {
        return Set.copyOf(activeWorldIds);
    }

    /**
     * Check if a world is active.
     *
     * @param worldId World ID
     * @return True if world is active
     */
    public boolean isWorldKnown(String worldId) {
        return activeWorldIds.contains(WorldId.unchecked(worldId));
    }
}
