/**
 * MovementResolver - Velocity and force calculations
 *
 * Implements Source-Engine style movement with:
 * - Approach-based velocity control
 * - Exponential friction decay
 * - Separation of wishMove (intent) and velocity (state)
 * - Ground/air physics distinction
 */

import { Vector3 } from '@babylonjs/core';
import { getLogger } from '@nimbus/shared';
import type { PhysicsEntity, PlayerBlockContext } from './types';
import type { PlayerEntity } from '../../types/PlayerEntity';

const logger = getLogger('MovementResolver');

/**
 * Physics configuration
 */
export interface PhysicsConfig {
  gravity: number; // blocks/s² (e.g., -20.0)
  underwaterGravity: number; // blocks/s² (e.g., -2.0)
  groundAcceleration: number; // blocks/s² (e.g., 100.0)
  airAcceleration: number; // blocks/s² (e.g., 10.0)
  groundFriction: number; // friction coefficient (e.g., 6.0)
  airFriction: number; // air resistance (e.g., 0.1)
  jumpSpeed: number; // blocks/s (e.g., 8.0)
  maxClimbHeight: number; // blocks (e.g., 0.1)
  coyoteTime: number; // seconds (e.g., 0.1)
}

/**
 * Type guard for PlayerEntity
 */
function isPlayerEntity(entity: PhysicsEntity): entity is PlayerEntity {
  return 'playerInfo' in entity;
}

/**
 * MovementResolver - Handles velocity and movement physics
 */
export class MovementResolver {
  private coyoteTimeTracking: Map<string, number> = new Map();

  constructor(private config: PhysicsConfig) {}

  /**
   * Get move speed for entity
   * Uses cached value from PhysicsEntity (updated on state change)
   */
  getMoveSpeed(entity: PhysicsEntity): number {
    return entity.effectiveSpeed;
  }

  /**
   * Get jump speed for entity
   * Uses cached value from PhysicsEntity (varies by state: WALK=8.0, CROUCH=4.0, RIDING=10.0)
   */
  getJumpSpeed(entity: PhysicsEntity): number {
    return entity.effectiveJumpSpeed;
  }

  /**
   * Update velocity based on wishMove (Source-Engine style)
   *
   * @param entity Entity to update
   * @param wishMove Movement intention (input vector)
   * @param context Block context for ground detection
   * @param resistance Ground resistance (0-1)
   * @param deltaTime Frame time
   */
  updateVelocity(
    entity: PhysicsEntity,
    wishMove: Vector3,
    context: PlayerBlockContext,
    resistance: number,
    deltaTime: number
  ): void {
    const grounded = entity.grounded;
    const inWater = entity.inWater;

    // Get speed for current mode
    const maxSpeed = this.getMoveSpeed(entity);

    // Apply resistance to max speed
    const effectiveMaxSpeed = maxSpeed * (1 - resistance);

    // === PLANAR VELOCITY (X, Z) ===

    // Calculate target planar velocity from wishMove
    const wishDir = new Vector3(wishMove.x, 0, wishMove.z);
    const wishLength = wishDir.length();

    let vPlanarTarget: Vector3;
    if (wishLength > 0.001) {
      // Normalize and scale by speed and input magnitude
      wishDir.normalize();
      vPlanarTarget = wishDir.scale(effectiveMaxSpeed * Math.min(wishLength, 1.0));
    } else {
      vPlanarTarget = Vector3.Zero();
    }

    // Approach target velocity with acceleration
    const accel = grounded ? this.config.groundAcceleration : this.config.airAcceleration;
    const vPlanarCurrent = new Vector3(entity.velocity.x, 0, entity.velocity.z);
    const vPlanarNew = this.approach(vPlanarCurrent, vPlanarTarget, accel * deltaTime);

    // Apply friction (exponential decay)
    const friction = grounded ? this.config.groundFriction : this.config.airFriction;
    const frictionFactor = Math.exp(-friction * deltaTime);
    vPlanarNew.scaleInPlace(frictionFactor);

    // Update entity velocity
    entity.velocity.x = vPlanarNew.x;
    entity.velocity.z = vPlanarNew.z;

    // === VERTICAL VELOCITY (Y) ===

    // Gravity (disabled for FLY and FREE_FLY modes, but all other physics are active)
    if (!grounded && entity.movementMode !== 'climb' && entity.movementMode !== 'fly' && entity.movementMode !== 'free_fly') {
      const gravityForce = inWater ? this.config.underwaterGravity : this.config.gravity;
      entity.velocity.y += gravityForce * deltaTime;
    }

    // Clamp vertical velocity (terminal velocity)
    const terminalVelocity = 50.0; // blocks/s
    if (entity.velocity.y < -terminalVelocity) {
      entity.velocity.y = -terminalVelocity;
    }

    // Climbing mode: use wishMove.y directly
    if (entity.movementMode === 'climb') {
      entity.velocity.y = wishMove.y * this.getMoveSpeed(entity);
    }

    // Fly mode: use wishMove.y directly (full 3D control)
    if (entity.movementMode === 'fly' || entity.movementMode === 'free_fly') {
      entity.velocity.y = wishMove.y * this.getMoveSpeed(entity);
    }
  }

  /**
   * Handle jump input
   *
   * @param entity Entity to jump
   * @param startJump Jump button pressed this frame
   * @param deltaTime Frame time
   */
  handleJump(entity: PhysicsEntity, startJump: boolean, deltaTime: number, jumpValue : number): void {
    // Underwater: Jump always works (swimming upward)
    // Jump force scaled by underwater gravity ratio, but multiplied for better control
    if (entity.inWater && startJump) {
      const gravityRatio = Math.abs(this.config.underwaterGravity / this.config.gravity);
      entity.velocity.y = (jumpValue > 0 ? jumpValue : this.getJumpSpeed(entity)) * gravityRatio * 4.0; // 4x boost for better underwater swimming
      return;
    }

    // Update coyote time
    const coyoteKey = entity.entityId;
    let timeSinceGrounded = this.coyoteTimeTracking.get(coyoteKey) || 0;

    if (entity.grounded) {
      timeSinceGrounded = 0;
    } else {
      timeSinceGrounded += deltaTime;
    }

    this.coyoteTimeTracking.set(coyoteKey, timeSinceGrounded);

    // Jump if grounded or within coyote time
    if (startJump && timeSinceGrounded <= this.config.coyoteTime) {
      entity.velocity.y = jumpValue > 0 ? jumpValue : this.getJumpSpeed(entity);
      entity.grounded = false;
      this.coyoteTimeTracking.set(coyoteKey, this.config.coyoteTime + 1); // Prevent double jump

      // Emit jump event (for animations, sound effects, etc.)
      this.onJump(entity);
    }
  }

  /**
   * Called when entity jumps (for animations, sound, etc.)
   */
  private onJump(entity: PhysicsEntity): void {
    // Emit custom event on entity (can be subscribed to by PlayerService)
    (entity as any).onJump?.();
  }

  /**
   * Apply slope forces
   *
   * @param entity Entity on slope
   * @param slopeVector Slope direction and magnitude
   * @param deltaTime Frame time
   */
  applySlopeForces(entity: PhysicsEntity, slopeVector: { x: number; z: number }, deltaTime: number): void {
    // Slope sliding force (proportional to slope steepness)
    const slideForce = 5.0; // blocks/s² per unit slope
    entity.velocity.x += slopeVector.x * slideForce * deltaTime;
    entity.velocity.z += slopeVector.z * slideForce * deltaTime;
  }

  /**
   * Apply auto-move forces (conveyors, currents)
   *
   * @param entity Entity to move
   * @param autoMove Auto-move vector from blocks
   * @param deltaTime Frame time
   */
  applyAutoMove(entity: PhysicsEntity, autoMove: { x: number; y: number; z: number }, deltaTime: number): void {
    // Auto-move is additive to velocity
    entity.velocity.x += autoMove.x * deltaTime;
    entity.velocity.y += autoMove.y * deltaTime;
    entity.velocity.z += autoMove.z * deltaTime;
  }

  /**
   * Apply auto-orientation
   *
   * @param entity Entity to rotate
   * @param targetYaw Target yaw angle (radians)
   * @param deltaTime Frame time
   */
  applyAutoOrientation(entity: PhysicsEntity, targetYaw: number, deltaTime: number): void {
    // Get turn speed
    let turnSpeed = 2.0; // default radians/s
    if (isPlayerEntity(entity)) {
      turnSpeed = entity.playerInfo.effectiveTurnSpeed;
    }

    // Lerp towards target
    const diff = targetYaw - entity.rotation.y;
    const normalizedDiff = Math.atan2(Math.sin(diff), Math.cos(diff)); // Normalize to -PI to PI
    const maxRotation = turnSpeed * deltaTime;

    if (Math.abs(normalizedDiff) < maxRotation) {
      entity.rotation.y = targetYaw;
    } else {
      entity.rotation.y += Math.sign(normalizedDiff) * maxRotation;
    }
  }

  /**
   * Approach vector a towards b by maxDelta
   * Source-Engine style smooth acceleration
   */
  private approach(a: Vector3, b: Vector3, maxDelta: number): Vector3 {
    const diff = b.subtract(a);
    const distance = diff.length();

    if (distance <= maxDelta || distance < 0.001) {
      return b.clone();
    }

    return a.add(diff.scale(maxDelta / distance));
  }

  /**
   * Dispose resources
   */
  dispose(): void {
    this.coyoteTimeTracking.clear();
  }
}
