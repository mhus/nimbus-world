/**
 * WalkModeController - Walk/ground movement physics
 *
 * Implements the complete doMovement schema:
 * 1. Vorbereitung (Teleport/Flight checks)
 * 2. Environment prüfen (passableFrom)
 * 3. Bodenprüfung / Auto-Funktionen
 * 4. Semi-Solid & Slopes
 * 5. Bewegung / Kollision
 * 6. Weltgrenzen
 * 7. Bewegung anwenden
 */

import { Vector3 } from '@babylonjs/core';
import { getLogger } from '@nimbus/shared';
import type { PhysicsEntity } from './types';
import type { AppContext } from '../../AppContext';
import type { ChunkService } from '../ChunkService';
import { BlockContextAnalyzer } from './BlockContextAnalyzer';
import { CollisionDetector } from './CollisionDetector';
import { MovementResolver, PhysicsConfig } from './MovementResolver';
import { SurfaceAnalyzer } from './SurfaceAnalyzer';
import * as PhysicsUtils from './PhysicsUtils';

const logger = getLogger('WalkModeController');

/**
 * Entity dimensions
 */
interface EntityDimensions {
  height: number;
  width: number;
  footprint: number;
}

/**
 * WalkModeController - Handles walk/ground physics
 */
export class WalkModeController {
  private contextAnalyzer: BlockContextAnalyzer;
  private collisionDetector: CollisionDetector;
  private movementResolver: MovementResolver;
  private surfaceAnalyzer: SurfaceAnalyzer;
  private physicsService?: any; // Reference to PhysicsService for event emission

  constructor(
    private appContext: AppContext,
    private chunkService: ChunkService,
    physicsConfig: PhysicsConfig
  ) {
    this.contextAnalyzer = new BlockContextAnalyzer(chunkService);
    this.collisionDetector = new CollisionDetector(
      chunkService,
      this.contextAnalyzer,
      physicsConfig.maxClimbHeight
    );
    this.movementResolver = new MovementResolver(physicsConfig);
    this.surfaceAnalyzer = new SurfaceAnalyzer(chunkService);

    // Setup collision event callback
    this.collisionDetector.setCollisionEventCallback((x, y, z, action, id, gId) => {
      // Send collision event to server (action: 'collision' or 'climb')
      this.appContext.services.network?.sendBlockInteraction(
        x,
        y,
        z,
        action,
        undefined,
        id,
        gId
      );

      // Emit event for collision audio (only for actual collisions, not climb)
      if (action === 'collision' && this.physicsService) {
        // Get the block for audio playback
        const clientBlock = this.chunkService.getBlockAt(x, y, z);
        if (clientBlock) {
          this.physicsService.emit('collide:withBlock', {
            entityId: 'player', // TODO: Get actual entity ID
            block: clientBlock,
            x,
            y,
            z
          });
        }
      }
    });
  }

  /**
   * Main movement update - implements complete schema
   */
  doMovement(
    entity: PhysicsEntity,
    movementVector: Vector3,
    startJump: boolean,
    dimensions: EntityDimensions,
    deltaTime: number
  ): void {
    // === 1. VORBEREITUNG ===

    // Store movement intention
    entity.wishMove.copyFrom(movementVector);

    // Check if chunks are loaded
    const chunkSize = this.appContext.worldInfo?.chunkSize || 16;
    if (!PhysicsUtils.isChunkLoaded(entity.position.x, entity.position.z, this.chunkService, chunkSize)) {
      // Chunks not loaded - prevent movement
      entity.velocity.x = 0;
      entity.velocity.z = 0;
      return;
    }

    // === 2. ENVIRONMENT PRÜFEN ===

    const context = this.contextAnalyzer.getContext(entity, dimensions);

    // Check if stuck in solid block
    if (context.currentBlocks.hasSolid && !context.currentBlocks.allNonSolid) {
      // Check passableFrom
      if (context.currentBlocks.passableFrom !== undefined) {
        // Has passableFrom - may be able to exit
        // This is handled in collision detection
      } else {
        // Completely stuck - try to push up
        if (this.collisionDetector.checkAndPushUp(entity, dimensions)) {
          return; // Pushed up, skip this frame
        }
      }
    }

    // === 3. BODENPRÜFUNG / AUTO-FUNKTIONEN ===

    // Update grounded state
    this.collisionDetector.checkGroundCollision(entity, dimensions);

    // Apply auto-functions from ground/foot blocks
    if (entity.grounded || context.footBlocks.hasSolid) {
      // Auto-rotation
      if (context.footBlocks.hasAutoRotationY && context.footBlocks.autoOrientationY !== undefined) {
        this.movementResolver.applyAutoOrientation(
          entity,
          context.footBlocks.autoOrientationY,
          deltaTime
        );
      } else if (
        context.groundBlocks.hasAutoRotationY &&
        context.groundBlocks.autoOrientationY !== undefined
      ) {
        this.movementResolver.applyAutoOrientation(
          entity,
          context.groundBlocks.autoOrientationY,
          deltaTime
        );
      }

      // Auto-move
      if (context.footBlocks.hasAutoMove) {
        this.movementResolver.applyAutoMove(entity, context.footBlocks.autoMove, deltaTime);
      } else if (context.groundBlocks.hasAutoMove) {
        this.movementResolver.applyAutoMove(entity, context.groundBlocks.autoMove, deltaTime);
      }

      // Auto-jump
      entity.autoJump = 0;
      if (context.footBlocks.autoJump > 0 || context.groundBlocks.autoJump > 0) {
        entity.autoJump = Math.max(context.footBlocks.autoJump, context.groundBlocks.autoJump);
        if (!startJump) {
          // Trigger auto-jump
          startJump = true;
        }
      }
    }

    // === 4. SEMI-SOLID & SLOPES ===

    if (entity.onSlope && context.groundFootBlocks.cornerHeights) {
      // Apply slope forces
      const slope = this.surfaceAnalyzer.calculateSlope(context.groundFootBlocks.cornerHeights);
      this.movementResolver.applySlopeForces(entity, slope, deltaTime);

      // Clamp to slope surface
      if (context.groundFootBlocks.maxHeight > 0) {
        const surfaceY = Math.floor(entity.position.y) + 1.0 + context.groundFootBlocks.maxHeight;
        if (entity.position.y < surfaceY) {
          entity.position.y = surfaceY;
        }
      }
    }

    // === 5. BEWEGUNG / KOLLISION ===

    // Update velocity
    const resistance = context.groundBlocks.resistance;
    this.movementResolver.updateVelocity(entity, entity.wishMove, context, resistance, deltaTime);

    // Handle jump
    this.movementResolver.handleJump(entity, startJump, deltaTime, entity.autoJump);

    // Calculate movement distance
    const movement = entity.velocity.scale(deltaTime);
    const movementDistance = movement.length();

    // Max step size to prevent tunneling through blocks (0.8 blocks)
    const maxStepSize = 0.8;

    let resolvedPosition: Vector3;

    // If movement is larger than max step, split into multiple steps
    if (movementDistance > maxStepSize) {
      // Calculate number of steps needed
      const steps = Math.ceil(movementDistance / maxStepSize);
      const stepDelta = deltaTime / steps;

      // Start from current position
      resolvedPosition = entity.position.clone();

      // Execute movement in multiple steps
      for (let step = 0; step < steps; step++) {
        const stepMovement = entity.velocity.scale(stepDelta);
        const stepWishPosition = resolvedPosition.add(stepMovement);

        // Resolve collision for this step
        resolvedPosition = this.collisionDetector.resolveCollision(
          entity,
          stepWishPosition,
          dimensions
        );

        // Update entity position for next step collision check
        entity.position.copyFrom(resolvedPosition);
      }
    } else {
      // Normal single-step movement
      const wishPosition = entity.position.add(movement);

      // Resolve collisions (Swept-AABB: Y → X → Z)
      resolvedPosition = this.collisionDetector.resolveCollision(
        entity,
        wishPosition,
        dimensions
      );
    }

    // === 5.5 ENTITY COLLISIONS ===

    // Check entity collisions
    const entityService = this.appContext.services.entity;
    if (entityService) {
      const entitiesInRadius = entityService.getEntitiesInRadius(
        resolvedPosition,
        entityService.getCollisionCheckRadius(),
        entity.movementMode
      );

      const entityCollisionResult = this.collisionDetector.checkEntityCollisions(
        resolvedPosition,
        dimensions,
        entitiesInRadius
      );

      // Apply entity collision correction
      resolvedPosition.copyFrom(entityCollisionResult.position);

      // Notify EntityService of collisions
      for (const entityId of entityCollisionResult.collidedEntities) {
        entityService.onPlayerCollision(entityId, resolvedPosition);
      }
    }

    // === 6. WELTGRENZEN ===

    // Apply position
    entity.position.copyFrom(resolvedPosition);

    // Clamp to world bounds
    PhysicsUtils.clampToWorldBounds(entity, this.appContext);

    // Clamp to loaded chunks
    PhysicsUtils.clampToLoadedChunks(
      entity,
      entity.position.x,
      entity.position.z,
      this.chunkService,
      chunkSize
    );

    // === 7. CHECK UNDERWATER STATE ===

    // Only check if block position changed
    if (PhysicsUtils.hasBlockPositionChanged(entity)) {
      const eyeHeight = dimensions.height * 0.9; // Eye height approximation
      PhysicsUtils.checkUnderwaterState(entity, this.chunkService, this.appContext, eyeHeight);

      // Invalidate context cache
      this.contextAnalyzer.invalidateCache(entity.entityId);
    }

    // === 8. EMIT STEP OVER EVENT ===

    // Emit step event if entity is moving on ground OR swimming
    const isMoving = movementVector.lengthSquared() > 0.001;
    const isSwimming = entity.movementMode === 'swim';

    if (isMoving && (entity.grounded || isSwimming)) {
      // Determine movement type based on jump, crouch, and swim state
      let movementType = 'walk';
      if (isSwimming) {
        movementType = 'swim';
      } else if (startJump) {
        movementType = 'jump';
      } else if (entity.movementMode === 'crouch') {
        movementType = 'crouch';
      }

      if (isSwimming) {
        // SWIM mode: Send event with player position instead of block
        // Uses PhysicsService.emitStepOver which respects stepInterval (300ms throttle)
        if (this.physicsService) {
          // Create a minimal block-like object with player position
          const swimPosition = {
            block: {
              position: {
                x: Math.floor(entity.position.x),
                y: Math.floor(entity.position.y),
                z: Math.floor(entity.position.z),
              },
            },
            blockType: { id: 0 }, // Water/no block
            audioSteps: undefined, // No step audio when swimming
          };
          this.physicsService.emitStepOver(entity, swimPosition as any, movementType);
        }
      } else if (entity.grounded) {
        // Normal ground movement: Get ground block
        // Uses PhysicsService.emitStepOver which respects stepInterval (300ms throttle)
        const groundBlock = context.groundBlocks.blocks.find(b => b.block);

        if (groundBlock && groundBlock.block && this.physicsService) {
          this.physicsService.emitStepOver(entity, groundBlock.block, movementType);
        }
      }
    }
  }

  /**
   * Set physics service reference for event emission
   */
  setPhysicsService(physicsService: any): void {
    this.physicsService = physicsService;
  }

  /**
   * Dispose resources
   */
  dispose(): void {
    this.movementResolver.dispose();
  }
}
