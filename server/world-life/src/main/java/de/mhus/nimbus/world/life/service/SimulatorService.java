package de.mhus.nimbus.world.life.service;

import de.mhus.nimbus.generated.types.EntityPathway;
import de.mhus.nimbus.generated.types.Vector3;
import de.mhus.nimbus.generated.types.Waypoint;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.life.behavior.BehaviorRegistry;
import de.mhus.nimbus.world.life.behavior.EntityBehavior;
import de.mhus.nimbus.world.life.model.ChunkCoordinate;
import de.mhus.nimbus.world.life.model.SimulationState;
import de.mhus.nimbus.world.life.redis.PathwayPublisher;
import de.mhus.nimbus.world.shared.world.WEntity;
import de.mhus.nimbus.world.shared.world.WEntityService;
import de.mhus.nimbus.world.shared.world.WWorld;
import de.mhus.nimbus.world.shared.world.WWorldService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static de.mhus.nimbus.world.shared.world.BlockUtil.toChunkKey;

/**
 * Main entity simulation service.
 *
 * Supports multi-world simulation across all enabled worlds in MongoDB.
 *
 * Responsibilities:
 * - Load entities from database for all worlds
 * - Run simulation loop (every 1 second) for all worlds
 * - Manage entity simulation states per world
 * - Coordinate entity ownership across pods
 * - Generate pathways via behavior strategies
 * - Publish pathways to world-player pods
 *
 * Only simulates entities in active chunks (performance optimization).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SimulatorService {

    private final WEntityService entityService;
    private final BehaviorRegistry behaviorRegistry;
    private final MultiWorldChunkService multiWorldChunkService;
    private final PathwayPublisher pathwayPublisher;
    private final EntityOwnershipService ownershipService;
    private final WorldDiscoveryService worldDiscoveryService;
    private final WWorldService worldService;

    /**
     * Simulation states for all entities, grouped by world.
     * Maps worldId → (entityId → SimulationState)
     */
    private final Map<WorldId, Map<String, SimulationState>> worldSimulationStates = new ConcurrentHashMap<>();

    @PostConstruct
    public void initialize() {
        log.info("Initializing SimulatorService for multi-world support");
        loadAllWorldEntities();
    }

    /**
     * Load entities for all enabled worlds.
     * Called on startup and when worlds change.
     */
    @Scheduled(fixedDelay = 300000, initialDelay = 5000)
    public void loadAllWorldEntities() {
        Set<WorldId> knownWorlds = worldDiscoveryService.getKnownWorldIds();

        if (knownWorlds.isEmpty()) {
            log.debug("No worlds discovered yet, skipping entity load");
            return;
        }

        int totalInitialized = 0;
        int totalSkipped = 0;

        for (WorldId worldId : knownWorlds) {
            // Skip if already loaded
            if (worldSimulationStates.containsKey(worldId)) {
                continue;
            }

            log.info("Loading entities for world: {}", worldId);

            // Load all entities from database for this world
            List<WEntity> entities = entityService.findByWorldId(worldId);
            log.info("World {}: Loaded {} entities from database", worldId, entities.size());

            // Create simulation state map for this world
            Map<String, SimulationState> worldStates = new ConcurrentHashMap<>();

            // Initialize simulation state for each entity
            int initializedCount = 0;
            int missingPositionCount = 0;

            for (WEntity entity : entities) {
                if (!entity.isEnabled()) {
                    continue;
                }
                // Check if entity has position set
                if (entity.getEntityId().startsWith("@")) {
                    // Skip system/internal entities
                    log.info("World {}: Skipping player entity {} from simulation",
                            worldId, entity.getEntityId());
                    continue;
                }
                if (entity.getPosition() == null) {
                    log.warn("World {}: Entity {} has no position set, skipping simulation",
                            worldId, entity.getEntityId());
                    missingPositionCount++;
                    continue;
                }

                SimulationState state = new SimulationState(entity);
                worldStates.put(entity.getEntityId(), state);
                initializedCount++;
            }

            worldSimulationStates.put(worldId, worldStates);

            log.info("World {}: Initialized {} entities for simulation ({} skipped - no position)",
                    worldId, initializedCount, missingPositionCount);

            totalInitialized += initializedCount;
            totalSkipped += missingPositionCount;
        }

        // Remove worlds that are no longer known
        Set<WorldId> toRemove = new HashSet<>(worldSimulationStates.keySet());
        toRemove.removeAll(knownWorlds);
        for (WorldId worldId : toRemove) {
            worldSimulationStates.remove(worldId);
            log.info("Removed simulation states for disabled world: {}", worldId);
        }

        if (totalInitialized > 0 || totalSkipped > 0) {
            log.info("SimulatorService: {} total entities across {} worlds ({} skipped)",
                    totalInitialized, knownWorlds.size(), totalSkipped);
        }
    }

    /**
     * Main simulation loop.
     * Runs every second (configurable via world.life.simulation-interval-ms).
     *
     * Simulates all entities across all enabled worlds.
     *
     * For each entity in active chunks:
     * 1. Check chunk is active
     * 2. Claim ownership if needed
     * 3. Simulate entity (generate pathway)
     * 4. Publish pathways to Redis
     */
    @Scheduled(fixedDelayString = "#{${world.life.simulation-interval-ms:1000}}")
    public void simulationLoop() {
        long currentTime = System.currentTimeMillis();

        // Process each world separately
        for (Map.Entry<WorldId, Map<String, SimulationState>> worldEntry : worldSimulationStates.entrySet()) {
            WorldId worldId = worldEntry.getKey();
            Map<String, SimulationState> simulationStates = worldEntry.getValue();

            try {
                simulateWorld(worldId, simulationStates, currentTime);
            } catch (Exception e) {
                log.error("Error simulating world {}: {}", worldId, e.getMessage(), e);
            }
        }
    }

    /**
     * Simulate entities for a single world.
     *
     * @param worldId World ID
     * @param simulationStates Entity simulation states for this world
     * @param currentTime Current timestamp
     */
    private void simulateWorld(WorldId worldId, Map<String, SimulationState> simulationStates, long currentTime) {
        Set<ChunkCoordinate> activeChunks = multiWorldChunkService.getActiveChunks(worldId);

        if (activeChunks.isEmpty()) {
            log.trace("World {}: No active chunks, skipping simulation", worldId);
            return;
        }

        List<EntityPathway> newPathways = new ArrayList<>();
        WWorld world = worldService.getByWorldId(worldId).get();

        for (Map.Entry<String, SimulationState> entry : simulationStates.entrySet()) {
            String entityId = entry.getKey();
            SimulationState state = entry.getValue();
            WEntity entity = state.getEntity();

            try {
                // 1. Check if entity is in an active chunk
                String entityChunk = toChunkKey(world, entity.getPosition());
                if (entityChunk == null || !multiWorldChunkService.isChunkActive(worldId, entityChunk)) {
                    // Entity chunk is not active, release ownership if we own it
                    if (ownershipService.isOwnedByThisPod(worldId, entityId)) {
                        ownershipService.releaseEntity(worldId, entityId);
                        log.trace("World {}: Released entity {} (chunk {} no longer active)",
                                worldId, entityId, entityChunk);
                    }
                    continue;
                }

                // 2. Try to claim ownership if not already owned
                if (!ownershipService.isOwnedByThisPod(worldId, entityId)) {
                    boolean claimed = ownershipService.claimEntity(worldId, entityId, entityChunk);
                    if (!claimed) {
                        // Another pod owns this entity, skip simulation
                        continue;
                    }
                    log.debug("World {}: Claimed entity {} in chunk {}", worldId, entityId, entityChunk);
                }

                // 3. Simulate entity
                Optional<EntityPathway> pathway = simulateEntity(entity, state, currentTime, worldId);
                pathway.ifPresent(newPathways::add);

            } catch (Exception e) {
                log.error("World {}: Error simulating entity {}: {}", worldId, entityId, e.getMessage(), e);
            }
        }

        // 4. Publish pathways to Redis
        if (!newPathways.isEmpty()) {
            Set<ChunkCoordinate> affectedChunks = calculateAffectedChunks(world, newPathways);
            pathwayPublisher.publishPathways(worldId, newPathways, affectedChunks);

            log.debug("World {}: Generated {} pathways, affecting {} chunks",
                    worldId, newPathways.size(), affectedChunks.size());
        }
    }

    /**
     * Simulate a single entity and generate pathway if needed.
     *
     * @param entity Entity to simulate
     * @param state Simulation state
     * @param currentTime Current time
     * @param worldId World ID
     * @return Optional pathway if generated
     */
    private Optional<EntityPathway> simulateEntity(WEntity entity, SimulationState state, long currentTime, WorldId worldId) {
        // Get behavior for entity
        String behaviorType = getBehaviorType(entity);
        EntityBehavior behavior = behaviorRegistry.getBehavior(behaviorType);

        if (behavior == null) {
            log.warn("World {}: Behavior not found: {}, entity: {}", worldId, behaviorType, entity.getEntityId());
            return Optional.empty();
        }
        var world = worldService.getByWorldId(worldId).orElseThrow();

        // Generate pathway
        EntityPathway pathway = behavior.update(entity, state, currentTime, worldId);

        if (pathway != null) {
            // Update in-memory position to last waypoint target
            List<Waypoint> waypoints = pathway.getWaypoints();
            if (waypoints != null && !waypoints.isEmpty()) {
                Waypoint lastWaypoint = waypoints.get(waypoints.size() - 1);
                entity.setPosition(lastWaypoint.getTarget());

                // Update chunk if entity moved to different chunk
                updateEntityChunk(world, entity);
            }

            // Update simulation state
            state.setLastPathwayTime(currentTime);
            state.setCurrentPathway(pathway);
            state.updatePathwayEndTime();

            log.trace("Generated pathway for entity {}: {} waypoints",
                    entity.getEntityId(),
                    pathway.getWaypoints() != null ? pathway.getWaypoints().size() : 0);

            return Optional.of(pathway);
        }

        return Optional.empty();
    }

    /**
     * Get behavior type for entity.
     * Reads from entity.behaviorModel field, defaults to PreyAnimalBehavior.
     *
     * @param entity Entity
     * @return Behavior type identifier
     */
    private String getBehaviorType(WEntity entity) {
        String behaviorModel = entity.getBehaviorModel();
        return (behaviorModel != null && !behaviorModel.isBlank()) ? behaviorModel : "PreyAnimalBehavior";
    }

    /**
     * Calculate which chunks are affected by pathways.
     * Used to filter which sessions receive pathway updates.
     *
     * @param pathways List of pathways
     * @return Set of chunk coordinates
     */
    private Set<ChunkCoordinate> calculateAffectedChunks(WWorld world, List<EntityPathway> pathways) {
        var chunkSize = world.getPublicData().getChunkSize();
        Set<ChunkCoordinate> chunks = new HashSet<>();

        for (EntityPathway pathway : pathways) {
            if (pathway.getWaypoints() == null) {
                continue;
            }

            for (Waypoint waypoint : pathway.getWaypoints()) {
                Vector3 target = waypoint.getTarget();
                if (target == null) {
                    continue;
                }

                int cx = (int) Math.floor(target.getX() / chunkSize);
                int cz = (int) Math.floor(target.getZ() / chunkSize);
                chunks.add(new ChunkCoordinate(cx, cz));
            }
        }

        return chunks;
    }

    /**
     * Try to claim an orphaned entity if it's in an active chunk.
     * Called by OrphanDetectionTask.
     *
     * @param entityId Entity identifier
     */
    public void tryClaimOrphanedEntity(String entityId) {
        // Search for entity across all worlds
        for (Map.Entry<WorldId, Map<String, SimulationState>> worldEntry : worldSimulationStates.entrySet()) {
            WorldId worldId = worldEntry.getKey();
            Optional<WWorld> worldOpt = worldService.getByWorldId(worldId);
            Map<String, SimulationState> simulationStates = worldEntry.getValue();

            SimulationState state = simulationStates.get(entityId);
            if (state == null) {
                continue; // Not in this world
            }

            WEntity entity = state.getEntity();
            String entityChunk = toChunkKey(worldOpt.get(), entity.getPosition());

            if (entityChunk == null) {
                log.debug("Entity {} has no chunk information", entityId);
                return;
            }

            if (!multiWorldChunkService.isChunkActive(worldId, entityChunk)) {
                log.trace("Entity {} chunk not active in world {}, not claiming: chunk {}",
                        entityId, worldId, entityChunk);
                return;
            }

            // Try to claim entity
            boolean claimed = ownershipService.claimEntity(worldId, entityId, entityChunk);
            if (claimed) {
                log.info("Claimed orphaned entity {} in world {} chunk {}", entityId, worldId, entityChunk);
            }
            return;
        }

        log.debug("Entity not found in any world simulation states: {}", entityId);
    }

    /**
     * Get number of entities being simulated across all worlds.
     *
     * @return Total entity count
     */
    public int getEntityCount() {
        return worldSimulationStates.values().stream()
                .mapToInt(Map::size)
                .sum();
    }

    /**
     * Get number of entities being simulated per world.
     *
     * @return Map of worldId → entity count
     */
    public Map<WorldId, Integer> getEntityCountPerWorld() {
        Map<WorldId, Integer> counts = new HashMap<>();
        worldSimulationStates.forEach((worldId, states) -> counts.put(worldId, states.size()));
        return counts;
    }

    /**
     * Get number of entities actively owned by this pod.
     *
     * @return Owned entity count
     */
    public int getOwnedEntityCount() {
        return ownershipService.getOwnedEntityCount();
    }

    /**
     * Update entity chunk based on current position.
     * Recalculates chunk coordinates and updates if changed.
     *
     * @param entity Entity to update
     * @param worldId World ID
     */
    private void updateEntityChunk(WWorld world, WEntity entity) {
        if (entity.getPosition() == null) {
            return;
        }
        var chunkSize = world.getPublicData().getChunkSize();

        int cx = (int) Math.floor(entity.getPosition().getX() / chunkSize);
        int cz = (int) Math.floor(entity.getPosition().getZ() / chunkSize);
        String newChunk = cx + ":" + cz;

    }

    /**
     * Snapshot entity positions to database periodically.
     * Runs every 60 seconds to persist in-memory position changes.
     * Snapshots entities from all worlds.
     */
//    public void snapshotEntityPositions() {
//        List<WEntity> toUpdate = new ArrayList<>();
//
//        for (Map.Entry<WorldId, Map<String, SimulationState>> worldEntry : worldSimulationStates.entrySet()) {
//            WorldId worldId = worldEntry.getKey();
//            Map<String, SimulationState> simulationStates = worldEntry.getValue();
//
//            for (SimulationState state : simulationStates.values()) {
//                WEntity entity = state.getEntity();
//
//                // Only snapshot entities owned by this pod
//                if (ownershipService.isOwnedByThisPod(worldId, entity.getEntityId())) {
//                    toUpdate.add(entity);
//                }
//            }
//        }
//
//        if (!toUpdate.isEmpty()) {
//            entityService.saveAll(toUpdate);
//            log.info("Snapshotted {} entity positions to database across all worlds", toUpdate.size());
//        }
//    }
}
