/**
 * EntityPhysicsController - Client-side physics for entities
 *
 * This controller handles lightweight physics simulation on the client:
 * - Receives physics-enabled pathways from server as "prediction hints"
 * - Runs local physics simulation (gravity + block collision)
 * - Smoothly interpolates entity movement
 * - Distance-based update rates for performance
 */

import type { Vector3 } from '@nimbus/shared';
import type { Rotation } from '@nimbus/shared';
import type { ClientEntity } from '@nimbus/shared';
import type { EntityModel } from '@nimbus/shared';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('EntityPhysicsController');

/**
 * Physics constants (aligned with server physics)
 */
const PHYSICS_CONSTANTS = {
  /** Gravity acceleration (blocks/s²) */
  GRAVITY: -20.0,

  /** Ground friction coefficient */
  GROUND_FRICTION: 0.8,

  /** Air drag coefficient */
  AIR_DRAG: 0.98,

  /** Minimum velocity magnitude */
  MIN_VELOCITY: 0.001,

  /** Ground detection tolerance (blocks) */
  GROUND_TOLERANCE: 0.1,
};

/**
 * Distance-based update rates
 */
const UPDATE_RATES = {
  /** Update every frame (< 20 blocks) */
  NEAR: { distance: 20, frameSkip: 0 }, // 60 FPS

  /** Update every 2 frames (20-40 blocks) */
  MEDIUM: { distance: 40, frameSkip: 1 }, // 30 FPS

  /** Update every 6 frames (> 40 blocks) */
  FAR: { distance: Infinity, frameSkip: 5 }, // 10 FPS
};

/**
 * Physics state for an entity
 */
interface EntityPhysicsState {
  velocity: Vector3;
  grounded: boolean;
  framesSinceLastUpdate: number;
}

/**
 * EntityPhysicsController - Manages client-side entity physics
 */
export class EntityPhysicsController {
  private physicsStates: Map<string, EntityPhysicsState> = new Map();
  private playerPosition: Vector3 = { x: 0, y: 0, z: 0 };

  /**
   * Update player position for distance-based update rates
   */
  setPlayerPosition(position: Vector3): void {
    this.playerPosition = position;
  }

  /**
   * Initialize physics state for an entity
   */
  initializePhysics(
    clientEntity: ClientEntity,
    velocity: Vector3 = { x: 0, y: 0, z: 0 },
    grounded: boolean = false
  ): void {
    this.physicsStates.set(clientEntity.entity.id, {
      velocity: { ...velocity },
      grounded,
      framesSinceLastUpdate: 0,
    });
  }

  /**
   * Update physics for an entity
   *
   * @param clientEntity Client entity to update
   * @param deltaTime Time step in seconds
   * @param isBlockSolid Function to check if block at position is solid
   * @returns True if physics was updated (based on distance)
   */
  updatePhysics(
    clientEntity: ClientEntity,
    deltaTime: number,
    isBlockSolid: (x: number, y: number, z: number) => boolean
  ): boolean {
    // Get or create physics state
    let state = this.physicsStates.get(clientEntity.entity.id);
    if (!state) {
      this.initializePhysics(clientEntity);
      state = this.physicsStates.get(clientEntity.entity.id)!;
    }

    // Distance-based update rate
    const distance = this.getDistanceToPlayer(clientEntity.currentPosition);
    const updateRate = this.getUpdateRate(distance);

    state.framesSinceLastUpdate++;
    if (state.framesSinceLastUpdate <= updateRate.frameSkip) {
      return false; // Skip this frame
    }

    state.framesSinceLastUpdate = 0;

    // Get entity dimensions
    const dimensions = this.getEntityDimensions(clientEntity.model);

    // Current position and velocity
    const position = clientEntity.currentPosition;
    const velocity = state.velocity;

    // 1. NO GRAVITY on client - entity stays at current height
    // Server controls Y position via pathways
    // velocity.y += PHYSICS_CONSTANTS.GRAVITY * deltaTime; // DISABLED

    // 2. Apply friction/drag
    if (state.grounded) {
      velocity.x *= PHYSICS_CONSTANTS.GROUND_FRICTION;
      velocity.z *= PHYSICS_CONSTANTS.GROUND_FRICTION;

      if (Math.abs(velocity.x) < PHYSICS_CONSTANTS.MIN_VELOCITY) velocity.x = 0;
      if (Math.abs(velocity.z) < PHYSICS_CONSTANTS.MIN_VELOCITY) velocity.z = 0;
    } else {
      velocity.x *= PHYSICS_CONSTANTS.AIR_DRAG;
      velocity.z *= PHYSICS_CONSTANTS.AIR_DRAG;
    }

    // 3. Integrate velocity (only X and Z, no Y - server controls height)
    const newPosition = {
      x: position.x + velocity.x * deltaTime,
      y: position.y, // Keep Y from server pathway
      z: position.z + velocity.z * deltaTime,
    };

    // 4. Y-Correction: Check block at entity position (from server pathway)
    const floorX = Math.floor(newPosition.x);
    const floorY = Math.floor(newPosition.y);
    const floorZ = Math.floor(newPosition.z);

    // Check if block at feet position is solid
    const blockAtFeetSolid = isBlockSolid(floorX, floorY, floorZ);

    if (blockAtFeetSolid) {
      // Block at feet is solid → lift entity on top of block
      newPosition.y = floorY + 1;
    } else {
      // Block at feet is not solid → check block below
      const blockBelowSolid = isBlockSolid(floorX, floorY - 1, floorZ);

      if (blockBelowSolid) {
        // Block below is solid → correct entity down to stand on it
        newPosition.y = floorY;
      }
      // else: no solid block at feet or below → keep Y from server pathway
    }

    // Update grounded state
    state.grounded = true; // Always grounded in lightweight mode
    velocity.y = 0; // Always zero

    // 5. Apply new position
    clientEntity.currentPosition = newPosition;
    state.grounded = true;

    return true; // Physics was updated
  }

  /**
   * Apply server-predicted velocity to entity
   * Used when receiving physics pathways from server
   */
  applyServerVelocity(clientEntity: ClientEntity, velocity: Vector3): void {
    let state = this.physicsStates.get(clientEntity.entity.id);
    if (!state) {
      this.initializePhysics(clientEntity, velocity);
    } else {
      // Blend server velocity with current velocity for smoothness
      state.velocity.x = velocity.x * 0.7 + state.velocity.x * 0.3;
      state.velocity.y = velocity.y;
      state.velocity.z = velocity.z * 0.7 + state.velocity.z * 0.3;
    }
  }

  /**
   * Get entity dimensions from model
   */
  private getEntityDimensions(entityModel: EntityModel): {
    height: number;
    width: number;
    footprint: number;
  } {
    const dimensions = entityModel.dimensions;
    return (
      dimensions.walk ??
      dimensions.sprint ??
      dimensions.crouch ??
      dimensions.swim ??
      dimensions.fly ??
      { height: 1.8, width: 0.6, footprint: 0.6 }
    );
  }

  /**
   * Get distance from entity to player
   */
  private getDistanceToPlayer(entityPosition: Vector3): number {
    const dx = entityPosition.x - this.playerPosition.x;
    const dy = entityPosition.y - this.playerPosition.y;
    const dz = entityPosition.z - this.playerPosition.z;
    return Math.sqrt(dx * dx + dy * dy + dz * dz);
  }

  /**
   * Get update rate based on distance
   */
  private getUpdateRate(distance: number): { distance: number; frameSkip: number } {
    if (distance < UPDATE_RATES.NEAR.distance) {
      return UPDATE_RATES.NEAR;
    } else if (distance < UPDATE_RATES.MEDIUM.distance) {
      return UPDATE_RATES.MEDIUM;
    } else {
      return UPDATE_RATES.FAR;
    }
  }

  /**
   * Get current velocity for an entity
   */
  getVelocity(entityId: string): Vector3 | null {
    const state = this.physicsStates.get(entityId);
    return state ? { ...state.velocity } : null;
  }

  /**
   * Check if entity is grounded
   */
  isGrounded(entityId: string): boolean {
    return this.physicsStates.get(entityId)?.grounded ?? false;
  }

  /**
   * Remove physics state for an entity (cleanup)
   */
  removeEntity(entityId: string): void {
    this.physicsStates.delete(entityId);
  }
}
