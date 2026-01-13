/**
 * PhysicsEntity - Entity with physics simulation
 *
 * Core interface for all entities managed by the physics system.
 */

import { Vector3 } from '@babylonjs/core';
import type { PlayerBlockContext } from './BlockContext';

/**
 * Movement mode
 */
export type MovementMode = 'walk' | 'sprint' | 'crouch' | 'swim' | 'climb' | 'free_fly' | 'fly' | 'teleport';

/**
 * Climb animation state
 */
export interface ClimbState {
  active: boolean;
  startY: number;
  targetY: number;
  startX: number;
  targetX: number;
  startZ: number;
  targetZ: number;
  progress: number; // 0.0 to 1.0
}

/**
 * Entity with physics
 */
export interface PhysicsEntity {
  /** Entity position */
  position: Vector3;

  /** Entity velocity (physics state) */
  velocity: Vector3;

  /** Entity rotation (Euler angles in radians) */
  rotation: Vector3;

  /** Movement mode */
  movementMode: MovementMode;

  /** Movement intention (input vector before physics) */
  wishMove: Vector3;

  /** Is entity on ground? */
  grounded: boolean;

  /** Is entity on slope? */
  onSlope: boolean;

  /** Is entity in water? */
  inWater: boolean;

  /** Can auto-jump be triggered? */
  autoJump: number;

  /** Jump requested this frame (set by jump() method) */
  jumpRequested: boolean;

  /** Entity ID for logging */
  entityId: string;

  /** Last block position for change detection (floor of position) */
  lastBlockPos: Vector3;

  /** Cached block context (invalidated when block position changes) */
  cachedContext?: PlayerBlockContext;

  /** Auto-climb state (for smooth animation) */
  climbState?: ClimbState;

  /** Accumulated fall distance in blocks (for FALL state detection) */
  fallDistance: number;

  /** Was falling in previous frame (for landing detection) */
  wasFalling: boolean;

  // ============================================
  // Cached state-dependent values
  // Updated when movement state changes for performance
  // ============================================

  /** Current horizontal movement speed (varies by state: WALK=5.0, SPRINT=7.0, CROUCH=2.5) */
  effectiveSpeed: number;

  /** Current jump velocity (varies by state: WALK=8.0, CROUCH=4.0, RIDING=10.0) */
  effectiveJumpSpeed: number;

  /** Current turn/mouse sensitivity (varies by state) */
  effectiveTurnSpeed: number;

  /** Current eye height for camera positioning (varies by state: WALK=1.6, CROUCH=0.8) */
  cachedEyeHeight: number;

  /** Current selection/interaction radius (varies by state: WALK=5.0, CROUCH=4.0, FLY=8.0) */
  cachedSelectionRadius: number;

  /** Last step sound time for throttling (timestamp in ms) */
  lastStepTime?: number;
}
