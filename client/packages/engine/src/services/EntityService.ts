/**
 * EntityService - Entity and EntityModel management
 *
 * Manages entity models and entity instances:
 * - Lazy loading from REST API
 * - Caching with LRU eviction
 * - ClientEntity management (rendering state)
 * - Waypoint interpolation
 */

import {
  EntityModel,
  Entity,
  EntityPathway,
  ClientEntity,
  createClientEntity,
  getLogger,
  ExceptionHandler,
  ENTITY_POSES,
  MessageType,
  isAirBlockTypeId,
} from '@nimbus/shared';
import type { Rotation } from '@nimbus/shared';
import { Vector3 } from '@babylonjs/core';
import type { AppContext } from '../AppContext';
import type { NetworkService } from './NetworkService';
import { EntityPhysicsController } from './entity/EntityPhysicsController';

const logger = getLogger('EntityService');

/**
 * Entity collision information for physics checks
 */
export interface EntityCollisionInfo {
  entityId: string;
  position: Vector3;
  rotation: Rotation;
  dimensions: { height: number; width: number; footprint: number };
  solid: boolean;
}

/**
 * Entity event types
 */
export type EntityEventType = 'pathway' | 'visibility' | 'transform' | 'pose' | 'removed';

/**
 * Entity event listener
 */
export type EntityEventListener = (data: any) => void;

/**
 * EntityService configuration
 */
export interface EntityServiceConfig {
  /** Maximum number of entity models in cache */
  maxModelCacheSize?: number;

  /** Maximum number of entities in cache */
  maxEntityCacheSize?: number;

  /** Cache eviction timeout in milliseconds (entities not accessed for this time are evicted) */
  cacheEvictionTimeout?: number;

  /** Interval for cache cleanup in milliseconds */
  cacheCleanupInterval?: number;

  /** Update interval in milliseconds (entity position/state updates) */
  updateInterval?: number;

  /** Visibility radius in blocks (entities beyond this distance from player are hidden) */
  visibilityRadius?: number;
}

/**
 * EntityService - Manages entities and entity models
 *
 * Features:
 * - Lazy loading of entity models from REST API
 * - Lazy loading of entities from REST API
 * - LRU cache with automatic eviction
 * - ClientEntity management with rendering state
 * - Waypoint interpolation
 */
export class EntityService {
  private appContext: AppContext;
  private networkService: NetworkService;
  private config: Required<EntityServiceConfig>;

  // Caches
  private entityModelCache: Map<string, EntityModel> = new Map();
  private entityCache: Map<string, ClientEntity> = new Map();
  private entityPathwayCache: Map<string, EntityPathway> = new Map(); // entityId -> pathway

  // Event system
  private eventListeners: Map<EntityEventType, Set<EntityEventListener>> = new Map();

  // Update loop
  private updateInterval?: NodeJS.Timeout;
  private lastUpdateTime: number = Date.now();

  // Cache cleanup
  private cleanupInterval?: NodeJS.Timeout;

  // Visibility radius
  private _visibilityRadius: number;

  // Collision check radius
  private _collisionCheckRadius: number = 10; // blocks

  // Physics controller
  private physicsController: EntityPhysicsController;

  // Proximity tracking: Map<entityId, isInRange>
  private entityProximityState: Map<string, boolean> = new Map();

  constructor(appContext: AppContext, config?: EntityServiceConfig) {
    if (!appContext.services.network) {
      throw new Error('NetworkService is required for EntityService');
    }

    this.appContext = appContext;
    this.networkService = appContext.services.network;

    // Default configuration
    this.config = {
      maxModelCacheSize: config?.maxModelCacheSize ?? 100,
      maxEntityCacheSize: config?.maxEntityCacheSize ?? 1000,
      cacheEvictionTimeout: config?.cacheEvictionTimeout ?? 300000, // 5 minutes
      cacheCleanupInterval: config?.cacheCleanupInterval ?? 60000, // 1 minute
      updateInterval: config?.updateInterval ?? 100, // 100ms update loop
      visibilityRadius: config?.visibilityRadius ?? 50, // 50 blocks
    };

    this._visibilityRadius = this.config.visibilityRadius;

    // Initialize physics controller
    this.physicsController = new EntityPhysicsController();

    logger.debug('EntityService initialized', { config: this.config });

    // Start cache cleanup
    this.startCacheCleanup();

    // Start update loop
    this.startUpdateLoop();

    // Register chunk events
    this.registerChunkEvents();
  }

  /**
   * Register chunk event listeners
   */
  private registerChunkEvents(): void {
    const chunkService = this.appContext.services.chunk;
    if (!chunkService) {
      logger.warn('ChunkService not available, chunk events will not be registered');
      return;
    }

    // Listen for chunk unload events
    chunkService.on('chunk:unloaded', (data: { cx: number; cz: number }) => {
      // Get chunk size from world info
      const worldInfo = this.appContext.worldInfo;
      if (worldInfo && worldInfo.chunkSize) {
        this.onChunkUnloaded(data.cx, data.cz, worldInfo.chunkSize);
      }
    });

    logger.debug('Chunk event listeners registered');
  }

  /**
   * Get entity model by ID (lazy loading with cache)
   */
  async getEntityModel(modelId: string): Promise<EntityModel | null> {
    try {
      // Check cache
      const cached = this.entityModelCache.get(modelId);
      if (cached) {
        logger.debug('Entity model cache hit', { modelId });
        return cached;
      }

      // Load from REST API
      const url = this.networkService.getEntityModelUrl(modelId);
      logger.debug('Loading entity model from API', { modelId, url });
      const response = await fetch(url, {
        credentials: 'include',
      });

      if (!response.ok) {
        if (response.status === 404) {
          logger.warn('Entity model not found', { modelId });
          return null;
        }
        throw new Error(`Failed to load entity model: ${response.statusText}`);
      }

      const data = await response.json();
      const model = this.deserializeEntityModel(data);

      if (!model) {
        logger.warn('Failed to deserialize entity model', { modelId, data });
        return null;
      }

      // Add to cache
      this.entityModelCache.set(modelId, model);
      this.evictModelCache();

      logger.debug('Entity model loaded', { modelId });
      return model;
    } catch (error) {
      ExceptionHandler.handle(error, 'EntityService.getEntityModel', { modelId });
      return null;
    }
  }

  /**
   * Get entity by ID (lazy loading with cache)
   */
  async getEntity(entityId: string): Promise<ClientEntity | null> {
    try {
      // Check cache
      const cached = this.entityCache.get(entityId);
      if (cached) {
        cached.lastAccess = Date.now();
        logger.debug('Entity cache hit', { entityId });
        return cached;
      }

      // Load from REST API
      const url = this.networkService.getEntityUrl(entityId);
      logger.debug('Loading entity from API', { entityId, url });
      const response = await fetch(url, {
        credentials: 'include',
      });

      if (!response.ok) {
        if (response.status === 404) {
          logger.warn('Entity not found', { entityId });
          return null;
        }
        throw new Error(`Failed to load entity: ${response.statusText}`);
      }

      const data = await response.json();
      const entity = data as Entity;

      // Load entity model
      const model = await this.getEntityModel(entity.model);
      if (!model) {
        logger.warn('Entity model not found for entity', { entityId, modelId: entity.model });
        return null;
      }

      // Create ClientEntity
      const clientEntity = createClientEntity(entity, model);

      // Add to cache
      this.entityCache.set(entityId, clientEntity);
      this.evictEntityCache();

      logger.debug('Entity loaded', { entityId });
      return clientEntity;
    } catch (error) {
      ExceptionHandler.handle(error, 'EntityService.getEntity', { entityId });
      return null;
    }
  }

  /**
   * Update entity in cache
   */
  updateEntity(entity: Entity): void {
    const clientEntity = this.entityCache.get(entity.id);
    if (clientEntity) {
      clientEntity.entity = entity;
      clientEntity.lastAccess = Date.now();
      logger.debug('Entity updated in cache', { entityId: entity.id });
    }
  }

  /**
   * Set entity pathway
   * If entity doesn't exist, it will be loaded automatically
   */
  async setEntityPathway(pathway: EntityPathway): Promise<void> {
    this.entityPathwayCache.set(pathway.entityId, pathway);

    // Check if entity exists
    let clientEntity = this.entityCache.get(pathway.entityId);

    // If entity doesn't exist, load it (unknown entity from network)
    if (!clientEntity) {
      logger.debug('Unknown entity pathway received, loading entity', { entityId: pathway.entityId });

      try {
        const loadedEntity = await this.getEntity(pathway.entityId);

        if (!loadedEntity) {
          logger.warn('Failed to load entity for pathway', { entityId: pathway.entityId });
          return;
        }

        clientEntity = loadedEntity;
      } catch (error) {
        ExceptionHandler.handle(error, 'EntityService.setEntityPathway.loadEntity', {
          entityId: pathway.entityId,
        });
        return;
      }
    }

    // Update ClientEntity with waypoints
    clientEntity.currentWaypoints = pathway.waypoints;
    clientEntity.currentWaypointIndex = 0;
    clientEntity.lastAccess = Date.now();

    // Check if this is a physics-based pathway
    const isPhysicsPathway = pathway.physicsEnabled ?? false;
    const hasServerPhysics = clientEntity.entity.physics ?? false;
    const hasClientPhysics = clientEntity.entity.clientPhysics ?? false;

    if (isPhysicsPathway && hasClientPhysics) {
      // For physics-based entities: Apply velocity hint but keep current position
      // The physics controller will handle the movement

      // Initialize position on first spawn (if entity has never had a position)
      if (!clientEntity.currentPosition ||
          (clientEntity.currentPosition.x === 0 &&
           clientEntity.currentPosition.y === 0 &&
           clientEntity.currentPosition.z === 0)) {
        if (pathway.waypoints.length > 0) {
          const firstWaypoint = pathway.waypoints[0];
          clientEntity.currentPosition = {
            x: firstWaypoint.target.x,
            y: firstWaypoint.target.y,
            z: firstWaypoint.target.z,
          };
          logger.debug('Entity physics initial position set', {
            entityId: pathway.entityId,
            position: clientEntity.currentPosition,
          });
        }
      }

      if (pathway.velocity) {
        this.physicsController.applyServerVelocity(clientEntity, pathway.velocity);
      }

      // Update rotation from pathway
      if (pathway.waypoints.length > 0) {
        const firstWaypoint = pathway.waypoints[0];
        clientEntity.currentRotation = {
          y: firstWaypoint.rotation.y,
          p: firstWaypoint.rotation.p ?? 0,
        };
      }

      logger.debug('Entity physics pathway set', {
        entityId: pathway.entityId,
        waypointCount: pathway.waypoints.length,
        velocity: pathway.velocity,
        position: clientEntity.currentPosition,
      });
    } else {
      // For traditional waypoint-based entities: Set position directly
      if (pathway.waypoints.length > 0) {
        const firstWaypoint = pathway.waypoints[0];
        clientEntity.currentPosition = {
          x: firstWaypoint.target.x,
          y: firstWaypoint.target.y,
          z: firstWaypoint.target.z,
        };
        clientEntity.currentRotation = {
          y: firstWaypoint.rotation.y,
          p: firstWaypoint.rotation.p ?? 0,
        };
        clientEntity.currentPose = firstWaypoint.pose ?? 0;
      }

      logger.debug('Entity waypoint pathway set', {
        entityId: pathway.entityId,
        waypointCount: pathway.waypoints.length,
      });
    }

    // Emit pathway event
    this.emit('pathway', pathway);
  }

  /**
   * Get entity pathway
   */
  getEntityPathway(entityId: string): EntityPathway | null {
    return this.entityPathwayCache.get(entityId) || null;
  }

  /**
   * Update entity position/rotation from interpolation
   */
  updateEntityTransform(
    entityId: string,
    position: { x: number; y: number; z: number },
    rotation: { y: number; p: number },
    pose: number
  ): void {
    const clientEntity = this.entityCache.get(entityId);
    if (clientEntity) {
      clientEntity.currentPosition = position;
      clientEntity.currentRotation = rotation;
      clientEntity.currentPose = pose;
      clientEntity.lastAccess = Date.now();

      // Emit transform event
      this.emit('transform', { entityId, position, rotation, pose });
    }
  }

  /**
   * Set entity visibility
   */
  setEntityVisibility(entityId: string, visible: boolean): void {
    const clientEntity = this.entityCache.get(entityId);
    if (clientEntity) {
      clientEntity.visible = visible;
      clientEntity.lastAccess = Date.now();
      logger.debug('Entity visibility changed', { entityId, visible });

      // Emit visibility event
      this.emit('visibility', { entityId, visible });
    }
  }

  /**
   * Get all cached entities
   */
  getAllEntities(): ClientEntity[] {
    return Array.from(this.entityCache.values());
  }

  /**
   * Get all visible entities
   */
  getVisibleEntities(): ClientEntity[] {
    return Array.from(this.entityCache.values()).filter(e => e.visible);
  }

  /**
   * Remove entity from cache
   */
  removeEntity(entityId: string): void {
    const removed = this.entityCache.delete(entityId);
    this.entityPathwayCache.delete(entityId);

    if (removed) {
      logger.debug('Entity removed from cache', { entityId });

      // Emit removed event
      this.emit('removed', entityId);
    }
  }

  /**
   * Clear all caches
   */
  clearCache(): void {
    this.entityModelCache.clear();
    this.entityCache.clear();
    this.entityPathwayCache.clear();
    logger.debug('Entity caches cleared');
  }

  /**
   * Get cache statistics
   */
  getCacheStats(): {
    modelCacheSize: number;
    entityCacheSize: number;
    pathwayCacheSize: number;
    maxModelCacheSize: number;
    maxEntityCacheSize: number;
  } {
    return {
      modelCacheSize: this.entityModelCache.size,
      entityCacheSize: this.entityCache.size,
      pathwayCacheSize: this.entityPathwayCache.size,
      maxModelCacheSize: this.config.maxModelCacheSize,
      maxEntityCacheSize: this.config.maxEntityCacheSize,
    };
  }

  /**
   * Get visibility radius
   */
  get visibilityRadius(): number {
    return this._visibilityRadius;
  }

  /**
   * Set visibility radius
   */
  set visibilityRadius(value: number) {
    this._visibilityRadius = value;
    logger.debug('Visibility radius changed', { radius: value });
  }

  /**
   * Dispose service (stop cleanup interval and update loop)
   */
  dispose(): void {
    if (this.updateInterval) {
      clearInterval(this.updateInterval);
      this.updateInterval = undefined;
      logger.debug('Update loop stopped');
    }

    if (this.cleanupInterval) {
      clearInterval(this.cleanupInterval);
      this.cleanupInterval = undefined;
      logger.debug('Cache cleanup stopped');
    }
  }

  /**
   * Start cache cleanup interval
   */
  private startCacheCleanup(): void {
    this.cleanupInterval = setInterval(() => {
      this.cleanupCache();
    }, this.config.cacheCleanupInterval);

    logger.debug('Cache cleanup started', { interval: this.config.cacheCleanupInterval });
  }

  /**
   * Cleanup expired entities from cache
   */
  private cleanupCache(): void {
    const now = Date.now();
    const evictionThreshold = now - this.config.cacheEvictionTimeout;

    let evictedCount = 0;

    // Evict old entities
    for (const [entityId, clientEntity] of this.entityCache.entries()) {
      if (clientEntity.lastAccess < evictionThreshold) {
        this.entityCache.delete(entityId);
        this.entityPathwayCache.delete(entityId);
        evictedCount++;
      }
    }

    if (evictedCount > 0) {
      logger.debug('Cache cleanup evicted entities', { count: evictedCount });
    }
  }

  /**
   * Evict oldest entity models if cache is full
   */
  private evictModelCache(): void {
    if (this.entityModelCache.size <= this.config.maxModelCacheSize) {
      return;
    }

    // Simple eviction: remove first entry (oldest in insertion order)
    const firstKey = this.entityModelCache.keys().next().value;
    if (firstKey) {
      this.entityModelCache.delete(firstKey);
      logger.debug('Entity model evicted from cache', { modelId: firstKey });
    }
  }

  /**
   * Evict oldest entities if cache is full
   */
  private evictEntityCache(): void {
    if (this.entityCache.size <= this.config.maxEntityCacheSize) {
      return;
    }

    // Find entity with oldest lastAccess
    let oldestEntityId: string | null = null;
    let oldestAccess = Date.now();

    for (const [entityId, clientEntity] of this.entityCache.entries()) {
      if (clientEntity.lastAccess < oldestAccess) {
        oldestAccess = clientEntity.lastAccess;
        oldestEntityId = entityId;
      }
    }

    if (oldestEntityId) {
      this.entityCache.delete(oldestEntityId);
      this.entityPathwayCache.delete(oldestEntityId);
      logger.debug('Entity evicted from cache', { entityId: oldestEntityId });
    }
  }

  /**
   * Deserialize entity model from JSON (convert plain objects to Maps)
   */
  private deserializeEntityModel(data: any): EntityModel | null {
    if (!data || typeof data !== 'object') {
      return null;
    }

    // Convert poseMapping: String keys (e.g., "WALK") to ENTITY_POSES enum values
    const poseMapping = new Map<number, any>();
    if (data.poseMapping) {
      for (const [key, value] of Object.entries(data.poseMapping)) {
        // Convert string key to ENTITY_POSES enum value
        const poseId = ENTITY_POSES[key as keyof typeof ENTITY_POSES];
        if (poseId !== undefined) {
          poseMapping.set(poseId, value);
        } else {
          logger.warn('Unknown pose key in poseMapping', { key, entityModelId: data.id });
        }
      }
    }

    return {
      ...data,
      poseMapping,
      modelModifierMapping: new Map(Object.entries(data.modelModifierMapping || {})),
    } as EntityModel;
  }

  /**
   * Start update loop for entity position/state updates
   */
  private startUpdateLoop(): void {
    this.updateInterval = setInterval(() => {
      this.update();
    }, this.config.updateInterval);

    logger.debug('Update loop started', { interval: this.config.updateInterval });
  }

  /**
   * Update all entities (position interpolation, visibility management)
   */
  private update(): void {
    try {
      // Get current time with network lag compensation
      // TODO: Get server lag from PingMessageHandler via NetworkService
      const serverLag = 0; // For now, no lag compensation
      const currentTime = Date.now() + serverLag;

      // Calculate delta time
      const deltaTime = (currentTime - this.lastUpdateTime) / 1000; // Convert to seconds
      this.lastUpdateTime = currentTime;

      // Get player position
      const playerService = this.appContext.services.player;
      if (!playerService) {
        return; // Player service not available yet
      }

      const playerPos = playerService.getPosition();

      // Update physics controller with player position
      this.physicsController.setPlayerPosition(playerPos);

      // Update all entities
      for (const clientEntity of this.entityCache.values()) {
        this.updateEntity_internal(clientEntity, currentTime, deltaTime, playerPos);
      }
    } catch (error) {
      ExceptionHandler.handle(error, 'EntityService.update');
    }
  }

  /**
   * Update single entity (position interpolation and visibility)
   */
  private updateEntity_internal(
    clientEntity: ClientEntity,
    currentTime: number,
    deltaTime: number,
    playerPos: { x: number; y: number; z: number }
  ): void {
    // Update lastAccess
    clientEntity.lastAccess = currentTime;

    // Get pathway
    const pathway = this.entityPathwayCache.get(clientEntity.id);
    if (!pathway || pathway.waypoints.length === 0) {
      return;
    }

    // Check if entity has physics enabled
    const hasServerPhysics = clientEntity.entity.physics ?? false;
    const hasClientPhysics = clientEntity.entity.clientPhysics ?? false;
    const isPhysicsPathway = pathway.physicsEnabled ?? false;

    if (hasClientPhysics && isPhysicsPathway) {
      // Client-side physics enabled: Run local physics simulation
      this.updateEntityPhysics(clientEntity, pathway, deltaTime);
    } else {
      // Client-side physics disabled: Use traditional waypoint interpolation
      this.updateEntityWaypoint(clientEntity, pathway, currentTime);
    }

    // Check visibility based on distance to player
    const distance = Math.sqrt(
      Math.pow(clientEntity.currentPosition.x - playerPos.x, 2) +
      Math.pow(clientEntity.currentPosition.y - playerPos.y, 2) +
      Math.pow(clientEntity.currentPosition.z - playerPos.z, 2)
    );

    const shouldBeVisible = distance <= this._visibilityRadius;

    // Update visibility if changed
    if (clientEntity.visible !== shouldBeVisible) {
      clientEntity.visible = shouldBeVisible;
      this.emit('visibility', { entityId: clientEntity.id, visible: shouldBeVisible });
    }

    // Check proximity notification (only for visible entities)
    if (shouldBeVisible) {
      this.checkProximityNotification(clientEntity, distance);
    }
  }

  /**
   * Update entity using physics simulation
   */
  private updateEntityPhysics(
    clientEntity: ClientEntity,
    pathway: EntityPathway,
    deltaTime: number
  ): void {
    // Store old position for step sound detection
    const oldPosition = { ...clientEntity.currentPosition };

    // Apply server-predicted velocity (if provided)
    if (pathway.velocity) {
      this.physicsController.applyServerVelocity(clientEntity, pathway.velocity);
    }

    // Get helper functions for physics simulation
    const chunkService = this.appContext.services.chunk;
    if (!chunkService) {
      return; // Can't do physics without chunk service
    }

    // Helper: Get block at position
    const getBlockAtPosition = (x: number, y: number, z: number) => {
      const floorX = Math.floor(x);
      const floorY = Math.floor(y);
      const floorZ = Math.floor(z);

      return chunkService.getBlockAt(floorX, floorY, floorZ);
    };

    // Helper: Check if block is solid (not air)
    const isBlockSolid = (x: number, y: number, z: number): boolean => {
      const block = getBlockAtPosition(x, y, z);
      return block ? block.blockType.id !== '0' : false;
    };

    // Update physics
    const wasUpdated = this.physicsController.updatePhysics(
      clientEntity,
      deltaTime,
      isBlockSolid
    );

    if (wasUpdated) {
      // Get velocity for animation
      const velocity = this.physicsController.getVelocity(clientEntity.entity.id);
      const speed = velocity
        ? Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z)
        : 0;

      // Determine pose based on velocity and grounded state
      const isGrounded = this.physicsController.isGrounded(clientEntity.entity.id);
      let pose = ENTITY_POSES.IDLE;
      if (!isGrounded) {
        pose = ENTITY_POSES.JUMP;
      } else if (speed > 0.1) {
        pose = ENTITY_POSES.WALK;
      }

      clientEntity.currentPose = pose;

      // Emit transform event
      this.emit('transform', {
        entityId: clientEntity.id,
        position: clientEntity.currentPosition,
        rotation: clientEntity.currentRotation,
        pose: pose,
        velocity: speed,
      });

      // Check for step sound emission (physics entities)
      this.checkAndEmitStepSoundPhysics(clientEntity, oldPosition, speed, isGrounded);
    }
  }

  /**
   * Update entity using traditional waypoint interpolation
   */
  private updateEntityWaypoint(
    clientEntity: ClientEntity,
    pathway: EntityPathway,
    currentTime: number
  ): void {
    // Store old position for movement detection
    const oldPosition = { ...clientEntity.currentPosition };

    // Interpolate position from waypoints
    const result = this.interpolatePosition(pathway, currentTime);
    if (result) {
      // Calculate velocity (distance / time)
      const velocity = result.velocity ?? 0;

      // Update entity position/rotation/pose
      clientEntity.currentPosition = result.position;
      clientEntity.currentRotation = result.rotation;
      clientEntity.currentPose = result.pose;
      clientEntity.currentWaypointIndex = result.waypointIndex;

      // Emit transform event with velocity
      this.emit('transform', {
        entityId: clientEntity.id,
        position: result.position,
        rotation: result.rotation,
        pose: result.pose,
        velocity: velocity,
      });

      // Check for step sound emission (300ms throttle for waypoint entities)
      this.checkAndEmitStepSound(clientEntity, oldPosition, velocity);
    }
  }

  /**
   * Interpolate entity position from pathway waypoints
   */
  private interpolatePosition(
    pathway: EntityPathway,
    currentTime: number
  ): {
    position: { x: number; y: number; z: number };
    rotation: { y: number; p: number };
    pose: number;
    waypointIndex: number;
    velocity: number;
  } | null {
    const waypoints = pathway.waypoints;
    if (waypoints.length === 0) {
      return null;
    }

    // Find current waypoint segment
    let currentIndex = 0;
    for (let i = 0; i < waypoints.length - 1; i++) {
      if (currentTime >= waypoints[i].timestamp && currentTime < waypoints[i + 1].timestamp) {
        currentIndex = i;
        break;
      }
    }

    // If past last waypoint
    if (currentTime >= waypoints[waypoints.length - 1].timestamp) {
      const lastWaypoint = waypoints[waypoints.length - 1];

      return {
        position: { x: lastWaypoint.target.x, y: lastWaypoint.target.y, z: lastWaypoint.target.z },
        rotation: { y: lastWaypoint.rotation.y, p: lastWaypoint.rotation.p ?? 0 },
        pose: lastWaypoint.pose ?? pathway.idlePose ?? 0,
        waypointIndex: waypoints.length - 1,
        velocity: 0, // Not moving (past last waypoint)
      };
    }

    // Interpolate between two waypoints
    const from = waypoints[currentIndex];
    const to = waypoints[currentIndex + 1];

    const t = (currentTime - from.timestamp) / (to.timestamp - from.timestamp);
    const clampedT = Math.max(0, Math.min(1, t));

    // Linear interpolation for position
    const position = {
      x: from.target.x + (to.target.x - from.target.x) * clampedT,
      y: from.target.y + (to.target.y - from.target.y) * clampedT,
      z: from.target.z + (to.target.z - from.target.z) * clampedT,
    };

    // Linear interpolation for rotation
    const rotation = {
      y: from.rotation.y + (to.rotation.y - from.rotation.y) * clampedT,
      p: (from.rotation.p ?? 0) + ((to.rotation.p ?? 0) - (from.rotation.p ?? 0)) * clampedT,
    };

    // Use target pose when close to target
    const pose = clampedT > 0.5 ? (to.pose ?? pathway.idlePose ?? 0) : (from.pose ?? pathway.idlePose ?? 0);

    // Calculate velocity (distance between waypoints / time between waypoints)
    const dx = to.target.x - from.target.x;
    const dy = to.target.y - from.target.y;
    const dz = to.target.z - from.target.z;
    const distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
    const timeDiff = (to.timestamp - from.timestamp) / 1000; // Convert to seconds
    const velocity = timeDiff > 0 ? distance / timeDiff : 0;

    return {
      position,
      rotation,
      pose,
      waypointIndex: currentIndex,
      velocity,
    };
  }

  /**
   * Handle chunk unload - hide all entities in this chunk
   * Prevents "dead" entities from staying in the world
   */
  onChunkUnloaded(chunkX: number, chunkZ: number, chunkSize: number): void {
    try {
      let hiddenCount = 0;

      // Check all entities
      for (const clientEntity of this.entityCache.values()) {
        const pos = clientEntity.currentPosition;

        // Calculate chunk coordinates from entity position
        const entityChunkX = Math.floor(pos.x / chunkSize);
        const entityChunkZ = Math.floor(pos.z / chunkSize);

        // If entity is in the unloaded chunk, hide it
        if (entityChunkX === chunkX && entityChunkZ === chunkZ) {
          if (clientEntity.visible) {
            clientEntity.visible = false;
            this.emit('visibility', { entityId: clientEntity.id, visible: false });
            hiddenCount++;
          }
        }
      }

      if (hiddenCount > 0) {
        logger.debug('Entities hidden due to chunk unload', {
          chunkX,
          chunkZ,
          hiddenCount,
        });
      }
    } catch (error) {
      ExceptionHandler.handle(error, 'EntityService.onChunkUnloaded', { chunkX, chunkZ });
    }
  }

  /**
   * Register event listener
   */
  on(event: EntityEventType, listener: EntityEventListener): void {
    if (!this.eventListeners.has(event)) {
      this.eventListeners.set(event, new Set());
    }
    this.eventListeners.get(event)!.add(listener);
  }

  /**
   * Unregister event listener
   */
  off(event: EntityEventType, listener: EntityEventListener): void {
    const listeners = this.eventListeners.get(event);
    if (listeners) {
      listeners.delete(listener);
    }
  }

  /**
   * Emit event to all listeners
   */
  private emit(event: EntityEventType, data: any): void {
    const listeners = this.eventListeners.get(event);
    if (listeners) {
      for (const listener of listeners) {
        try {
          listener(data);
        } catch (error) {
          ExceptionHandler.handle(error, 'EntityService.emit', { event, data });
        }
      }
    }
  }

  /**
   * Get collision check radius
   */
  getCollisionCheckRadius(): number {
    return this._collisionCheckRadius;
  }

  /**
   * Set collision check radius
   */
  setCollisionCheckRadius(radius: number): void {
    this._collisionCheckRadius = radius;
  }

  /**
   * Get entities in radius for collision detection
   *
   * @param center Center position (usually player position)
   * @param radius Search radius in blocks
   * @param movementMode Player's current movement mode (for dimension selection)
   * @returns Array of entity collision info
   */
  getEntitiesInRadius(
    center: Vector3,
    radius: number,
    movementMode: 'walk' | 'sprint' | 'crouch' | 'swim' | 'climb' | 'free_fly' | 'fly' | 'teleport' = 'walk'
  ): EntityCollisionInfo[] {
    const entitiesInRadius: EntityCollisionInfo[] = [];

    for (const clientEntity of this.entityCache.values()) {
      // Skip if not visible
      if (!clientEntity.visible) {
        continue;
      }

      // Calculate distance to center
      const dx = clientEntity.currentPosition.x - center.x;
      const dy = clientEntity.currentPosition.y - center.y;
      const dz = clientEntity.currentPosition.z - center.z;
      const distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

      // Skip if too far
      if (distance > radius) {
        continue;
      }

      // Get dimensions for current movement mode
      const dimensions = this.getEntityDimensions(clientEntity.model, movementMode);

      entitiesInRadius.push({
        entityId: clientEntity.entity.id,
        position: new Vector3(clientEntity.currentPosition.x, clientEntity.currentPosition.y, clientEntity.currentPosition.z),
        rotation: clientEntity.currentRotation,
        dimensions,
        solid: clientEntity.entity.solid ?? false,
      });
    }

    return entitiesInRadius;
  }

  /**
   * Get entity dimensions for movement mode
   */
  private getEntityDimensions(
    entityModel: EntityModel,
    movementMode: 'walk' | 'sprint' | 'crouch' | 'swim' | 'climb' | 'free_fly' | 'fly' | 'teleport'
  ): { height: number; width: number; footprint: number } {
    const dimensions = entityModel.dimensions;

    // If dimensions is undefined, return default
    if (!dimensions) {
      return { height: 1.8, width: 0.6, footprint: 0.6 };
    }

    // Try specific movement mode first, then fallback
    return (
      dimensions[movementMode] ??
      dimensions.walk ??
      dimensions.sprint ??
      dimensions.crouch ??
      { height: 1.8, width: 0.6, footprint: 0.6 }
    );
  }

  /**
   * Check proximity notification for entity
   * Sends notification when player enters/exits attention range
   *
   * @param clientEntity Entity to check
   * @param distance Current distance to player
   */
  private checkProximityNotification(clientEntity: ClientEntity, distance: number): void {
    // Check if entity wants proximity notifications
    const notifyRange = clientEntity.entity.notifyOnAttentionRange;
    if (!notifyRange || notifyRange <= 0) {
      return;
    }

    // Get player info for stealth modifiers
    const playerService = this.appContext.services.player;
    if (!playerService || !this.appContext.playerInfo) {
      return;
    }

    const playerInfo = this.appContext.playerInfo;

    // Calculate effective range based on player movement mode
    const movementMode = playerService.getMovementMode();
    const isCrouching = movementMode === 'crouch';

    const distanceReduction = isCrouching
      ? playerInfo.distanceNotifyReductionCrouch
      : playerInfo.distanceNotifyReductionWalk;

    const effectiveRange = notifyRange + distanceReduction;

    // Get current proximity state
    const wasInRange = this.entityProximityState.get(clientEntity.id) ?? false;
    const isInRange = distance < effectiveRange;

    // Check for state change
    if (isInRange && !wasInRange) {
      // Entered range - send notification
      this.entityProximityState.set(clientEntity.id, true);

      const messageId = `proximity_${Date.now()}_${Math.random().toString(36).substring(7)}`;
      this.networkService.send({
        i: messageId,
        t: MessageType.ENTITY_INTERACTION,
        d: {
          entityId: clientEntity.id,
          ts: Date.now(),
          ac: 'entityProximity',
          pa: {
            distance: distance,
            effectiveRange: effectiveRange,
            entered: true,
          },
        },
      });

      logger.debug('Entity proximity notification sent (entered range)', {
        entityId: clientEntity.id,
        distance,
        effectiveRange,
      });
    } else if (!isInRange && wasInRange) {
      // Exited range - update state
      this.entityProximityState.set(clientEntity.id, false);

      logger.debug('Player exited entity proximity range', {
        entityId: clientEntity.id,
        distance,
        effectiveRange,
      });
    }
  }

  /**
   * Called when player collides with an entity
   * Sends collision event to server if entity has notifyOnCollision=true
   *
   * @param entityId Entity that was collided with
   * @param playerPosition Player's position at collision
   */
  onPlayerCollision(entityId: string, playerPosition: Vector3): void {
    const clientEntity = this.entityCache.get(entityId);
    if (!clientEntity) {
      return;
    }

    // Check if entity wants collision notifications
    if (clientEntity.entity.notifyOnCollision === true) {
      // Send collision event to server
      const messageId = `collision_${Date.now()}_${Math.random().toString(36).substring(7)}`;
      this.networkService.send({
        i: messageId,
        t: MessageType.ENTITY_INTERACTION,
        d: {
          entityId: entityId,
          ts: Date.now(),
          ac: 'entityCollision',
          pa: {
            playerPosition: {
              x: playerPosition.x,
              y: playerPosition.y,
              z: playerPosition.z,
            },
          },
        },
      });

      logger.debug('Entity collision notification sent', {
        entityId,
        playerPosition,
      });
    } else {
      logger.trace('Player collision with entity (no notification)', {
        entityId,
        playerPosition,
      });
    }
  }

  /**
   * Check if entity should emit step sound and emit if conditions are met
   * Only emits if:
   * - Entity is moving (velocity > 0.1)
   * - Entity position changed (not floating in one spot)
   * - Entity is on ground (has solid block below)
   * - Enough time passed since last step (300ms throttle)
   *
   * @param clientEntity Entity to check
   * @param oldPosition Previous position
   * @param velocity Current velocity
   */
  private checkAndEmitStepSound(
    clientEntity: ClientEntity,
    oldPosition: { x: number; y: number; z: number },
    velocity: number
  ): void {
    // Check if entity is moving (velocity threshold)
    if (velocity < 0.1) {
      return; // Not moving
    }

    // Check if position actually changed
    const positionChanged =
      Math.abs(clientEntity.currentPosition.x - oldPosition.x) > 0.001 ||
      Math.abs(clientEntity.currentPosition.y - oldPosition.y) > 0.001 ||
      Math.abs(clientEntity.currentPosition.z - oldPosition.z) > 0.001;

    if (!positionChanged) {
      return; // Position didn't change
    }

    // Throttle: Check if enough time passed since last step (300ms for entities)
    const now = Date.now();
    if (clientEntity.lastStepTime && now - clientEntity.lastStepTime < 300) {
      return; // Too soon
    }

    // Find block under entity (ground block)
    const chunkService = this.appContext.services.chunk;
    if (!chunkService) {
      return; // Can't check ground without chunk service
    }

    const floorX = Math.floor(clientEntity.currentPosition.x);
    const floorY = Math.floor(clientEntity.currentPosition.y);
    const floorZ = Math.floor(clientEntity.currentPosition.z);

    // Derive movementType from entity pose
    const movementType = this.getMovementTypeFromPose(clientEntity.currentPose);

    // SWIM mode: emit event even without ground block
    if (movementType === 'swim') {
      const physicsService = this.appContext.services.physics;
      if (physicsService) {
        clientEntity.lastStepTime = now;

        // Create minimal block object with position for swim mode
        const swimPosition = {
          block: {
            position: { x: floorX, y: floorY, z: floorZ },
          },
          blockType: { id: '0' },
          audioSteps: undefined,
        };

        (physicsService as any).emit('step:over', {
          entityId: clientEntity.id,
          block: swimPosition,
          movementType,
        });
      }
      return;
    }

    // Check block below entity (grounded check)
    const groundBlock = chunkService.getBlockAt(floorX, floorY - 1, floorZ);

    if (!groundBlock || groundBlock.blockType.id === '0') {
      return; // No ground block or air - entity is floating/flying
    }

    // Emit step sound event - bypass PhysicsService and emit directly to avoid double throttling
    const physicsService = this.appContext.services.physics;
    if (physicsService) {
      // Update throttle timestamp
      clientEntity.lastStepTime = now;

      // Emit directly (PhysicsService has 'emit' method but also throttles in emitStepOver)
      // We bypass emitStepOver because we already throttle in EntityService
      (physicsService as any).emit('step:over', {
        entityId: clientEntity.id,
        block: groundBlock,
        movementType,
      });
    }
  }

  /**
   * Check if physics entity should emit step sound
   * Similar to checkAndEmitStepSound but uses grounded state from physics controller
   *
   * @param clientEntity Entity to check
   * @param oldPosition Previous position
   * @param speed Current speed (horizontal velocity magnitude)
   * @param isGrounded Grounded state from physics controller
   */
  private checkAndEmitStepSoundPhysics(
    clientEntity: ClientEntity,
    oldPosition: { x: number; y: number; z: number },
    speed: number,
    isGrounded: boolean
  ): void {
    // Check if entity is moving (speed threshold)
    if (speed < 0.1) {
      return; // Not moving
    }

    // Check if entity is grounded
    if (!isGrounded) {
      return; // In air - no step sounds
    }

    // Check if position actually changed
    const positionChanged =
      Math.abs(clientEntity.currentPosition.x - oldPosition.x) > 0.001 ||
      Math.abs(clientEntity.currentPosition.z - oldPosition.z) > 0.001;

    if (!positionChanged) {
      return; // Position didn't change
    }

    // Throttle: Check if enough time passed since last step (300ms for entities)
    const now = Date.now();
    if (clientEntity.lastStepTime && now - clientEntity.lastStepTime < 300) {
      return; // Too soon
    }

    // Find block under entity (ground block)
    const chunkService = this.appContext.services.chunk;
    if (!chunkService) {
      return; // Can't check ground without chunk service
    }

    const floorX = Math.floor(clientEntity.currentPosition.x);
    const floorY = Math.floor(clientEntity.currentPosition.y);
    const floorZ = Math.floor(clientEntity.currentPosition.z);

    // Derive movementType from entity pose
    const movementType = this.getMovementTypeFromPose(clientEntity.currentPose);

    // SWIM mode: emit event even without ground block (checked via pose, not isGrounded)
    if (movementType === 'swim') {
      const physicsService = this.appContext.services.physics;
      if (physicsService) {
        clientEntity.lastStepTime = now;

        // Create minimal block object with position for swim mode
        const swimPosition = {
          block: {
            position: { x: floorX, y: floorY, z: floorZ },
          },
          blockType: { id: 0 },
          audioSteps: undefined,
        };

        (physicsService as any).emit('step:over', {
          entityId: clientEntity.id,
          block: swimPosition,
          movementType,
        });
      }
      return;
    }

    // Check block below entity
    const groundBlock = chunkService.getBlockAt(floorX, floorY - 1, floorZ);

    if (!groundBlock || isAirBlockTypeId(groundBlock.blockType.id)) {
      return; // No ground block or air
    }

    // Emit step sound event - bypass PhysicsService and emit directly to avoid double throttling
    const physicsService = this.appContext.services.physics;
    if (physicsService) {
      // Update throttle timestamp
      clientEntity.lastStepTime = now;

      // Emit directly (PhysicsService has 'emit' method but also throttles in emitStepOver)
      // We bypass emitStepOver because we already throttle in EntityService
      (physicsService as any).emit('step:over', {
        entityId: clientEntity.id,
        block: groundBlock,
        movementType,
      });
    }
  }

  /**
   * Get movement type from entity pose
   * Maps ENTITY_POSES to movement type strings for audio
   */
  private getMovementTypeFromPose(pose: number): string {
    switch (pose) {
      case ENTITY_POSES.CROUCH:
        return 'crouch';
      case ENTITY_POSES.SWIM:
        return 'swim';
      case ENTITY_POSES.JUMP:
        return 'jump';
      case ENTITY_POSES.RUN:
      case ENTITY_POSES.SPRINT:
        return 'run';
      case ENTITY_POSES.WALK:
      case ENTITY_POSES.WALK_SLOW:
      case ENTITY_POSES.IDLE:
      default:
        return 'walk';
    }
  }
}
