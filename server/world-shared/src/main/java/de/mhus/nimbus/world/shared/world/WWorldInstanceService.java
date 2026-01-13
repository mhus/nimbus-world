package de.mhus.nimbus.world.shared.world;

import de.mhus.nimbus.shared.types.WorldId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Business logic service for WWorldInstance.
 * Manages world instances which are independent copies of worlds.
 * Supports listeners for instance lifecycle events.
 */
@Service
@Slf4j
public class WWorldInstanceService {

    private final WWorldInstanceRepository repository;
    private final WWorldService worldService;
    private final List<WWorldInstanceListener> listeners;

    /**
     * Constructor with lazy initialization to avoid circular dependencies.
     *
     * @param repository The repository
     * @param worldService The world service (lazy to break circular dependency)
     * @param listeners List of listeners (lazy initialized)
     */
    public WWorldInstanceService(
            WWorldInstanceRepository repository,
            @Lazy WWorldService worldService,
            @Lazy List<WWorldInstanceListener> listeners) {
        this.repository = repository;
        this.worldService = worldService;
        this.listeners = listeners;
    }

    /**
     * Find all world instances.
     *
     * @return List of all instances
     */
    @Transactional(readOnly = true)
    public List<WWorldInstance> findAll() {
        return repository.findAll();
    }

    /**
     * Find a world instance by instanceId.
     * WARNING: This method does not validate that the instance belongs to the expected worldId.
     * Use findByInstanceIdWithValidation() for secure lookup.
     *
     * @param instanceId The instanceId
     * @return Optional containing the instance if found
     */
    @Transactional(readOnly = true)
    public Optional<WWorldInstance> findByInstanceId(String instanceId) {
        return repository.findByInstanceId(instanceId);
    }

    /**
     * Find a world instance by instanceId and validate it belongs to the expected worldId.
     * This method extracts the base worldId from the full instanceId using WorldId class
     * and validates that the found instance actually belongs to that worldId.
     *
     * @param fullInstanceId The full instanceId (format: worldId!instance per WorldId spec)
     * @return Optional containing the instance if found and validated
     */
    @Transactional(readOnly = true)
    public Optional<WWorldInstance> findByInstanceIdWithValidation(String fullInstanceId) {
        if (fullInstanceId == null || fullInstanceId.isBlank()) {
            return Optional.empty();
        }

        // Parse using WorldId class
        WorldId worldId;
        try {
            worldId = WorldId.unchecked(fullInstanceId);
        } catch (Exception e) {
            log.warn("Invalid worldId format: {}", fullInstanceId, e);
            return Optional.empty();
        }

        // Check if it's actually an instance
        if (!worldId.isInstance()) {
            log.warn("WorldId is not an instance: {}", fullInstanceId);
            return Optional.empty();
        }

        // Extract base worldId (without instance part)
        String expectedWorldId = worldId.withoutInstance().getId();

        // Find instance
        Optional<WWorldInstance> instanceOpt = repository.findByInstanceId(fullInstanceId);

        // Validate that instance belongs to expected worldId
        return instanceOpt.filter(instance -> {
            if (!expectedWorldId.equals(instance.getWorldId())) {
                log.warn("Instance worldId mismatch: instanceId={}, expected={}, actual={}",
                        fullInstanceId, expectedWorldId, instance.getWorldId());
                return false;
            }
            return true;
        });
    }

    /**
     * Find all instances based on a specific world.
     *
     * @param worldId The worldId
     * @return List of instances
     */
    @Transactional(readOnly = true)
    public List<WWorldInstance> findByWorldId(String worldId) {
        return repository.findByWorldId(worldId);
    }

    /**
     * Find all instances based on a specific world (WorldId object).
     *
     * @param worldId The WorldId object
     * @return List of instances
     */
    @Transactional(readOnly = true)
    public List<WWorldInstance> findByWorldId(WorldId worldId) {
        return repository.findByWorldId(worldId.getId());
    }

    /**
     * Find all instances created by a specific player.
     *
     * @param creator The playerId of the creator
     * @return List of instances
     */
    @Transactional(readOnly = true)
    public List<WWorldInstance> findByCreator(String creator) {
        return repository.findByCreator(creator);
    }

    /**
     * Find all instances where a specific player is allowed.
     *
     * @param playerId The playerId
     * @return List of instances
     */
    @Transactional(readOnly = true)
    public List<WWorldInstance> findByPlayer(String playerId) {
        return repository.findByPlayersContaining(playerId);
    }

    /**
     * Find all enabled instances based on a specific world.
     *
     * @param worldId The worldId
     * @return List of enabled instances
     */
    @Transactional(readOnly = true)
    public List<WWorldInstance> findEnabledByWorldId(String worldId) {
        return repository.findByWorldIdAndEnabled(worldId, true);
    }

    /**
     * Find all enabled instances created by a specific player.
     *
     * @param creator The playerId of the creator
     * @return List of enabled instances
     */
    @Transactional(readOnly = true)
    public List<WWorldInstance> findEnabledByCreator(String creator) {
        return repository.findByCreatorAndEnabled(creator, true);
    }

    /**
     * Check if an instance exists by instanceId.
     *
     * @param instanceId The instanceId
     * @return true if exists, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean existsByInstanceId(String instanceId) {
        return repository.existsByInstanceId(instanceId);
    }

    /**
     * Create a new world instance for a player.
     * This is a convenience method that:
     * - Generates a unique instanceId
     * - Creates the instance
     * - Adds the player to active players
     * - Saves everything in one transaction
     *
     * @param worldId The base worldId this instance is based on
     * @param worldName The world name (for title generation)
     * @param playerId The playerId of the player
     * @param playerDisplayName The display name of the player (for title)
     * @return The created and active instance
     * @throws IllegalArgumentException if world does not exist
     */
    @Transactional
    public WWorldInstance createInstanceForPlayer(String worldId, String worldName,
                                                    String playerId, String playerDisplayName) {
        if (worldId == null || worldId.isBlank()) {
            throw new IllegalArgumentException("worldId cannot be null or blank");
        }
        if (playerId == null || playerId.isBlank()) {
            throw new IllegalArgumentException("playerId cannot be null or blank");
        }

        // Validate that world exists
        if (!worldService.getByWorldId(worldId).isPresent()) {
            throw new IllegalArgumentException("World does not exist: " + worldId);
        }

        // Generate unique instanceId using WorldId class
        String uuid = java.util.UUID.randomUUID().toString();
        WorldId baseWorldId = WorldId.unchecked(worldId);
        String instanceId = baseWorldId.withInstance(uuid).getId();

        // Create instance
        String title = (worldName != null ? worldName : baseWorldId) + " - " +
                       (playerDisplayName != null ? playerDisplayName : playerId);
        String description = "Auto-created instance for player " + playerId;

        WWorldInstance instance = WWorldInstance.builder()
                .instanceId(instanceId)
                .worldId(worldId)
                .title(title)
                .description(description)
                .creator(playerId)
                .players(List.of(playerId))
                .enabled(true)
                .build();
        instance.touchCreate();

        // Add player to active players
        instance.addActivePlayer(playerId);

        // Save
        repository.save(instance);

        log.info("World instance created for player: instanceId={}, worldId={}, playerId={}",
                instanceId, worldId, playerId);

        // Notify listeners
        notifyListenersCreated(instance);

        return instance;
    }

    /**
     * Create a new world instance.
     * Validates that the base world exists.
     *
     * @param instanceId The unique instanceId
     * @param worldId The worldId this instance is based on
     * @param title The title
     * @param description The description
     * @param creator The playerId of the creator
     * @param players List of playerIds allowed to access
     * @return The created instance
     * @throws IllegalStateException if instance already exists
     * @throws IllegalArgumentException if world does not exist
     */
    @Transactional
    public WWorldInstance create(String instanceId, String worldId, String title, String description,
                                   String creator, List<String> players) {
        if (repository.existsByInstanceId(instanceId)) {
            throw new IllegalStateException("Instance already exists: " + instanceId);
        }

        // Validate that world exists
        if (!worldService.getByWorldId(worldId).isPresent()) {
            throw new IllegalArgumentException("World does not exist: " + worldId);
        }

        WWorldInstance instance = WWorldInstance.builder()
                .instanceId(instanceId)
                .worldId(worldId)
                .title(title)
                .description(description)
                .creator(creator)
                .players(players != null ? players : List.of())
                .enabled(true)
                .build();
        instance.touchCreate();
        repository.save(instance);

        log.debug("World instance created: instanceId={}, worldId={}, creator={}",
                instanceId, worldId, creator);

        // Notify listeners
        notifyListenersCreated(instance);

        return instance;
    }

    /**
     * Update an existing world instance.
     *
     * @param instanceId The instanceId
     * @param updater Consumer to update the instance
     * @return Optional containing the updated instance
     */
    @Transactional
    public Optional<WWorldInstance> update(String instanceId, java.util.function.Consumer<WWorldInstance> updater) {
        return repository.findByInstanceId(instanceId).map(existing -> {
            updater.accept(existing);
            existing.touchUpdate();
            repository.save(existing);
            log.debug("World instance updated: instanceId={}", instanceId);
            return existing;
        });
    }

    /**
     * Save a world instance (update timestamp and persist).
     *
     * @param instance The instance to save
     * @return The saved instance
     */
    @Transactional
    public WWorldInstance save(WWorldInstance instance) {
        instance.touchUpdate();
        WWorldInstance saved = repository.save(instance);
        log.debug("World instance saved: instanceId={}", instance.getInstanceId());
        return saved;
    }

    /**
     * Add a player to an instance.
     *
     * @param instanceId The instanceId
     * @param playerId The playerId to add
     * @return true if player was added, false if already present or instance not found
     */
    @Transactional
    public boolean addPlayer(String instanceId, String playerId) {
        return repository.findByInstanceId(instanceId).map(instance -> {
            boolean added = instance.addPlayer(playerId);
            if (added) {
                save(instance);
                log.debug("Player added to instance: instanceId={}, playerId={}", instanceId, playerId);
            }
            return added;
        }).orElse(false);
    }

    /**
     * Remove a player from an instance.
     *
     * @param instanceId The instanceId
     * @param playerId The playerId to remove
     * @return true if player was removed, false if not present or instance not found
     */
    @Transactional
    public boolean removePlayer(String instanceId, String playerId) {
        return repository.findByInstanceId(instanceId).map(instance -> {
            boolean removed = instance.removePlayer(playerId);
            if (removed) {
                save(instance);
                log.debug("Player removed from instance: instanceId={}, playerId={}", instanceId, playerId);
            }
            return removed;
        }).orElse(false);
    }

    /**
     * Check if a player has access to an instance.
     *
     * @param instanceId The instanceId
     * @param playerId The playerId
     * @return true if player has access, false otherwise
     */
    @Transactional(readOnly = true)
    public boolean hasPlayerAccess(String instanceId, String playerId) {
        return repository.findByInstanceId(instanceId)
                .map(instance -> instance.isPlayerAllowed(playerId))
                .orElse(false);
    }

    /**
     * Delete a world instance by instanceId.
     *
     * @param instanceId The instanceId
     * @return true if deleted, false if not found
     */
    @Transactional
    public boolean delete(String instanceId) {
        Optional<WWorldInstance> instanceOpt = repository.findByInstanceId(instanceId);
        if (instanceOpt.isEmpty()) {
            return false;
        }

        WWorldInstance instance = instanceOpt.get();

        // Delete from repository
        repository.deleteByInstanceId(instanceId);
        log.debug("World instance deleted: instanceId={}", instanceId);

        // Notify listeners
        notifyListenersDeleted(instance);

        return true;
    }

    /**
     * Count instances by worldId.
     *
     * @param worldId The worldId
     * @return Number of instances
     */
    @Transactional(readOnly = true)
    public long countByWorldId(String worldId) {
        return repository.countByWorldId(worldId);
    }

    /**
     * Count instances by creator.
     *
     * @param creator The playerId of the creator
     * @return Number of instances
     */
    @Transactional(readOnly = true)
    public long countByCreator(String creator) {
        return repository.countByCreator(creator);
    }

    /**
     * Find all instances accessible by a player.
     * Includes instances where player is creator or in players list.
     *
     * @param playerId The playerId
     * @return List of accessible instances
     */
    @Transactional(readOnly = true)
    public List<WWorldInstance> findAccessibleByPlayer(String playerId) {
        // Get instances where player is creator
        List<WWorldInstance> createdInstances = repository.findByCreator(playerId);

        // Get instances where player is in players list
        List<WWorldInstance> playerInstances = repository.findByPlayersContaining(playerId);

        // Combine both lists (use stream to avoid duplicates)
        return java.util.stream.Stream.concat(
                createdInstances.stream(),
                playerInstances.stream()
        ).distinct().toList();
    }

    /**
     * Remove a player from an instance and delete the instance if no active players remain.
     * This is typically called when a session is closed.
     *
     * @param instanceIdOrWorldId The full instance ID or worldId (will be validated)
     * @param playerId The playerId to remove
     * @return true if player was removed, false if instance not found or player not active
     */
    @Transactional
    public boolean removePlayerAndDeleteIfEmpty(String instanceIdOrWorldId, String playerId) {
        if (instanceIdOrWorldId == null || instanceIdOrWorldId.isBlank()) {
            log.debug("Empty instanceId provided");
            return false;
        }

        if (playerId == null || playerId.isBlank()) {
            log.debug("Empty playerId provided");
            return false;
        }

        // Check if this is an instance worldId
        WorldId worldId;
        try {
            worldId = WorldId.unchecked(instanceIdOrWorldId);
        } catch (Exception e) {
            log.warn("Invalid worldId format: {}", instanceIdOrWorldId, e);
            return false;
        }

        if (!worldId.isInstance()) {
            log.debug("WorldId is not an instance, no cleanup needed: {}", instanceIdOrWorldId);
            return false;
        }

        // Find and validate instance
        Optional<WWorldInstance> instanceOpt = findByInstanceIdWithValidation(instanceIdOrWorldId);

        if (instanceOpt.isEmpty()) {
            log.warn("Instance not found or validation failed: {}", instanceIdOrWorldId);
            return false;
        }

        WWorldInstance instance = instanceOpt.get();

        // Remove player from active players
        boolean removed = instance.removeActivePlayer(playerId);

        if (removed) {
            log.info("Player {} removed from instance {} (active players: {})",
                    playerId, instanceIdOrWorldId, instance.getActivePlayerCount());

            // Check if instance is now empty
            if (instance.hasNoActivePlayers()) {
                log.info("Instance {} has no active players, deleting instance", instanceIdOrWorldId);
                delete(instanceIdOrWorldId);
                log.info("Instance {} deleted successfully", instanceIdOrWorldId);
            } else {
                // Save updated instance
                save(instance);
            }
            return true;
        } else {
            log.debug("Player {} was not in active players list for instance {}",
                    playerId, instanceIdOrWorldId);
            return false;
        }
    }

    /**
     * Notify all registered listeners that an instance was created.
     *
     * @param instance The created instance
     */
    private void notifyListenersCreated(WWorldInstance instance) {
        if (listeners == null || listeners.isEmpty()) {
            return;
        }

        WorldInstanceEvent event = WorldInstanceEvent.created(instance);
        for (WWorldInstanceListener listener : listeners) {
            try {
                listener.worldInstanceCreated(event);
            } catch (Exception e) {
                log.error("Error notifying listener {} about instance creation: {}",
                        listener.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
        log.debug("Notified {} listeners about instance creation: instanceId={}",
                listeners.size(), instance.getInstanceId());
    }

    /**
     * Notify all registered listeners that an instance was deleted.
     *
     * @param instance The deleted instance
     */
    private void notifyListenersDeleted(WWorldInstance instance) {
        if (listeners == null || listeners.isEmpty()) {
            return;
        }

        WorldInstanceEvent event = WorldInstanceEvent.deleted(instance);
        for (WWorldInstanceListener listener : listeners) {
            try {
                listener.worldInstanceDeleted(event);
            } catch (Exception e) {
                log.error("Error notifying listener {} about instance deletion: {}",
                        listener.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
        log.debug("Notified {} listeners about instance deletion: instanceId={}",
                listeners.size(), instance.getInstanceId());
    }
}
