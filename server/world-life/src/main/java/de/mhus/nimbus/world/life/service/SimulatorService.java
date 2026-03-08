package de.mhus.nimbus.world.life.service;

import de.mhus.nimbus.generated.types.EntityPathway;
import de.mhus.nimbus.generated.types.Vector3;
import de.mhus.nimbus.generated.types.Waypoint;
import de.mhus.nimbus.shared.types.WorldId;
import de.mhus.nimbus.world.life.behavior.BehaviorRegistry;
import de.mhus.nimbus.world.life.behavior.CombatBehaviorHandler;
import de.mhus.nimbus.world.life.behavior.EntityBehavior;
import de.mhus.nimbus.world.life.model.ChunkCoordinate;
import de.mhus.nimbus.world.life.model.SimulationState;
import de.mhus.nimbus.world.life.redis.PathwayPublisher;
import de.mhus.nimbus.world.shared.gameplay.CombatConstants;
import de.mhus.nimbus.world.shared.gameplay.BaseEffectProcessor;
import de.mhus.nimbus.world.shared.gameplay.EntityCombatData;
import de.mhus.nimbus.world.shared.gameplay.VitalValue;
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
import java.util.LinkedHashMap;
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
    private final CombatBehaviorHandler combatBehaviorHandler;
    private final de.mhus.nimbus.world.shared.world.WItemService itemService;

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

    /**
     * Cached WWorld instances per worldId.
     * Loaded on first access per world, invalidated when world is removed.
     */
    private final Map<WorldId, WWorld> worldCache = new ConcurrentHashMap<>();

    /**
     * Get cached WWorld for a worldId. Loads from DB on first access.
     */
    private WWorld getCachedWorld(WorldId worldId) {
        return worldCache.computeIfAbsent(worldId, id ->
                worldService.getByWorldId(id).orElse(null));
    }

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
            if (worldStates.putIfAbsent(entityId, createSimulationState(worldId, entity)) == null) {
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
        WWorld world = getCachedWorld(worldId);
        if (world == null) {
            log.warn("World {}: World not found in cache or DB, skipping simulation", worldId);
            return;
        }
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

        // Check combat mode
        if (state.isInCombat()) {
            if (currentTime >= state.getCombatEndTime()) {
                state.exitCombat();
                log.debug("World {}: Entity {} combat ended (timeout)", worldId, entity.getEntityId());
            } else {
                // Generate combat pathway
                EntityPathway combatPathway = combatBehaviorHandler.generateCombatPathway(entity, state, currentTime, worldId);
                if (combatPathway != null) {
                    return finishPathway(entity, state, combatPathway, currentTime, worldId);
                }
            }
        }

        String behaviorType = getBehaviorType(entity);
        EntityBehavior behavior = behaviorRegistry.getBehavior(behaviorType);

        if (behavior == null) {
            log.warn("World {}: Behavior not found: {}, entity: {}", worldId, behaviorType, entity.getEntityId());
            return Optional.empty();
        }
        EntityPathway pathway = behavior.update(entity, state, currentTime, worldId);

        // Process combat tick for entities with combat data
        processCombatTick(state, 1.0, worldId);

        if (pathway != null) {
            return finishPathway(entity, state, pathway, currentTime, worldId);
        }

        return Optional.empty();
    }

    /**
     * Finalize a pathway: update entity position, state, and return as Optional.
     */
    private Optional<EntityPathway> finishPathway(WEntity entity, SimulationState state,
                                                   EntityPathway pathway, long currentTime, WorldId worldId) {
        List<Waypoint> waypoints = pathway.getWaypoints();
        if (waypoints != null && !waypoints.isEmpty()) {
            Waypoint lastWaypoint = waypoints.get(waypoints.size() - 1);
            entity.setPosition(lastWaypoint.getTarget());
            var world = getCachedWorld(worldId);
            if (world != null) {
                updateEntityChunk(world, entity);
            }
        }

        state.setLastPathwayTime(currentTime);
        state.setCurrentPathway(pathway);
        state.updatePathwayEndTime();

        log.trace("Generated pathway for entity {}: {} waypoints",
                entity.getEntityId(),
                pathway.getWaypoints() != null ? pathway.getWaypoints().size() : 0);

        return Optional.of(pathway);
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
            var world = getCachedWorld(worldId);
            if (world != null) {
                updateEntityChunk(world, entity);
            }
        }

        // Reset lifecycle, combat, and attackers
        state.setLifecycleState(SimulationState.LifecycleState.ALIVE);
        state.setLifecycleTimestamp(0);
        state.setCurrentPathway(null);
        state.exitCombat();
        state.setPathwayEndTime(0);
        state.getAttackers().clear();

        // Clear Redis state (absence = ALIVE, also removes looters)
        entityStateRedisService.removeAll(worldId.getId(), entityId);

        // Publish initial health after respawn
        if (combatData != null) {
            VitalValue health = combatData.getVital("health");
            if (health != null) {
                publishHealthStatus(worldId, entityId, health);
            }
        }

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
            WWorld world = getCachedWorld(worldId);
            Map<String, SimulationState> simulationStates = worldEntry.getValue();

            SimulationState state = simulationStates.get(entityId);
            if (state == null) continue;
            if (world == null) continue;

            WEntity entity = state.getEntity();
            String entityChunk = BlockUtil.toChunkKey(world, entity.getPosition());

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

    public Map<String, SimulationState> getSimulationStates(WorldId worldId) {
        return worldSimulationStates.get(worldId);
    }

    /**
     * Reload all entities for a world from DB.
     * Clears existing simulation states and re-loads from currently active chunks.
     *
     * @return number of entities loaded
     */
    public int reloadEntities(WorldId worldId) {
        // Invalidate world cache so it's reloaded from DB
        worldCache.remove(worldId);

        // Clear existing states
        Map<String, SimulationState> worldStates = worldSimulationStates.get(worldId);
        if (worldStates != null) {
            worldStates.clear();
        }
        Map<String, Set<String>> chunkRefs = entityActiveChunkRefs.get(worldId);
        if (chunkRefs != null) {
            chunkRefs.clear();
        }

        // Re-load from active chunks
        Set<ChunkCoordinate> activeChunks = multiWorldChunkService.getActiveChunks(worldId);
        if (activeChunks.isEmpty()) {
            log.info("World {}: No active chunks for reload", worldId);
            return 0;
        }

        onChunksActivated(worldId, activeChunks);

        int count = worldSimulationStates.containsKey(worldId) ? worldSimulationStates.get(worldId).size() : 0;
        log.info("World {}: Reloaded {} entities from {} active chunks", worldId, count, activeChunks.size());
        return count;
    }

    /**
     * Trigger an immediate combat tick for an entity that just entered combat via spread.
     * Generates a combat pathway and publishes it right away, so the entity reacts
     * without waiting for the next scheduled simulation tick.
     */
    public void triggerImmediateCombatTick(WorldId worldId, SimulationState state) {
        WEntity entity = state.getEntity();
        if (state.getLifecycleState() != SimulationState.LifecycleState.ALIVE) return;
        if (!state.isInCombat()) return;

        long currentTime = System.currentTimeMillis();
        EntityPathway combatPathway = combatBehaviorHandler.generateCombatPathway(entity, state, currentTime, worldId);
        if (combatPathway == null) return;

        finishPathway(entity, state, combatPathway, currentTime, worldId);

        // Publish single pathway immediately
        WWorld world = getCachedWorld(worldId);
        if (world != null) {
            Set<ChunkCoordinate> chunks = calculateAffectedChunks(world, List.of(combatPathway));
            pathwayPublisher.publishPathways(worldId, List.of(combatPathway), chunks);
        }

        log.debug("World {}: Immediate combat tick for entity {} (spread reaction)",
                worldId, entity.getEntityId());
    }

    /**
     * Spread combat mode to nearby entities when an entity enters combat.
     * Radius is defined per entity via combat_spreadRadius property (0 = no spread).
     * Uses pathway interpolation for current positions.
     */
    public void spreadCombatMode(WorldId worldId, String attackedEntityId, String attackerEntityId, String sessionId) {
        Map<String, SimulationState> worldStates = worldSimulationStates.get(worldId);
        if (worldStates == null) return;

        SimulationState attackedState = worldStates.get(attackedEntityId);
        if (attackedState == null) return;

        double spreadRadius = getServerDouble(attackedState, "combat_spreadRadius", 0);
        if (spreadRadius <= 0) return;

        Vector3 attackedPos = getCurrentEntityPosition(attackedState);
        if (attackedPos == null) return;

        long now = System.currentTimeMillis();

        for (SimulationState neighborState : worldStates.values()) {
            if (neighborState == attackedState) continue;
            if (neighborState.getLifecycleState() != SimulationState.LifecycleState.ALIVE) continue;
            if (neighborState.isInCombat()) continue;
            if (neighborState.getCombatData() == null) continue;

            Vector3 neighborPos = getCurrentEntityPosition(neighborState);
            if (neighborPos == null) continue;

            double dist = distance(attackedPos, neighborPos);
            if (dist <= spreadRadius) {
                neighborState.setCombatStrategy(neighborState.getCombatData().getCombatStrategy());
                neighborState.enterCombat(attackerEntityId, sessionId, now);
                triggerImmediateCombatTick(worldId, neighborState);
                log.info("World {}: Combat spread from {} to {} (dist={}, radius={})",
                        worldId, attackedEntityId, neighborState.getEntity().getEntityId(),
                        String.format("%.1f", dist), spreadRadius);
            }
        }
    }

    /**
     * Get current entity position by interpolating the active pathway.
     * Falls back to WEntity.position (spawn point) if no pathway exists.
     */
    private Vector3 getCurrentEntityPosition(SimulationState state) {
        var pathway = state.getCurrentPathway();
        if (pathway != null && pathway.getWaypoints() != null && !pathway.getWaypoints().isEmpty()) {
            var waypoints = pathway.getWaypoints();
            long now = System.currentTimeMillis();

            if (now <= waypoints.getFirst().getTimestamp()) {
                return waypoints.getFirst().getTarget();
            }
            if (now >= waypoints.getLast().getTimestamp()) {
                return waypoints.getLast().getTarget();
            }
            for (int i = 0; i < waypoints.size() - 1; i++) {
                var from = waypoints.get(i);
                var to = waypoints.get(i + 1);
                if (now >= from.getTimestamp() && now < to.getTimestamp()) {
                    double t = (double) (now - from.getTimestamp()) / (to.getTimestamp() - from.getTimestamp());
                    return Vector3.builder()
                            .x(from.getTarget().getX() + (to.getTarget().getX() - from.getTarget().getX()) * t)
                            .y(from.getTarget().getY() + (to.getTarget().getY() - from.getTarget().getY()) * t)
                            .z(from.getTarget().getZ() + (to.getTarget().getZ() - from.getTarget().getZ()) * t)
                            .build();
                }
            }
            return waypoints.getLast().getTarget();
        }
        // Fallback: entity spawn position
        return state.getEntity().getPosition();
    }

    private static double distance(Vector3 a, Vector3 b) {
        double dx = b.getX() - a.getX();
        double dy = b.getY() - a.getY();
        double dz = b.getZ() - a.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static double getServerDouble(SimulationState state, String key, double defaultValue) {
        var server = state.getEntity().getServer();
        if (server == null) return defaultValue;
        String val = server.get(key);
        if (val == null || val.isBlank()) return defaultValue;
        try {
            return Double.parseDouble(val.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
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
     * Publishes initial health to Redis and clients so health bars show correct values from the start.
     */
    private SimulationState createSimulationState(WorldId worldId, WEntity entity) {
        SimulationState state = new SimulationState(entity);
        if (entity.getServer() != null) {
            EntityCombatData combatData = EntityCombatData.fromEntityProperties(entity.getServer());
            if (combatData != null) {
                state.setCombatData(combatData);
                state.setCombatStrategy(combatData.getCombatStrategy());
                // Load weapon and apply its effects to combat stats
                applyEntityWeapon(combatData, entity);

                // Publish initial health so clients show correct values
                VitalValue health = combatData.getVital("health");
                if (health != null) {
                    publishHealthStatus(worldId, entity.getEntityId(), health);
                }

                log.debug("Initialized combat data for entity {}: health={}, strategy={}, weapon={}",
                        entity.getEntityId(),
                        health != null ? health.getBase() : "none",
                        combatData.getCombatStrategy(),
                        combatData.getWeaponItemId());
            }
        }
        return state;
    }

    /**
     * Load the entity's weapon and apply its effects to combat stats.
     * If combat_weapon is set in server properties, load the WItem and apply effects.
     * If no weapon is configured (fist), apply synthetic fist stats based on entity base values.
     */
    private void applyEntityWeapon(EntityCombatData combatData, WEntity entity) {
        String weaponId = combatData.getWeaponItemId();
        if (CombatConstants.FIST_ITEM_ID.equals(weaponId)) {
            // Fist: synthetic base values already set via initBaseDefaults / fromEntityProperties
            return;
        }
        // Load real weapon from DB
        var worldId = WorldId.of(entity.getWorldId()).orElse(null);
        if (worldId == null) return;
        var weaponOpt = itemService.findByItemId(worldId, weaponId);
        if (weaponOpt.isEmpty()) {
            log.warn("Weapon item '{}' not found for entity {} — falling back to fist",
                    weaponId, entity.getEntityId());
            combatData.setWeaponItemId(CombatConstants.FIST_ITEM_ID);
            return;
        }
        var weapon = weaponOpt.get();
        if (weapon.getServer() == null) return;
        String effectsDef = weapon.getServer().get("effects");
        combatData.applyWeaponEffects(effectsDef);
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

        // Snapshot health before tick to detect changes from regen
        VitalValue health = combatData.getVital("health");
        double healthBefore = health != null ? health.getCurrent() : 0;

        boolean died = baseEffectProcessor.processTick(
                combatData, deltaSeconds, outgoingDeltas, worldId.getId(), entityId);

        // Publish outgoing deltas
        if (!outgoingDeltas.isEmpty()) {
            vitalDeltaPublisher.publishDeltas(outgoingDeltas);
        }

        if (died) {
            log.info("World {}: Entity {} died", worldId, entityId);
            handleEntityDeath(state, worldId);
        } else if (health != null && health.getCurrent() != healthBefore) {
            // Health changed (e.g. from regen) — publish status update to clients
            publishHealthStatus(worldId, entityId, health);
        }
    }

    private void publishHealthStatus(WorldId worldId, String entityId, VitalValue health) {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("health", health.getCurrent());
        status.put("healthMax", health.getEffectiveMax());
        entityStatusPublisher.publishStatusUpdate(worldId.getId(), entityId, status, null);
        entityStateRedisService.updateHealth(worldId.getId(), entityId, health.getCurrent(), health.getEffectiveMax());
    }

    /**
     * Handle entity death: publish death status and start death/respawn lifecycle.
     */
    private void handleEntityDeath(SimulationState state, WorldId worldId) {
        String entityId = state.getEntity().getEntityId();

        // Publish death status to clients
        entityStatusPublisher.publishStatusUpdate(worldId.getId(), entityId,
                Map.of("health", 0.0, "healthMax", 0.0, "death", 1), null);

        // Store lifecycle and attackers (loot-eligible players) in Redis for cross-pod access
        entityStateRedisService.updateState(worldId.getId(), entityId,
                EntityStateRedisService.LIFECYCLE_DEAD, 0.0, 0.0);
        entityStateRedisService.setLooters(worldId.getId(), entityId, state.getAttackers());

        // Remove pathway from Redis so entity stops moving on clients
        pathwayPublisher.removePathway(worldId, entityId);

        // Transition to DEAD lifecycle state
        state.setLifecycleState(SimulationState.LifecycleState.DEAD);
        state.setLifecycleTimestamp(System.currentTimeMillis());
        state.setCurrentPathway(null);

        log.info("World {}: Entity {} died, fade time {}s, respawn time {}s, attackers: {}",
                worldId, entityId,
                state.getFadeTimeMs() / 1000,
                state.getRespawnTimeMs() / 1000,
                state.getAttackers());
    }

    private void updateEntityChunk(WWorld world, WEntity entity) {
        if (entity.getPosition() == null) return;
        var chunkSize = world.getPublicData().getChunkSize();

        int cx = (int) Math.floor(entity.getPosition().getX() / chunkSize);
        int cz = (int) Math.floor(entity.getPosition().getZ() / chunkSize);
        String newChunk = cx + ":" + cz;
    }
}
