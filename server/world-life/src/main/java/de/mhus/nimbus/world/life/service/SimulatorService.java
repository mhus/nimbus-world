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
import de.mhus.nimbus.world.shared.gameplay.BaseEffectProcessor;
import de.mhus.nimbus.world.shared.gameplay.EntityCombatData;
import de.mhus.nimbus.world.shared.redis.EntityStateRedisService;
import de.mhus.nimbus.world.shared.redis.EntityStatusPublisher;
import de.mhus.nimbus.world.shared.redis.VitalDeltaBroadcastMessage;
import de.mhus.nimbus.world.shared.redis.VitalDeltaPublisher;
import de.mhus.nimbus.world.shared.world.BlockUtil;
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
import java.util.stream.Collectors;

/**
 * Main entity simulation service.
 *
 * Supports multi-world simulation across all enabled worlds.
 * Entities are loaded/unloaded dynamically based on chunk activation/deactivation
 * events from MultiWorldChunkService.
 *
 * Responsibilities:
 * - Load entities when chunks activate (chunk-based)
 * - Unload entities when all their chunks deactivate
 * - Run simulation loop (every 1 second) for all worlds
 * - Manage entity simulation states per world
 * - Coordinate entity ownership across pods
 * - Generate pathways via behavior strategies
 * - Publish pathways to world-player pods
 * - Send Gone signals when entities are unloaded
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SimulatorService implements MultiWorldChunkService.WorldChunkChangeListener {

    private final WEntityService entityService;
    private final BehaviorRegistry behaviorRegistry;
    private final MultiWorldChunkService multiWorldChunkService;
    private final PathwayPublisher pathwayPublisher;
    private final EntityOwnershipService ownershipService;
    private final EntityStatusPublisher entityStatusPublisher;
    private final WWorldService worldService;
    private final VitalDeltaPublisher vitalDeltaPublisher;
    private final EntityStateRedisService entityStateRedisService;

    private final BaseEffectProcessor baseEffectProcessor = new BaseEffectProcessor();

    /**
     * Simulation states for all entities, grouped by world.
     * Maps worldId → (entityId → SimulationState)
     */
    private final Map<WorldId, Map<String, SimulationState>> worldSimulationStates = new ConcurrentHashMap<>();

    /**
     * Tracks which active chunks each entity references.
     * worldId → entityId → Set of chunkKeys
     * When all chunks for an entity are deactivated, the entity is unloaded.
     */
    private final Map<WorldId, Map<String, Set<String>>> entityActiveChunkRefs = new ConcurrentHashMap<>();

    @PostConstruct
    public void initialize() {
        log.info("Initializing SimulatorService — registering as WorldChunkChangeListener");
        multiWorldChunkService.addWorldChunkChangeListener(this);
    }

    @Override
    public void onChunksActivated(WorldId worldId, Set<ChunkCoordinate> added) {
        if (added == null || added.isEmpty()) return;

        Set<String> chunkKeys = added.stream()
                .map(ChunkCoordinate::toKey)
                .collect(Collectors.toSet());

        List<WEntity> entities = entityService.findEnabledByChunks(worldId, chunkKeys);

        Map<String, SimulationState> worldStates = worldSimulationStates
                .computeIfAbsent(worldId, k -> new ConcurrentHashMap<>());
        Map<String, Set<String>> chunkRefs = entityActiveChunkRefs
                .computeIfAbsent(worldId, k -> new ConcurrentHashMap<>());

        int loaded = 0;
        for (WEntity entity : entities) {
            String entityId = entity.getEntityId();

            // Skip system/player entities
            if (entityId.startsWith("@")) continue;
            // Skip entities without position
            if (entity.getPosition() == null) continue;

            // Track active chunk refs for this entity
            Set<String> entityChunks = chunkRefs.computeIfAbsent(entityId, k -> ConcurrentHashMap.newKeySet());
            entityChunks.addAll(chunkKeys);

            // Only create simulation state if not already loaded
            if (worldStates.putIfAbsent(entityId, createSimulationState(entity)) == null) {
                loaded++;
            }
        }

        if (loaded > 0) {
            log.info("World {}: Chunk activation loaded {} new entities ({} chunks activated)",
                    worldId, loaded, added.size());
        }
    }

    @Override
    public void onChunksDeactivated(WorldId worldId, Set<ChunkCoordinate> removed) {
        if (removed == null || removed.isEmpty()) return;

        Set<String> removedKeys = removed.stream()
                .map(ChunkCoordinate::toKey)
                .collect(Collectors.toSet());

        Map<String, SimulationState> worldStates = worldSimulationStates.get(worldId);
        Map<String, Set<String>> chunkRefs = entityActiveChunkRefs.get(worldId);

        if (chunkRefs == null || worldStates == null) return;

        List<String> entitiesToUnload = new ArrayList<>();

        for (Map.Entry<String, Set<String>> entry : chunkRefs.entrySet()) {
            String entityId = entry.getKey();
            Set<String> entityChunks = entry.getValue();

            // Remove deactivated chunks from this entity's active set
            entityChunks.removeAll(removedKeys);

            // If entity has no more active chunks, schedule for unload
            if (entityChunks.isEmpty()) {
                entitiesToUnload.add(entityId);
            }
        }

        for (String entityId : entitiesToUnload) {
            SimulationState state = worldStates.remove(entityId);
            chunkRefs.remove(entityId);

            // Release ownership
            ownershipService.releaseEntity(worldId, entityId);

            // Clean up Redis caches
            List<String> affectedChunks = state != null && state.getEntity().getAffectedChunks() != null
                    ? state.getEntity().getAffectedChunks()
                    : List.of();
            pathwayPublisher.cleanupEntityFromRedis(worldId, entityId, affectedChunks);

            // Send Gone signal to world-player
            entityStatusPublisher.publishStatusUpdate(
                    worldId.getId(), entityId, Map.of(EntityStatusPublisher.GONE, 1), null);

            log.debug("World {}: Unloaded entity {} (all chunks deactivated)", worldId, entityId);
        }

        if (!entitiesToUnload.isEmpty()) {
            log.info("World {}: Chunk deactivation unloaded {} entities ({} chunks removed)",
                    worldId, entitiesToUnload.size(), removed.size());
        }
    }

    /**
     * Main simulation loop.
     * Runs every second (configurable via world.life.simulation-interval-ms).
     *
     * Simulates all entities across all enabled worlds.
     */
    @Scheduled(fixedDelayString = "#{${world.life.simulation-interval-ms:1000}}")
    public void simulationLoop() {
        long currentTime = System.currentTimeMillis();

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
     * Periodic diagnostic counter — logs state every 60 ticks (~1 minute).
     */
    private long diagnosticCounter = 0;

    /**
     * Simulate entities for a single world.
     */
    private void simulateWorld(WorldId worldId, Map<String, SimulationState> simulationStates, long currentTime) {
        Set<ChunkCoordinate> activeChunks = multiWorldChunkService.getActiveChunks(worldId);

        boolean diagnosticTick = (diagnosticCounter++ % 60 == 0);

        if (activeChunks.isEmpty()) {
            if (diagnosticTick && !simulationStates.isEmpty()) {
                log.info("World {}: DIAG no active chunks, {} entities in memory but not simulated",
                        worldId, simulationStates.size());
            }
            return;
        }

        if (diagnosticTick) {
            log.info("World {}: DIAG {} active chunks, {} entities loaded",
                    worldId, activeChunks.size(), simulationStates.size());
        }

        List<EntityPathway> newPathways = new ArrayList<>();
        WWorld world = worldService.getByWorldId(worldId).get();
        int skippedOwnership = 0;
        int skippedNoPathway = 0;

        for (Map.Entry<String, SimulationState> entry : simulationStates.entrySet()) {
            String entityId = entry.getKey();
            SimulationState state = entry.getValue();
            WEntity entity = state.getEntity();

            try {
                // Entity is in worldSimulationStates → it was loaded by onChunksActivated.
                // No need to re-check chunk activity here; unloading is handled by onChunksDeactivated.
                String entityChunk = BlockUtil.toChunkKey(world, entity.getPosition());

                // Try to claim ownership if not already owned
                if (!ownershipService.isOwnedByThisPod(worldId, entityId)) {
                    boolean claimed = ownershipService.claimEntity(worldId, entityId, entityChunk);
                    if (!claimed) {
                        skippedOwnership++;
                        continue;
                    }
                    log.debug("World {}: Claimed entity {} in chunk {}", worldId, entityId, entityChunk);
                }

                // Simulate entity
                Optional<EntityPathway> pathway = simulateEntity(entity, state, currentTime, worldId);
                if (pathway.isPresent()) {
                    newPathways.add(pathway.get());
                } else {
                    skippedNoPathway++;
                }

            } catch (Exception e) {
                log.error("World {}: Error simulating entity {}: {}", worldId, entityId, e.getMessage(), e);
            }
        }

        if (diagnosticTick) {
            log.info("World {}: DIAG tick result: {} pathways, skipped: {} ownership, {} no-pathway",
                    worldId, newPathways.size(), skippedOwnership, skippedNoPathway);
        }

        // Publish pathways to Redis
        if (!newPathways.isEmpty()) {
            Set<ChunkCoordinate> affectedChunks = calculateAffectedChunks(world, newPathways);
            pathwayPublisher.publishPathways(worldId, newPathways, affectedChunks);

            log.debug("World {}: Generated {} pathways, affecting {} chunks",
                    worldId, newPathways.size(), affectedChunks.size());
        }
    }

    /**
     * Simulate a single entity and generate pathway if needed.
     */
    private Optional<EntityPathway> simulateEntity(WEntity entity, SimulationState state, long currentTime, WorldId worldId) {

        // Handle death/respawn lifecycle
        if (state.getLifecycleState() != SimulationState.LifecycleState.ALIVE) {
            return handleLifecycleTick(entity, state, currentTime, worldId);
        }

        String behaviorType = getBehaviorType(entity);
        EntityBehavior behavior = behaviorRegistry.getBehavior(behaviorType);

        if (behavior == null) {
            log.warn("World {}: Behavior not found: {}, entity: {}", worldId, behaviorType, entity.getEntityId());
            return Optional.empty();
        }
        var world = worldService.getByWorldId(worldId).orElseThrow();

        EntityPathway pathway = behavior.update(entity, state, currentTime, worldId);

        // Process combat tick for entities with combat data
        processCombatTick(state, 1.0, worldId);

        if (pathway != null) {
            List<Waypoint> waypoints = pathway.getWaypoints();
            if (waypoints != null && !waypoints.isEmpty()) {
                Waypoint lastWaypoint = waypoints.get(waypoints.size() - 1);
                entity.setPosition(lastWaypoint.getTarget());
                updateEntityChunk(world, entity);
            }

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
     * Handle lifecycle transitions for dead/gone entities.
     * DEAD → (fade time) → GONE → (respawn time) → ALIVE
     */
    private Optional<EntityPathway> handleLifecycleTick(WEntity entity, SimulationState state, long currentTime, WorldId worldId) {
        long elapsed = currentTime - state.getLifecycleTimestamp();
        String entityId = entity.getEntityId();

        if (state.getLifecycleState() == SimulationState.LifecycleState.DEAD) {
            if (elapsed >= state.getFadeTimeMs()) {
                // Fade time over → send gone, transition to GONE
                entityStatusPublisher.publishStatusUpdate(worldId.getId(), entityId,
                        Map.of(EntityStatusPublisher.GONE, 1), null);
                entityStateRedisService.setLifecycle(worldId.getId(), entityId,
                        EntityStateRedisService.LIFECYCLE_GONE);
                state.setLifecycleState(SimulationState.LifecycleState.GONE);
                state.setLifecycleTimestamp(currentTime);
                log.info("World {}: Entity {} gone after death fade", worldId, entityId);
            }
        } else if (state.getLifecycleState() == SimulationState.LifecycleState.GONE) {
            if (elapsed >= state.getRespawnTimeMs()) {
                // Respawn time over → reset and respawn
                respawnEntity(entity, state, currentTime, worldId);
                return simulateEntity(entity, state, currentTime, worldId);
            }
        }
        return Optional.empty();
    }

    /**
     * Respawn entity: reset health, position to middlePoint, and lifecycle to ALIVE.
     */
    private void respawnEntity(WEntity entity, SimulationState state, long currentTime, WorldId worldId) {
        String entityId = entity.getEntityId();

        // Reset health
        EntityCombatData combatData = state.getCombatData();
        if (combatData != null) {
            var health = combatData.getVital("health");
            if (health != null) {
                health.setCurrent(health.getBase());
                health.clamp();
            }
            combatData.getActiveEffects().removeIf(e -> !e.isPermanent());
        }

        // Reset position to middlePoint (spawn point)
        if (entity.getMiddlePoint() != null) {
            entity.setPosition(Vector3.builder()
                    .x(entity.getMiddlePoint().getX())
                    .y(entity.getMiddlePoint().getY())
                    .z(entity.getMiddlePoint().getZ())
                    .build());
            var world = worldService.getByWorldId(worldId).orElse(null);
            if (world != null) {
                updateEntityChunk(world, entity);
            }
        }

        // Reset lifecycle
        state.setLifecycleState(SimulationState.LifecycleState.ALIVE);
        state.setLifecycleTimestamp(0);
        state.setCurrentPathway(null);
        state.setPathwayEndTime(0);

        // Clear Redis state (absence = ALIVE)
        entityStateRedisService.remove(worldId.getId(), entityId);

        log.info("World {}: Entity {} respawned at middlePoint", worldId, entityId);
    }

    private String getBehaviorType(WEntity entity) {
        String behaviorModel = entity.getBehaviorModel();
        return (behaviorModel != null && !behaviorModel.isBlank()) ? behaviorModel : "PreyAnimalBehavior";
    }

    private Set<ChunkCoordinate> calculateAffectedChunks(WWorld world, List<EntityPathway> pathways) {
        var chunkSize = world.getPublicData().getChunkSize();
        Set<ChunkCoordinate> chunks = new HashSet<>();

        for (EntityPathway pathway : pathways) {
            if (pathway.getWaypoints() == null) continue;

            for (Waypoint waypoint : pathway.getWaypoints()) {
                Vector3 target = waypoint.getTarget();
                if (target == null) continue;

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
     */
    public void tryClaimOrphanedEntity(String entityId) {
        for (Map.Entry<WorldId, Map<String, SimulationState>> worldEntry : worldSimulationStates.entrySet()) {
            WorldId worldId = worldEntry.getKey();
            Optional<WWorld> worldOpt = worldService.getByWorldId(worldId);
            Map<String, SimulationState> simulationStates = worldEntry.getValue();

            SimulationState state = simulationStates.get(entityId);
            if (state == null) continue;

            WEntity entity = state.getEntity();
            String entityChunk = BlockUtil.toChunkKey(worldOpt.get(), entity.getPosition());

            if (entityChunk == null) {
                log.debug("Entity {} has no chunk information", entityId);
                return;
            }

            if (!multiWorldChunkService.isChunkActive(worldId, entityChunk)) {
                log.trace("Entity {} chunk not active in world {}, not claiming: chunk {}",
                        entityId, worldId, entityChunk);
                return;
            }

            boolean claimed = ownershipService.claimEntity(worldId, entityId, entityChunk);
            if (claimed) {
                log.info("Claimed orphaned entity {} in world {} chunk {}", entityId, worldId, entityChunk);
            }
            return;
        }

        log.debug("Entity not found in any world simulation states: {}", entityId);
    }

    /**
     * Find a simulation state by world and entity ID.
     *
     * @param worldId  World ID
     * @param entityId Entity ID (e.g., "cow2")
     * @return SimulationState or null if not loaded on this pod
     */
    public SimulationState findSimulationState(WorldId worldId, String entityId) {
        Map<String, SimulationState> worldStates = worldSimulationStates.get(worldId);
        if (worldStates == null) return null;
        return worldStates.get(entityId);
    }

    public int getEntityCount() {
        return worldSimulationStates.values().stream()
                .mapToInt(Map::size)
                .sum();
    }

    public Map<WorldId, Integer> getEntityCountPerWorld() {
        Map<WorldId, Integer> counts = new HashMap<>();
        worldSimulationStates.forEach((worldId, states) -> counts.put(worldId, states.size()));
        return counts;
    }

    public int getOwnedEntityCount() {
        return ownershipService.getOwnedEntityCount();
    }

    /**
     * Create a SimulationState and initialize combat data from entity properties if available.
     */
    private SimulationState createSimulationState(WEntity entity) {
        SimulationState state = new SimulationState(entity);
        if (entity.getServer() != null) {
            EntityCombatData combatData = EntityCombatData.fromEntityProperties(entity.getServer());
            if (combatData != null) {
                state.setCombatData(combatData);
                log.debug("Initialized combat data for entity {}: health={}",
                        entity.getEntityId(),
                        combatData.getVital("health") != null ? combatData.getVital("health").getBase() : "none");
            }
        }
        return state;
    }

    /**
     * Process combat tick for an entity's combat data.
     * Runs effects, regen, and death check.
     */
    private void processCombatTick(SimulationState state, double deltaSeconds, WorldId worldId) {
        EntityCombatData combatData = state.getCombatData();
        if (combatData == null) return;

        List<VitalDeltaBroadcastMessage> outgoingDeltas = new ArrayList<>();
        String entityId = state.getEntity().getEntityId();

        boolean died = baseEffectProcessor.processTick(
                combatData, deltaSeconds, outgoingDeltas, worldId.getId(), entityId);

        // Publish outgoing deltas
        if (!outgoingDeltas.isEmpty()) {
            vitalDeltaPublisher.publishDeltas(outgoingDeltas);
        }

        if (died) {
            log.info("World {}: Entity {} died", worldId, entityId);
            handleEntityDeath(state, worldId);
        }
    }

    /**
     * Handle entity death: publish death status and start death/respawn lifecycle.
     */
    private void handleEntityDeath(SimulationState state, WorldId worldId) {
        String entityId = state.getEntity().getEntityId();

        // Publish death status to clients
        entityStatusPublisher.publishStatusUpdate(worldId.getId(), entityId,
                Map.of("health", 0.0, "healthMax", 0.0, "death", 1), null);

        // Store lifecycle in Redis for cross-pod access
        entityStateRedisService.updateState(worldId.getId(), entityId,
                EntityStateRedisService.LIFECYCLE_DEAD, 0.0, 0.0);

        // Transition to DEAD lifecycle state
        state.setLifecycleState(SimulationState.LifecycleState.DEAD);
        state.setLifecycleTimestamp(System.currentTimeMillis());
        state.setCurrentPathway(null);

        log.info("World {}: Entity {} died, fade time {}s, respawn time {}s",
                worldId, entityId,
                state.getFadeTimeMs() / 1000,
                state.getRespawnTimeMs() / 1000);
    }

    private void updateEntityChunk(WWorld world, WEntity entity) {
        if (entity.getPosition() == null) return;
        var chunkSize = world.getPublicData().getChunkSize();

        int cx = (int) Math.floor(entity.getPosition().getX() / chunkSize);
        int cz = (int) Math.floor(entity.getPosition().getZ() / chunkSize);
        String newChunk = cx + ":" + cz;
    }
}
