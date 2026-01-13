/**
 * PhysicsService - Physics simulation for entities
 *
 * Handles movement physics for locally managed entities (player, etc.).
 * Supports two movement modes: Walk and Fly.
 *
 * For PlayerEntity, uses dynamic values from PlayerInfo.
 * For other entities, uses default physics constants.
 */

import { Vector3 } from '@babylonjs/core';
import {
  getLogger,
  Direction,
  DirectionHelper,
  PlayerMovementState,
  movementModeToKey,
  DEFAULT_STATE_VALUES,
} from '@nimbus/shared';
import type { AppContext } from '../AppContext';
import type { ChunkService } from './ChunkService';
import type { PlayerEntity } from '../types/PlayerEntity';
import type { ClientBlock } from '../types/ClientBlock';
import type {
  PhysicsEntity,
  MovementMode,
} from './physics/types';
import { WalkModeController } from './physics/WalkModeController';
import { FlyModeController } from './physics/FlyModeController';
import type { PhysicsConfig } from './physics/MovementResolver';
import type { Modifier, ModifierStack } from './ModifierService';
import type { MovementStateValues } from '@nimbus/shared';

const logger = getLogger('PhysicsService');

// Re-export for backwards compatibility
export type { PhysicsEntity, MovementMode } from './physics/types';

/**
 * Type guard to check if entity is a PlayerEntity
 */
function isPlayerEntity(entity: PhysicsEntity): entity is PlayerEntity {
  return 'playerInfo' in entity;
}

/**
 * Get state values for entity based on current movement mode
 * Returns all state-dependent values (dimensions, speeds, etc.)
 */
function getStateValuesForEntity(entity: PhysicsEntity): MovementStateValues {
  if (isPlayerEntity(entity) && entity.playerInfo.stateValues) {
    const stateKey = movementModeToKey(entity.movementMode);
    // Try movement-specific key first, then 'default', then 'walk' as final fallback
    return entity.playerInfo.stateValues[stateKey]
      || entity.playerInfo.stateValues.default
      || entity.playerInfo.stateValues.walk
      || DEFAULT_STATE_VALUES.walk;
  }

  // Default values for non-player entities
  return DEFAULT_STATE_VALUES.walk;
}

/**
 * PhysicsService - Manages physics for entities
 *
 * Features:
 * - Walk mode: XZ movement, gravity, jumping, auto-climb
 * - Fly mode: Full 3D movement, no gravity
 * - Underwater mode: Fly-like movement with gravity disabled, collisions enabled
 * - Block collision detection
 * - Auto-push-up when inside block
 * - Water detection from ClientHeightData
 *
 * For PlayerEntity: Uses dynamic values from PlayerInfo
 * For other entities: Uses default physics constants
 */
export class PhysicsService {
  private appContext: AppContext;
  private chunkService?: ChunkService;

  // Movement controllers (new modular system)
  private walkController?: WalkModeController;
  private flyController?: FlyModeController;

  // Movement state modifiers (for StackModifier system)
  private jumpModifier?: Modifier<PlayerMovementState>;
  private fallModifier?: Modifier<PlayerMovementState>;
  private swimModifier?: Modifier<PlayerMovementState>;

  // Player direction broadcast (for effects that need continuous target updates)
  private sendPlayerDirectionEnabled: boolean = false;
  private lastPlayerRotation: { yaw: number; pitch: number } | null = null;
  private lastTargetPosition: Vector3 | null = null;

  // Physics constants (global, not player-specific)
  private readonly gravity: number = -20.0; // blocks per second²
  private readonly underwaterGravity: number = -0.5; // blocks per second² (2.5% of normal, extremely slow sinking)

  // Movement constants (Source-Engine style)
  private readonly groundAcceleration: number = 100.0; // blocks per second² (fast response)
  private readonly airAcceleration: number = 10.0; // blocks per second² (limited air control)
  private readonly groundFriction: number = 6.0; // friction coefficient (exponential decay)
  private readonly airFriction: number = 0.1; // air resistance (minimal)
  private readonly maxClimbHeight: number = 0.1; // maximum auto-climb height in blocks
  private readonly coyoteTime: number = 0.1; // seconds after leaving ground when jump is still allowed

  // Default values for non-player entities
  private readonly defaultWalkSpeed: number = 5.0; // blocks per second
  private readonly defaultFlySpeed: number = 10.0; // blocks per second
  private readonly defaultJumpSpeed: number = 8.0; // blocks per second
  private readonly defaultEntityHeight: number = 1.8; // Entity height in blocks
  private readonly defaultEntityWidth: number = 0.6; // Entity width in blocks
  private readonly defaultUnderwaterSpeed: number = 3.0; // blocks per second

  // Entities managed by physics
  private entities: Map<string, PhysicsEntity> = new Map();

  // Physics enabled state - prevents falling through world on initial load
  // Physics is paused until initial chunks around player are loaded
  private physicsEnabled: boolean = false;

  // Teleportation pending state - disables physics while waiting for chunks
  // Used when player is teleported to a new location
  private teleportationPending: boolean = true;
  private teleportCheckTimer: NodeJS.Timeout | null = null;

  // Track if climbable velocity was set this frame (before updateWalkMode runs)
  private climbableVelocitySetThisFrame: Map<string, boolean> = new Map();

  // Step sound throttle interval (per entity)
  private readonly stepInterval: number = 300; // ms between step events

  // Event listeners
  private eventListeners: Map<string, Array<(...args: any[]) => void>> = new Map();

  /**
   * Get the movement state stack from PlayerService
   * Used to set physics-based movement states (JUMP, FALL, SWIM)
   */
  private get movementStateStack(): ModifierStack<PlayerMovementState> | undefined {
    return this.appContext.services.player?.movementStateStack;
  }

  constructor(appContext: AppContext) {
    this.appContext = appContext;

    // Initialize movement state modifiers after a short delay
    // This allows PlayerService and ModifierService to be fully initialized
    setTimeout(() => this.initializeMovementStateModifiers(), 100);

    logger.debug('PhysicsService initialized', {
      gravity: this.gravity,
      underwaterGravity: this.underwaterGravity,
      defaultWalkSpeed: this.defaultWalkSpeed,
      defaultFlySpeed: this.defaultFlySpeed,
      defaultUnderwaterSpeed: this.defaultUnderwaterSpeed,
    });
  }

  /**
   * Set ChunkService for collision detection (called after ChunkService is created)
   */
  setChunkService(chunkService: ChunkService): void {
    this.chunkService = chunkService;

    // Initialize new modular controllers
    const physicsConfig: PhysicsConfig = {
      gravity: this.gravity,
      underwaterGravity: this.underwaterGravity,
      groundAcceleration: this.groundAcceleration,
      airAcceleration: this.airAcceleration,
      groundFriction: this.groundFriction,
      airFriction: this.airFriction,
      jumpSpeed: this.defaultJumpSpeed,
      maxClimbHeight: this.maxClimbHeight,
      coyoteTime: this.coyoteTime,
    };

    this.walkController = new WalkModeController(this.appContext, chunkService, physicsConfig);
    this.walkController.setPhysicsService(this);
    this.flyController = new FlyModeController(this.appContext, this.defaultFlySpeed);

    logger.debug('ChunkService set for collision detection, controllers initialized');
  }

  /**
   * Initialize movement state modifiers
   * Creates modifiers for JUMP, FALL, and SWIM states
   */
  private initializeMovementStateModifiers(): void {
    const stack = this.movementStateStack;
    if (!stack) {
      logger.warn('Movement state stack not available yet');
      return;
    }

    // JUMP modifier (priority 10 - highest)
    if (!this.jumpModifier) {
      this.jumpModifier = stack.addModifier(PlayerMovementState.JUMP, 10);
      this.jumpModifier.setEnabled(false); // Initially disabled
      logger.debug('Jump modifier created (priority 10)');
    }

    // FALL modifier (priority 20)
    if (!this.fallModifier) {
      this.fallModifier = stack.addModifier(PlayerMovementState.FALL, 20);
      this.fallModifier.setEnabled(false); // Initially disabled
      logger.debug('Fall modifier created (priority 20)');
    }

    // SWIM modifier (priority 30)
    if (!this.swimModifier) {
      this.swimModifier = stack.addModifier(PlayerMovementState.SWIM, 30);
      this.swimModifier.setEnabled(false); // Initially disabled
      logger.debug('Swim modifier created (priority 30)');
    }

    logger.debug('Movement state modifiers initialized');
  }

  /**
   * Register an entity for physics simulation
   */
  registerEntity(entity: PhysicsEntity): void {
    // Initialize new fields if not present
    if (!entity.wishMove) {
      entity.wishMove = new Vector3(0, 0, 0);
    }
    if (entity.grounded === undefined) {
      entity.grounded = false;
    }
    if (entity.onSlope === undefined) {
      entity.onSlope = false;
    }
    if (entity.inWater === undefined) {
      entity.inWater = false;
    }
    if (entity.autoJump === undefined) {
      entity.autoJump = 0;
    }
    if (entity.jumpRequested === undefined) {
      entity.jumpRequested = false;
    }
    if (entity.fallDistance === undefined) {
      entity.fallDistance = 0;
    }
    if (entity.wasFalling === undefined) {
      entity.wasFalling = false;
    }
    if (!entity.lastBlockPos) {
      entity.lastBlockPos = new Vector3(
        Math.floor(entity.position.x),
        Math.floor(entity.position.y),
        Math.floor(entity.position.z)
      );
    }

    this.entities.set(entity.entityId, entity);

    // Clamp to world bounds on registration (in case entity spawns outside)
    this.clampToWorldBounds(entity);

    logger.debug('Entity registered', { entityId: entity.entityId, mode: entity.movementMode });
  }

  /**
   * Unregister an entity
   */
  unregisterEntity(entityId: string): void {
    this.entities.delete(entityId);
    logger.debug('Entity unregistered', { entityId });
  }

  /**
   * Get an entity
   */
  getEntity(entityId: string): PhysicsEntity | undefined {
    return this.entities.get(entityId);
  }

  /**
   * Enable physics simulation
   *
   * Call this after initial chunks are loaded to prevent falling through the world
   */
  enablePhysics(): void {
    if (!this.physicsEnabled) {
      this.physicsEnabled = true;
      logger.debug('Physics enabled - initial chunks loaded');
    }
  }

  /**
   * Disable physics simulation
   */
  disablePhysics(): void {
    this.physicsEnabled = false;
    logger.debug('Physics disabled');
  }

  /**
   * Check if physics is enabled
   */
  isPhysicsEnabled(): boolean {
    return this.physicsEnabled;
  }

  /**
   * Teleport entity to specific block coordinates
   * Waits for chunks to be loaded before positioning
   *
   * @param entity Entity to teleport
   * @param blockX Target block X coordinate (integer)
   * @param blockY Target block Y coordinate (integer)
   * @param blockZ Target block Z coordinate (integer)
   */
  teleport(entity: PhysicsEntity, blockPosition : Vector3, rotation? : Vector3): void {
    // Convert block coordinates to position (center of block)
    // Block N contains positions from N.0 to (N+1).0
    // Center is at N + 0.5
    const posX = Math.floor(blockPosition.x) + 0.5;
    const posY = Math.floor(blockPosition.y) + 0.5;
    const posZ = Math.floor(blockPosition.z) + 0.5;

    // Position player at target XZ, high Y for falling
    entity.position.x = posX;
    entity.position.z = posZ;
    entity.position.y = posY;
    if (rotation) {
      entity.rotation = rotation;
    }
    entity.velocity.set(0, 0, 0);

    this.teleportationPending = true;
    this.physicsEnabled = false;

    // Clear existing timer if any
    if (this.teleportCheckTimer) {
      clearInterval(this.teleportCheckTimer);
    }

    logger.debug('Teleportation pending - physics disabled, starting chunk check timer', {
      targetPosition: { x: posX, y: posY, z: posZ }
    });

    // Check every 1 second if chunk and heightData are ready
    this.teleportCheckTimer = setInterval(() => {
      this.checkTeleportationReady(entity.entityId);
    }, 1000);

    // Also check immediately
    this.checkTeleportationReady(entity.entityId);
  }

  /**
   * Check if teleportation target is ready (chunk + heightData + blocks exist)
   */
  private checkTeleportationReady(entityId: string): void {
    const entity = this.entities.get(entityId);
    if (!entity) {
      logger.warn('Entity not found for teleportation check', { entityId });
      this.cancelTeleportation();
      return;
    }

    // Get chunk service
    if (!this.chunkService) {
      logger.warn('ChunkService not available for teleportation check');
      return;
    }

    // Check if chunk is loaded
    const chunk = this.chunkService.getChunkForBlockPosition(entity.position);
    if (!chunk || !chunk.isLoaded) {
      logger.debug('Waiting for chunk to load at position', entity.position);
      return;
    }

    // Try to get heightData for better positioning
    const heightData = chunk.getHeightDataForPosition(entity.position);

    if (heightData) {
      // We have heightData - use groundLevel for precise positioning
      const oldY = entity.position.y;
      const targetY = heightData[4]; // 4 = groundLevel

      logger.debug('Teleportation ready - positioning player with heightData', {
        entityId,
        oldY,
        targetY,
        playerX: entity.position.x,
        playerZ: entity.position.z
      });

      entity.position.y = targetY;
    } else {
      // No heightData available - just enable physics and let player fall/spawn naturally
      logger.debug('Teleportation ready - no heightData, enabling physics', {
        entityId,
        position: entity.position,
      });
    }

    entity.velocity.y = 0; // Reset vertical velocity

    // Clear timer and re-enable physics
    this.cancelTeleportation();
    this.physicsEnabled = true;
    logger.debug('Physics re-enabled after teleportation');

    // Remove splash screen after teleportation is complete
    const notificationService = this.appContext.services.notification;
    if (notificationService) {
      notificationService.showSplashScreen('');
      logger.debug('Splash screen removed after teleportation');
    }
  }

  /**
   * Cancel teleportation pending mode
   */
  private cancelTeleportation(): void {
    this.teleportationPending = false;

    if (this.teleportCheckTimer) {
      clearInterval(this.teleportCheckTimer);
      this.teleportCheckTimer = null;
    }
  }

  /**
   * Update all entities
   */
  update(deltaTime: number): void {
    // Skip physics simulation if not enabled or teleportation pending
    if (!this.physicsEnabled || this.teleportationPending) {
      return;
    }

    for (const entity of this.entities.values()) {
      this.updateEntity(entity, deltaTime);
    }

    // Clear climbable flags AFTER updating entities
    // Flags will be checked in the NEXT frame's updateWalkMode
    this.climbableVelocitySetThisFrame.clear();
  }

  /**
   * Update a single entity - NEW MODULAR SYSTEM
   */
  private updateEntity(entity: PhysicsEntity, deltaTime: number): void {
    // Get entity dimensions from state values
    const dimensions = getStateValuesForEntity(entity).dimensions;

    // Determine which controller to use based on movement mode
    const useWalkController =
      entity.movementMode === 'walk' ||
      entity.movementMode === 'sprint' ||
      entity.movementMode === 'crouch' ||
      entity.movementMode === 'swim' ||
      entity.movementMode === 'climb' ||
      entity.movementMode === 'fly'; // NEW: FLY with physics (no gravity)

    const useFreeFlyController =
      entity.movementMode === 'free_fly' ||
      entity.movementMode === 'teleport';

    if (useWalkController && this.walkController) {
      // Use new modular walk controller
      // For FLY mode: Physics enabled but no gravity (handled in WalkModeController)
      this.walkController.doMovement(
        entity,
        entity.wishMove,
        entity.jumpRequested, // Pass jump request flag
        dimensions,
        deltaTime
      );
      // Reset jump flag after processing
      entity.jumpRequested = false;
    } else if (useFreeFlyController && this.flyController) {
      // Use new modular fly controller (no physics, no collisions)
      this.flyController.update(entity, entity.wishMove, deltaTime);
      // Reset jump flag (not used in free fly mode)
      entity.jumpRequested = false;
    } else {
      // Fallback to old system (should not happen)
      this.checkUnderwaterStateIfMoved(entity);

      if (entity.movementMode === 'walk') {
        this.updateWalkMode(entity, deltaTime);
      } else if (entity.movementMode === 'fly' || entity.movementMode === 'free_fly') {
        this.updateFlyMode(entity, deltaTime);
      }
      entity.jumpRequested = false;
    }

    // Reset wishMove for next frame
    entity.wishMove.set(0, 0, 0);

    // Update movement state modifiers based on physics state
    this.updateMovementStateModifiers(entity, deltaTime);
  }

  /**
   * Update movement state modifiers based on entity physics state
   * Sets JUMP, FALL, and SWIM states via StackModifier system
   */
  private updateMovementStateModifiers(entity: PhysicsEntity, deltaTime: number): void {
    // Only update for player entity
    if (!isPlayerEntity(entity)) {
      return;
    }

    // Ensure modifiers are initialized
    if (!this.jumpModifier || !this.fallModifier || !this.swimModifier) {
      return;
    }

    // Skip modifier updates when in FLY or FREE_FLY mode
    // These modes should not be overridden by physics-based states (JUMP, FALL, SWIM)
    if (entity.movementMode === 'fly' || entity.movementMode === 'free_fly') {
      // Disable all physics-based modifiers in fly modes
      this.jumpModifier.setEnabled(false);
      this.fallModifier.setEnabled(false);
      this.swimModifier.setEnabled(false);
      return;
    }

    // Only allow automatic state modifiers (JUMP, FALL) when in WALK or SWIM mode
    // For manually chosen modes (FLY, SPRINT, CROUCH), disable automatic modifiers to respect player choice
    // Note: SWIM modifier is always controlled by inWater state, not by movementMode
    const allowAutoModifiers = entity.movementMode === 'walk' || entity.movementMode === 'swim';

    // SWIM state (priority 30) - always enable when underwater (independent of movement mode)
    // This prevents endless toggling between WALK and SWIM
    this.swimModifier.setEnabled(entity.inWater);

    // JUMP state (priority 10 - highest) - enabled during jump (only in WALK mode)
    // Jump is active if jumpRequested was set this frame (handled by controller)
    // We keep it enabled until player is grounded again
    if (allowAutoModifiers) {
      if (entity.jumpRequested) {
        this.jumpModifier.setEnabled(true);
      } else if (entity.grounded) {
        this.jumpModifier.setEnabled(false);
      }
    } else {
      this.jumpModifier.setEnabled(false);
    }

    // FALL state (priority 20) - enabled when falling (only in WALK mode)
    const FALL_THRESHOLD = 2.0; // blocks - minimum fall distance to trigger FALL state

    // Track fall distance when falling
    if (entity.velocity.y < 0 && !entity.grounded) {
      // Falling - accumulate distance
      entity.fallDistance += Math.abs(entity.velocity.y) * deltaTime;
      entity.wasFalling = true;

      // Enable FALL state if threshold exceeded (only in WALK mode)
      if (allowAutoModifiers && entity.fallDistance > FALL_THRESHOLD) {
        this.fallModifier.setEnabled(true);
      } else if (!allowAutoModifiers) {
        this.fallModifier.setEnabled(false);
      }
    } else if (entity.grounded && entity.wasFalling) {
      // Just landed - handle landing event
      this.handlePlayerLanding(entity);

      // Reset fall tracking
      entity.wasFalling = false;
      entity.fallDistance = 0;
      this.fallModifier.setEnabled(false);
    }
  }

  /**
   * Handle player landing after a fall
   * Sends landing event to server if fall distance exceeds threshold
   */
  private handlePlayerLanding(entity: PhysicsEntity): void {
    const FALL_THRESHOLD = 2.0; // Same as above
    const fallDistance = entity.fallDistance;

    // Only handle significant falls
    if (fallDistance < FALL_THRESHOLD) {
      return;
    }

    // Get entity dimensions for accurate landing position
    const dimensions = getStateValuesForEntity(entity).dimensions;

    // Calculate landing block position (under player's feet)
    const landingBlockPos = new Vector3(
      Math.floor(entity.position.x),
      Math.floor(entity.position.y - dimensions.height * 0.5), // Approximate foot position
      Math.floor(entity.position.z)
    );

    logger.debug('Player landed after fall', {
      fallDistance: fallDistance.toFixed(2),
      landingBlock: {
        x: landingBlockPos.x,
        y: landingBlockPos.y,
        z: landingBlockPos.z
      }
    });

    // TODO: Send landing event to server
    // this.sendPlayerLandedEvent(entity, fallDistance, landingBlockPos);
  }

  /**
   * Check if entity block coordinates have changed and run underwater check
   *
   * Optimization: Only check underwater state when entity moves to a different block
   * to avoid expensive chunk lookups every frame.
   *
   * Since waterHeight is per block column, we only need to check when:
   * - Block X coordinate changes (Math.floor(x))
   * - Block Y coordinate changes (Math.floor(y))
   * - Block Z coordinate changes (Math.floor(z))
   *
   * @param entity Entity to check
   */
  /**
   * @deprecated OLD METHOD - No longer used
   */
  private checkUnderwaterStateIfMoved(entity: PhysicsEntity): void {
    // Deprecated - underwater state is now tracked per-entity in entity.inWater
    // Use PhysicsUtils.checkUnderwaterState() instead
  }

  /**
   * @deprecated OLD METHOD - No longer used
   * Use PhysicsUtils.checkUnderwaterState() instead
   */
  private checkUnderwaterState(entity: PhysicsEntity): void {
    // Deprecated - underwater state now tracked per-entity in entity.inWater
  }

  /**
   * @deprecated OLD METHOD - Replaced by WalkModeController
   */
  private updateWalkMode(entity: PhysicsEntity, deltaTime: number): void {
    logger.warn('updateWalkMode called - should use WalkModeController instead', {
      entityId: entity.entityId,
    });
  }

  /**
   * @deprecated OLD METHOD - Not used anymore
   */
  private getPlayerClimbableSpeed(entity: PhysicsEntity): number {
    return 0;
  }

  /**
   * Update smooth climb animation
   *
   * Interpolates position diagonally from start to target (X, Y, Z simultaneously)
   * for a smooth climbing motion that moves forward and upward at the same time.
   * Climb speed is based on entity's walk speed for consistent movement feel.
   */
  private updateClimbAnimation(entity: PhysicsEntity, deltaTime: number): void {
    if (!entity.climbState) return;

    // Calculate total distance to climb (diagonal distance)
    const dx = entity.climbState.targetX - entity.climbState.startX;
    const dy = entity.climbState.targetY - entity.climbState.startY;
    const dz = entity.climbState.targetZ - entity.climbState.startZ;
    const totalDistance = Math.sqrt(dx * dx + dy * dy + dz * dz);

    // Use walk speed to determine climb speed (blocks per second)
    const climbSpeed = this.getMoveSpeed(entity);

    // Calculate progress based on speed and distance
    // progress per frame = (speed * deltaTime) / totalDistance
    const progressIncrement = totalDistance > 0 ? (climbSpeed * deltaTime) / totalDistance : 1.0;
    entity.climbState.progress += progressIncrement;

    if (entity.climbState.progress >= 1.0) {
      // Climb complete - snap to final position
      entity.position.x = entity.climbState.targetX;
      entity.position.y = entity.climbState.targetY;
      entity.position.z = entity.climbState.targetZ;
      entity.climbState = undefined;
      entity.grounded = true;
      entity.velocity.y = 0;
    } else {
      // Smooth, linear interpolation for steady climbing motion
      // Move diagonally: X, Y, Z all interpolate simultaneously
      const t = entity.climbState.progress;

      entity.position.x = entity.climbState.startX + dx * t;
      entity.position.y = entity.climbState.startY + dy * t;
      entity.position.z = entity.climbState.startZ + dz * t;
    }
  }

  /**
   * Determine the primary movement direction from delta X and Z
   * Returns the dominant horizontal direction based on movement vector
   *
   * @param dx Delta X movement
   * @param dz Delta Z movement
   * @returns Primary direction of movement (NORTH, SOUTH, EAST, WEST)
   */
  private getMovementDirection(dx: number, dz: number): Direction {
    // Determine which axis has larger movement
    const absDx = Math.abs(dx);
    const absDz = Math.abs(dz);

    if (absDx > absDz) {
      // X-axis dominant
      return dx > 0 ? Direction.EAST : Direction.WEST;
    } else {
      // Z-axis dominant (or equal, prefer Z)
      return dz > 0 ? Direction.SOUTH : Direction.NORTH;
    }
  }

  /**
   * Check if we can enter a block from a specific direction
   *
   * Moving from WEST to EAST:
   * - canEnterFrom(targetBlock, WEST) - entering from the WEST side
   *
   * @param passableFrom Direction bitfield from block (which sides are passable)
   * @param entrySide Which side of the block we're entering from
   * @param isSolid Whether the block is solid
   * @returns true if entry is allowed, false if blocked
   */
  private canEnterFrom(passableFrom: number | undefined, entrySide: Direction, isSolid: boolean): boolean {
    // No passableFrom set - use default behavior
    if (passableFrom === undefined || passableFrom === 0) {
      return !isSolid; // Solid blocks block, non-solid blocks allow
    }

    // Check if the entry side is passable
    return DirectionHelper.hasDirection(passableFrom, entrySide);
  }

  /**
   * Check if we can leave a block towards a specific direction
   *
   * Moving WEST: We exit through the WEST side
   * - canLeaveTo(sourceBlock, WEST) checks if WEST side is passable
   *
   * Example: passableFrom = ALL but WEST
   * - canLeaveTo(block, WEST) = FALSE (WEST not in passableFrom)
   * - canLeaveTo(block, EAST) = TRUE (EAST in passableFrom)
   *
   * @param passableFrom Direction bitfield from block (which sides are passable)
   * @param exitDir Which direction we're moving towards (which side we're exiting through)
   * @param isSolid Whether the block is solid
   * @returns true if exit is allowed, false if blocked
   */
  private canLeaveTo(passableFrom: number | undefined, exitDir: Direction, isSolid: boolean): boolean {
    // No passableFrom set - always allow exit
    if (passableFrom === undefined || passableFrom === 0) {
      return true;
    }

    // Check if the exit direction/side is passable
    return DirectionHelper.hasDirection(passableFrom, exitDir);
  }

  /**
   * Check if chunk at position is loaded
   */
  private isChunkLoaded(worldX: number, worldZ: number): boolean {
    if (!this.chunkService) {
      return true; // If no chunk service, allow movement
    }

    const chunkSize = this.appContext.worldInfo?.chunkSize || 16;
    const chunkX = Math.floor(worldX / chunkSize);
    const chunkZ = Math.floor(worldZ / chunkSize);

    const chunk = this.chunkService.getChunk(chunkX, chunkZ);
    return chunk !== undefined;
  }

  /**
   * Clamp entity to loaded chunk boundaries
   * Prevents player from moving into unloaded chunks
   */
  private clampToLoadedChunks(entity: PhysicsEntity, oldX: number, oldZ: number): void {
    if (!this.chunkService) {
      return;
    }

    // Check if new position is in a loaded chunk
    if (!this.isChunkLoaded(entity.position.x, entity.position.z)) {
      // Not loaded - revert position
      entity.position.x = oldX;
      entity.position.z = oldZ;
      entity.velocity.x = 0;
      entity.velocity.z = 0;

      logger.debug('Movement blocked - chunk not loaded', {
        entityId: entity.entityId,
        attemptedX: entity.position.x,
        attemptedZ: entity.position.z,
      });
    }
  }

  /**
   * Clamp entity position to world boundaries
   */
  private clampToWorldBounds(entity: PhysicsEntity): void {
    const worldInfo = this.appContext.worldInfo;
    if (!worldInfo || !worldInfo.start || !worldInfo.stop) {
      return;
    }

    const start = worldInfo.start;
    const stop = worldInfo.stop;
    let clamped = false;

    // Clamp X
    if (entity.position.x < start.x) {
      entity.position.x = start.x;
      entity.velocity.x = 0;
      clamped = true;
    } else if (entity.position.x > stop.x) {
      entity.position.x = stop.x;
      entity.velocity.x = 0;
      clamped = true;
    }

    // Clamp Y
    if (entity.position.y < start.y) {
      entity.position.y = start.y;
      entity.velocity.y = 0;
      clamped = true;
    } else if (entity.position.y > stop.y) {
      entity.position.y = stop.y;
      entity.velocity.y = 0;
      clamped = true;
    }

    // Clamp Z
    if (entity.position.z < start.z) {
      entity.position.z = start.z;
      entity.velocity.z = 0;
      clamped = true;
    } else if (entity.position.z > stop.z) {
      entity.position.z = stop.z;
      entity.velocity.z = 0;
      clamped = true;
    }

    if (clamped) {
      logger.debug('Entity clamped to world bounds', {
        entityId: entity.entityId,
        position: { x: entity.position.x, y: entity.position.y, z: entity.position.z },
        bounds: { start, stop },
      });
    }
  }

  /**
   * Check if block at position is solid
   */
  private isBlockSolid(x: number, y: number, z: number): boolean {
    if (!this.chunkService) {
      return false;
    }

    // Floor coordinates to get block position
    const blockX = Math.floor(x);
    const blockY = Math.floor(y);
    const blockZ = Math.floor(z);

    const clientBlock = this.chunkService.getBlockAt(blockX, blockY, blockZ);
    if (!clientBlock) {
      return false; // No block = not solid
    }

    // Direct access to pre-merged modifier (much faster!)
    return clientBlock.currentModifier.physics?.solid === true;
  }

  /**
   * Get corner heights for a block using priority cascade.
   *
   * Priority order:
   * 1. Block.cornerHeights (highest priority)
   * 2. PhysicsModifier.cornerHeights
   * 3. Auto-derived from offsets (if autoCornerHeights=true)
   *    - Block.offsets (Y values of top 4 corners)
   *    - VisibilityModifier.offsets
   * 4. undefined (no corner heights)
   *
   * @param block ClientBlock to get corner heights from
   * @returns Corner heights array [NW, NE, SE, SW] or undefined
   */
  // OLD METHOD REMOVED - See physics/ subdirectory for new implementation
  /**
   * Calculate interpolated surface height at a position within a block.
   * Uses bilinear interpolation between the four corner heights.
   *
   * @param block ClientBlock with potential cornerHeights
   * @param worldX World X position (can be fractional)
   * @param worldZ World Z position (can be fractional)
   * @returns Interpolated Y position of the block surface at this location
   */
  // OLD METHOD REMOVED - See physics/ subdirectory for new implementation
  /**
   * Analyze surface state at entity position.
   * Determines surface type, height, slope, and walkability.
   *
   * This is the central function for all slope/surface-related physics.
   * Called once per frame to cache surface properties.
   *
   * @param entity Entity to analyze surface for
   * @returns SurfaceState with all surface properties
   */
  // OLD METHOD REMOVED - See physics/ subdirectory for new implementation
  /**
   * Calculate all forces acting on entity.
   * Accumulates gravity, input, slope, autoMove, and climbing forces.
   *
   * @param entity Entity to calculate forces for
   * @param surface Surface state analysis
   * @param inputVelocity Input velocity from player (before this frame)
   * @param deltaTime Frame time
   * @returns ForceState with all accumulated forces
   */
  // OLD METHOD REMOVED - See physics/ subdirectory for new implementation
  /**
   * Resolve movement with collision detection and force application.
   * Central function that applies all forces while respecting collisions.
   *
   * @param entity Entity to move
   * @param forces Accumulated forces
   * @param surface Current surface state
   * @param deltaTime Frame time
   */
  // OLD METHOD REMOVED - See physics/ subdirectory for new implementation
  /**
   * Calculate slope vector from corner heights.
   * Returns normalized slope in X and Z directions.
   *
   * @param cornerHeights Array of 4 corner heights [NW, NE, SE, SW]
   * @returns Vector2 with slope in X and Z directions (range: -1 to 1 per axis)
   */
  // OLD METHOD REMOVED - See physics/ subdirectory for new implementation
  /**
   * Get all relevant blocks around player position and aggregate their physics properties
   *
   * This central function collects:
   * - Blocks at current Y level (for horizontal collision)
   * - Blocks above (Y + 1, for climb clearance)
   * - Blocks below (Y - 1 to Y - 3, for ground detection)
   * - Blocks player occupies (for push-up, water detection, etc.)
   *
   * Future physics properties to support:
   * - resistance (movement speed reduction)
   * - climbable (ladder-like climbing)
   * - autoClimbable (auto step-up)
   * - interactive (button, door, etc.)
   * - autoMove (conveyor belt, water current)
   * - gateFromDirection (one-way passage)
   * - liquid properties (water, lava)
   *
   * @param x X position (world coordinates)
   * @param z Z position (world coordinates)
   * @param y Y position (world coordinates, feet level)
   * @param entityWidth Entity width for bounding box
   * @param entityHeight Entity height for body collision
   * @returns Block context with aggregated properties
   */
  // OLD METHOD REMOVED - See physics/ subdirectory for new implementation
  /**
   * Check ground collision and update entity state
   */
  // OLD METHOD REMOVED - See physics/ subdirectory for new implementation
  /**
   * Check if player is inside a block and push up if needed
   *
   * Note: Auto-push-up is DISABLED when block has passableFrom set (as per spec)
   */
  // OLD METHOD REMOVED - See physics/ subdirectory for new implementation
  /**
   * Apply autoMove velocity to entity based on blocks at feet level or below
   *
   * AutoMove is applied when:
   * - Player is standing on autoMove block (block below feet, Y - 1), OR
   * - Player has autoMove block at feet level (Y)
   *
   * The maximum velocity per axis from all relevant blocks is used.
   * Movement is smooth and continuous while on/in the block.
   *
   * Use case: Conveyor belts, water currents, ice sliding
   */
  // applyAutoMove() is now integrated into calculateForces() and resolveMovement()

  /**
   * Apply autoOrientationY rotation to entity based on blocks at feet level or below
   *
   * AutoOrientationY is applied when:
   * - Player is standing on autoOrientationY block (block below feet, Y - 1), OR
   * - Player has autoOrientationY block at feet level (Y)
   *
   * When multiple blocks have orientation, the last (most recent) value is used.
   * Rotation is smooth and gradual with a standard rotation speed.
   *
   * Use case: Directional blocks (arrows), rotating platforms, alignment zones
   */
  // OLD METHOD REMOVED - See physics/ subdirectory for new implementation
  /**
   * Apply sliding from sloped surfaces based on cornerHeights.
   * Sliding velocity is calculated from slope and modified by resistance.
   *
   * Sliding is applied when:
   * - Player is standing on ground (isOnGround)
   * - Block has cornerHeights defined
   * - Block has resistance (used to dampen sliding)
   *
   * Formula: effectiveSliding = slope × (1 - resistance)
   * - resistance = 0: Full sliding
   * - resistance = 1: No sliding (completely blocked)
   *
   * Use case: Ramps, slides, sloped terrain
   */
  // applySlidingFromSlope() is now integrated into calculateForces() and resolveMovement()

  /**
   * Check if player is on or in a block with autoJump and trigger jump
   *
   * AutoJump is triggered when:
   * - Player is standing on autoJump block (block below feet, Y - 1), OR
   * - Player has autoJump block at feet level (Y) - for pressure plates
   *
   * Only these two levels are checked. No occupiedBlocks check (body).
   *
   * Use case: Trampoline blocks (below), pressure plates (at feet level)
   */
  // OLD METHOD REMOVED - See physics/ subdirectory for new implementation
  /**
   * Try to move entity horizontally with collision detection and auto-climb
   *
   * Uses getPlayerBlockContext() to collect all relevant blocks and their properties.
   *
   * Decision flow:
   * 1. Get block context at target position
   * 2. No collision → Move
   * 3. Collision with solid block:
   *    - Has autoClimbable + space above + on ground → Climb + Move
   *    - No autoClimbable or blocked above → Block movement
   *
   * @param entity Entity to move
   * @param dx Delta X movement
   * @param dz Delta Z movement
   * @returns true if movement was fully handled (climbing or blocked), false if normal movement occurred
   */
  // OLD METHOD REMOVED - See physics/ subdirectory for new implementation
  /**
   * Update entity in Fly mode
   */
  private updateFlyMode(entity: PhysicsEntity, deltaTime: number): void {
    // No gravity in fly mode
    // Velocity is directly controlled by input (no damping in fly mode for precise control)
    // Position is updated in moveForward/moveRight/moveUp methods

    // Clamp to world boundaries
    this.clampToWorldBounds(entity);
  }

  /**
   * Move entity forward/backward (relative to camera)
   *
   * TODO: When underwater, use fly-like movement (including pitch) but with collisions
   *
   * @param entity Entity to move
   * @param distance Distance to move (positive = forward)
   * @param cameraYaw Camera yaw rotation in radians
   * @param cameraPitch Camera pitch rotation in radians
   */
  moveForward(entity: PhysicsEntity, distance: number, cameraYaw: number, cameraPitch: number): void {
    // NEW SYSTEM: Accumulate wishMove (normalized input)
    // distance should be normalized (-1 to +1), not actual distance in meters
    const usePitch = entity.movementMode === 'free_fly' || entity.movementMode === 'fly' || entity.movementMode === 'teleport';

    if (usePitch) {
      // 3D movement with pitch
      entity.wishMove.x += Math.sin(cameraYaw) * Math.cos(cameraPitch) * distance;
      entity.wishMove.y += -Math.sin(cameraPitch) * distance;
      entity.wishMove.z += Math.cos(cameraYaw) * Math.cos(cameraPitch) * distance;
    } else {
      // Horizontal movement (walk, sprint, crouch, swim, climb)
      entity.wishMove.x += Math.sin(cameraYaw) * distance;
      entity.wishMove.z += Math.cos(cameraYaw) * distance;
    }
  }

  /**
   * Move entity right/left (strafe)
   *
   * @param entity Entity to move
   * @param distance Distance to move (positive = right)
   * @param cameraYaw Camera yaw rotation in radians
   */
  moveRight(entity: PhysicsEntity, distance: number, cameraYaw: number): void {
    // NEW SYSTEM: Set wishMove for strafe movement
    entity.wishMove.x += Math.sin(cameraYaw + Math.PI / 2) * distance;
    entity.wishMove.z += Math.cos(cameraYaw + Math.PI / 2) * distance;
  }

  /**
   * Move entity up/down (Fly mode or underwater)
   *
   * TODO: Underwater movement should also allow up/down movement
   *
   * @param entity Entity to move
   * @param distance Distance to move (positive = up)
   */
  moveUp(entity: PhysicsEntity, distance: number): void {
    // NEW SYSTEM: Set wishMove for vertical movement
    if (entity.movementMode === 'free_fly' || entity.movementMode === 'fly' || entity.movementMode === 'teleport' || entity.movementMode === 'swim') {
      entity.wishMove.y = distance;
    }
  }

  /**
   * Jump (Walk mode only)
   *
   * For PlayerEntity: Uses cached effectiveJumpSpeed
   * For other entities: Uses default jumpSpeed
   */
  jump(entity: PhysicsEntity): void {
    // NEW SYSTEM: Set jumpRequested flag instead of direct velocity manipulation
    // MovementResolver.handleJump() will process this with coyote time
    entity.jumpRequested = true;

    logger.debug('Jump requested', {
      entityId: entity.entityId,
      grounded: entity.grounded,
    });
  }

  /**
   * Set movement mode
   */
  setMovementMode(entity: PhysicsEntity, mode: MovementMode): void {
    // FREE_FLY mode only available in editor
    if (mode === 'free_fly' && !__EDITOR__) {
      logger.warn('FREE_FLY mode only available in Editor build');
      return;
    }

    entity.movementMode = mode;
    logger.debug('Movement mode changed', { entityId: entity.entityId, mode });

    // Reset velocity when switching modes
    entity.velocity.set(0, 0, 0);

    // In fly modes, no ground state
    if (mode === 'fly' || mode === 'free_fly') {
      entity.grounded = false;
    }
  }

  /**
   * Toggle between Walk and FREE_FLY modes (editor only)
   */
  toggleMovementMode(entity: PhysicsEntity): void {
    const newMode = entity.movementMode === 'walk' ? 'free_fly' : 'walk';
    this.setMovementMode(entity, newMode);
  }

  /**
   * Get current move speed for entity
   *
   * Returns appropriate speed based on movement mode, underwater state, and PlayerEntity cached values.
   * For PlayerEntity: Uses cached effective values (updated via 'playerInfo:updated' event)
   * For other entities: Uses default constants
   *
   * TODO: Add support for sprint/crawl/riding states
   */
  getMoveSpeed(entity: PhysicsEntity): number {
    // NEW SYSTEM: MovementResolver.getMoveSpeed() handles this
    // This method is kept for backwards compatibility only
    if (isPlayerEntity(entity)) {
      // Player: Use cached effective speed (updated when movement state changes)
      return entity.effectiveSpeed;
    } else {
      // Other entities: Use default constants
      if (entity.inWater) {
        return this.defaultUnderwaterSpeed;
      }

      return entity.movementMode === 'walk' ? this.defaultWalkSpeed : this.defaultFlySpeed;
    }
  }

  /**
   * Register event listener
   */
  on(event: string, listener: (...args: any[]) => void): void {
    if (!this.eventListeners.has(event)) {
      this.eventListeners.set(event, []);
    }
    this.eventListeners.get(event)!.push(listener);
  }

  /**
   * Unregister event listener
   */
  off(event: string, listener: (...args: any[]) => void): void {
    const listeners = this.eventListeners.get(event);
    if (listeners) {
      const index = listeners.indexOf(listener);
      if (index !== -1) {
        listeners.splice(index, 1);
      }
    }
  }

  /**
   * Emit event
   */
  private emit(event: string, ...args: any[]): void {
    const listeners = this.eventListeners.get(event);
    if (listeners) {
      listeners.forEach(listener => listener(...args));
    }
  }

  /**
   * Emit step over event when entity moves over a block
   * Called from movement controllers
   *
   * @param entity Physics entity
   * @param block Block entity is stepping on
   * @param movementType Type of movement (walk, jump, etc.)
   */
  emitStepOver(entity: any, block: ClientBlock, movementType: string): void {
    // Throttle step events (max once per stepInterval ms per entity)
    const now = Date.now();
    const lastStep = entity.lastStepTime;

    if (lastStep && now - lastStep < this.stepInterval) {
      return; // Too soon since last step
    }

    // Update last step time
    entity.lastStepTime = now;

    // Emit event
    this.emit('step:over', {
      entityId: entity.entityId,
      block,
      movementType,
    });
  }

  /**
   * Dispose physics service
   */
  dispose(): void {
    this.entities.clear();
    this.eventListeners.clear();

    // Dispose controllers
    if (this.walkController) {
      this.walkController.dispose();
    }

    logger.debug('PhysicsService disposed');
  }

  /**
   * Enable/disable player direction broadcast
   *
   * When enabled, emits 'player:direction' event when player rotation or target changes.
   * Used by effects like beam:follow that need continuous target updates.
   *
   * @param enabled Whether to broadcast direction updates
   */
  setPlayerDirectionBroadcast(enabled: boolean): void {
    this.sendPlayerDirectionEnabled = enabled;

    if (!enabled) {
      // Reset tracking when disabled
      this.lastPlayerRotation = null;
      this.lastTargetPosition = null;
    }

    logger.debug('Player direction broadcast', { enabled });
  }

  /**
   * Check player direction and emit event if changed
   * Should be called each frame (from update loop)
   */
  checkAndEmitPlayerDirection(): void {
    if (!this.sendPlayerDirectionEnabled) {
      return;
    }

    const playerService = this.appContext.services.player;
    const cameraService = this.appContext.services.camera;
    const selectService = this.appContext.services.select;

    if (!playerService || !cameraService || !selectService) {
      return;
    }

    // Get current rotation
    const rotation = cameraService.getRotation();

    // Get current target
    const targetEntity = selectService.getCurrentSelectedEntity();
    const targetBlock = selectService.getCurrentSelectedBlock();

    let targetPos: Vector3 | null = null;
    let isEntity = false;
    let isBlock = false;

    if (targetEntity) {
      // ClientEntity has currentPosition
      const pos = targetEntity.currentPosition;
      if (pos) {
        // Add +1.0 Y offset for entities (to aim at center/head height)
        targetPos = new Vector3(pos.x, pos.y + 1.0, pos.z);
        isEntity = true;
      }
    } else if (targetBlock) {
      // Add 0.5 offset for blocks to center
      targetPos = new Vector3(
        targetBlock.block.position.x + 0.5,
        targetBlock.block.position.y + 0.5,
        targetBlock.block.position.z + 0.5
      );
      isBlock = true;
    }

    // Check if rotation or target changed
    const rotationChanged =
      !this.lastPlayerRotation ||
      this.lastPlayerRotation.yaw !== rotation.y ||
      this.lastPlayerRotation.pitch !== rotation.x;

    const targetChanged =
      !this.lastTargetPosition ||
      !targetPos ||
      !this.lastTargetPosition.equals(targetPos);

    if (rotationChanged || targetChanged) {
      // Update tracking
      this.lastPlayerRotation = { yaw: rotation.y, pitch: rotation.x };
      this.lastTargetPosition = targetPos;

      // Emit event with player position and target
      // TODO: emit is private, need to implement public method or remove this feature
      // const playerPos = playerService.getPosition();
      // playerService.emit('player:direction', {
      //   playerPos,
      //   rotation,
      //   targetPos,
      // });
    }
  }
}
