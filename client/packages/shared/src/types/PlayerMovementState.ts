/**
 * PlayerMovementState - Physical movement state of the player
 *
 * Managed by StackModifier system with priority-based state resolution.
 * Different from animation poses - this represents the physical movement capability/mode.
 *
 * State Priority System (lower value = higher priority):
 * - 10: JUMP (PhysicsService - jump event)
 * - 20: FALL (PhysicsService - falling detection)
 * - 30: SWIM (PhysicsService - water detection)
 * - 100: Player input states (FLY, SPRINT, CROUCH)
 * - MAX: WALK (default state)
 *
 * Relationship to Poses:
 * MovementState + Velocity + Hysteresis → Pose (animation)
 * Example: WALK state + velocity > 0.2 → WALK pose
 *          WALK state + velocity < 0.05 + 500ms delay → IDLE pose
 */

/**
 * Player movement states
 * Only one state can be active at a time (determined by StackModifier priority)
 */
export enum PlayerMovementState {
  /** Normal walking - default state */
  WALK = 'WALK',

  /** Fast running/sprinting - player toggle */
  SPRINT = 'SPRINT',

  /** Jumping - set by PhysicsService on jump event */
  JUMP = 'JUMP',

  /** Falling - set by PhysicsService when airborne with negative y-velocity */
  FALL = 'FALL',

  /** Free flying - player toggle (editor only, no physics) */
  FREE_FLY = 'FREE_FLY',

  /** Flying with physics - player toggle (physics enabled, no gravity) */
  FLY = 'FLY',

  /** Swimming - set by PhysicsService when in water */
  SWIM = 'SWIM',

  /** Crouching - player toggle */
  CROUCH = 'CROUCH',

  /** Riding a mount/vehicle - set by mount system */
  RIDING = 'RIDING',
}

/**
 * Event data when player movement state changes
 */
export interface PlayerMovementStateChangedEvent {
  /** Player entity ID */
  playerId: string;

  /** Previous movement state */
  oldState: PlayerMovementState;

  /** New movement state */
  newState: PlayerMovementState;
}
