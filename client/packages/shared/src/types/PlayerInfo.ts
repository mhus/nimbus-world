/**
 * PlayerInfo - Player configuration and properties
 *
 * Contains all player-specific parameters that can change dynamically
 * during gameplay (e.g., through power-ups, status effects, equipment).
 *
 * **Base vs Effective Values:**
 * - Base values: Original unmodified values
 * - Effective values: Base + modifiers (power-ups, equipment, status effects)
 * - Services use effective values for actual gameplay
 *
 * Movement speeds are in blocks per second.
 */

import {ShortcutDefinition} from "./ShortcutDefinition";

/**
 * Movement state key - maps to both MovementMode and PlayerMovementState
 * 'default' is used as fallback when no specific movement state is known
 */
export type MovementStateKey = 'default' | 'walk' | 'sprint' | 'crouch' | 'swim' | 'climb' | 'free_fly' | 'fly' | 'teleport' | 'riding';

export interface MovementStateDomensions {
  height: number;    // Entity height in blocks (collision box)
  width: number;     // Entity width in blocks (collision box diameter)
  footprint: number; // Footprint radius in blocks (for corner sampling)
}

/**
 * Movement state configuration
 * Contains all values that vary by movement state
 *
 * This replaces the flat property structure with a unified matrix
 * that makes state-dependent values explicit and easier to manage.
 */
export interface MovementStateValues {
  // Physics dimensions
  dimensions: MovementStateDomensions;
  // Movement speeds (blocks/second)
  baseMoveSpeed: number;      // Base horizontal movement speed
  effectiveMoveSpeed: number; // Base + modifiers
  baseJumpSpeed: number;      // Jump/vertical speed
  effectiveJumpSpeed: number; // Base + modifiers

  // Camera & View
  eyeHeight: number;          // Eye position relative to feet (for camera)
  baseTurnSpeed: number;      // Degrees/second (mouse sensitivity)
  effectiveTurnSpeed: number; // Base + modifiers

  // Interaction
  selectionRadius: number;    // Block selection/interaction reach

  // Stealth & Detection
  stealthRange: number;              // How far entities can detect player
  distanceNotifyReduction: number;   // Stealth modifier (0.0 = normal, 1.0 = invisible)
}

/**
 * Player information and properties
 */
export interface PlayerInfo {
  /** Player unique identifier */
  playerId: string;

  /** Player title (shown to other players) */
  title: string;

  /**
   * Shortcuts map
   * Maps shortcut keys to action definitions
   * Keys: 'key0'-'key9', 'click0'-'click2', 'slot0'-'slotN'
   */
  shortcuts?: Record<string, ShortcutDefinition>; // javaType: Map<String,ShortcutDefinition>
  editorShortcuts?: Record<string, ShortcutDefinition>; // javaType: Map<String,ShortcutDefinition>

  // ============================================
  // State-Based Values (NEW UNIFIED STRUCTURE)
  // ============================================

  /**
   * State-based values per movement mode
   * Contains all properties that vary by movement state
   * (speeds, dimensions, eyeHeight, selectionRadius, etc.)
   */
  stateValues: Record<MovementStateKey, MovementStateValues>; // javaType: Map<String,MovementStateValues>

  // ============================================
  // Base Movement Speeds (blocks per second)
  // DEPRECATED: Use stateValues instead
  // Original unmodified values
  // ============================================

  /** Base normal walking speed */
  baseWalkSpeed: number;

  /** Base sprint/running speed */
  baseRunSpeed: number;

  /** Base swimming/underwater movement speed */
  baseUnderwaterSpeed: number;

  /** Base sneaking/crouching speed */
  baseCrawlSpeed: number;

  /** Base speed when riding a mount or vehicle */
  baseRidingSpeed: number;

  /** Base jump vertical velocity */
  baseJumpSpeed: number;

  // ============================================
  // Effective Movement Speeds (blocks per second)
  // Base + modifiers (power-ups, equipment, status effects)
  // These values are used by PhysicsService
  // ============================================

  /** Effective walking speed (base + modifiers) */
  effectiveWalkSpeed: number;

  /** Effective sprint/running speed (base + modifiers) */
  effectiveRunSpeed: number;

  /** Effective swimming/underwater speed (base + modifiers) */
  effectiveUnderwaterSpeed: number;

  /** Effective sneaking/crouching speed (base + modifiers) */
  effectiveCrawlSpeed: number;

  /** Effective riding speed (base + modifiers) */
  effectiveRidingSpeed: number;

  /** Effective jump vertical velocity (base + modifiers) */
  effectiveJumpSpeed: number;

  // ============================================
  // Player Dimensions
  // ============================================

  /** Player eye height in blocks (for camera position and raycast origin) */
  eyeHeight: number;

  // ============================================
  // Stealth & Detection
  // ============================================

  /** Detection range for mobs when sneaking (in blocks) */
  stealthRange: number;

  /** Distance reduction for entity attention range when walking (in blocks, default: 0) */
  distanceNotifyReductionWalk: number;

  /** Distance reduction for entity attention range when crouching (in blocks, default: 0) */
  distanceNotifyReductionCrouch: number;

  // ============================================
  // Selection & Interaction
  // ============================================

  /** Maximum selection/targeting range in blocks (for auto-select and shortcuts) */
  selectionRadius: number;

  // ============================================
  // Camera Control
  // ============================================

  /** Base mouse sensitivity for camera rotation (on land) */
  baseTurnSpeed: number;

  /** Effective mouse sensitivity (base + modifiers, e.g., dizzy effects) */
  effectiveTurnSpeed: number;

  /** Base mouse sensitivity for camera rotation (underwater) */
  baseUnderwaterTurnSpeed: number;

  /** Effective underwater mouse sensitivity (base + modifiers) */
  effectiveUnderwaterTurnSpeed: number;

  // ============================================
  // Third-Person View
  // ============================================

  /** Entity model ID for third-person view (optional, e.g., "farmer1") */
  thirdPersonModelId?: string;

  /** Modifiers for third-person model appearance (e.g., colors, accessories) */
  thirdPersonModelModifiers?: Map<string, string>;

}

/**
 * Default state values for all movement states
 * Used as fallback when playerInfo.stateValues is not populated
 */
export const DEFAULT_STATE_VALUES: Record<MovementStateKey, MovementStateValues> = {
  // Default fallback values (same as walk)
  default: {
    dimensions: { height: 2.0, width: 0.6, footprint: 0.3 },
    baseMoveSpeed: 5.0,
    effectiveMoveSpeed: 5.0,
    baseJumpSpeed: 8.0,
    effectiveJumpSpeed: 8.0,
    eyeHeight: 1.6,
    baseTurnSpeed: 0.003,    // radians per pixel
    effectiveTurnSpeed: 0.003,
    selectionRadius: 5.0,
    stealthRange: 8.0,
    distanceNotifyReduction: 0.0,
  },

  walk: {
    dimensions: { height: 2.0, width: 0.6, footprint: 0.3 },
    baseMoveSpeed: 5.0,
    effectiveMoveSpeed: 5.0,
    baseJumpSpeed: 8.0,
    effectiveJumpSpeed: 8.0,
    eyeHeight: 1.6,
    baseTurnSpeed: 0.003,    // radians per pixel
    effectiveTurnSpeed: 0.003,
    selectionRadius: 5.0,
    stealthRange: 8.0,
    distanceNotifyReduction: 0.0,
  },

  sprint: {
    dimensions: { height: 2.0, width: 0.6, footprint: 0.3 },
    baseMoveSpeed: 7.0,
    effectiveMoveSpeed: 7.0,
    baseJumpSpeed: 8.0,
    effectiveJumpSpeed: 8.0,
    eyeHeight: 1.6,
    baseTurnSpeed: 0.003,
    effectiveTurnSpeed: 0.003,
    selectionRadius: 5.0,
    stealthRange: 12.0,
    distanceNotifyReduction: 0.0,
  },

  crouch: {
    dimensions: { height: 1.0, width: 0.6, footprint: 0.3 },
    baseMoveSpeed: 1.5,  // Very slow for stealth
    effectiveMoveSpeed: 1.5,
    baseJumpSpeed: 4.0,
    effectiveJumpSpeed: 4.0,
    eyeHeight: 0.8,
    baseTurnSpeed: 0.002,
    effectiveTurnSpeed: 0.002,
    selectionRadius: 4.0,
    stealthRange: 4.0,
    distanceNotifyReduction: 0.5,
  },

  swim: {
    dimensions: { height: 1.8, width: 0.6, footprint: 0.3 },
    baseMoveSpeed: 3.0,
    effectiveMoveSpeed: 3.0,
    baseJumpSpeed: 4.0,
    effectiveJumpSpeed: 4.0,
    eyeHeight: 1.4,
    baseTurnSpeed: 0.002,
    effectiveTurnSpeed: 0.002,
    selectionRadius: 4.0,
    stealthRange: 6.0,
    distanceNotifyReduction: 0.3,
  },

  climb: {
    dimensions: { height: 1.8, width: 0.6, footprint: 0.3 },
    baseMoveSpeed: 2.5,
    effectiveMoveSpeed: 2.5,
    baseJumpSpeed: 0.0,
    effectiveJumpSpeed: 0.0,
    eyeHeight: 1.5,
    baseTurnSpeed: 0.002,
    effectiveTurnSpeed: 0.002,
    selectionRadius: 4.0,
    stealthRange: 6.0,
    distanceNotifyReduction: 0.2,
  },

  free_fly: {
    dimensions: { height: 1.8, width: 0.6, footprint: 0.3 },
    baseMoveSpeed: 10.0,
    effectiveMoveSpeed: 10.0,
    baseJumpSpeed: 0.0,
    effectiveJumpSpeed: 0.0,
    eyeHeight: 1.6,
    baseTurnSpeed: 0.004,
    effectiveTurnSpeed: 0.004,
    selectionRadius: 8.0,
    stealthRange: 15.0,
    distanceNotifyReduction: 0.0,
  },

  fly: {
    dimensions: { height: 1.8, width: 0.6, footprint: 0.3 },
    baseMoveSpeed: 10.0,
    effectiveMoveSpeed: 10.0,
    baseJumpSpeed: 0.0,
    effectiveJumpSpeed: 0.0,
    eyeHeight: 1.6,
    baseTurnSpeed: 0.004,
    effectiveTurnSpeed: 0.004,
    selectionRadius: 8.0,
    stealthRange: 15.0,
    distanceNotifyReduction: 0.0,
  },

  teleport: {
    dimensions: { height: 1.8, width: 0.6, footprint: 0.3 },
    baseMoveSpeed: 20.0,
    effectiveMoveSpeed: 20.0,
    baseJumpSpeed: 0.0,
    effectiveJumpSpeed: 0.0,
    eyeHeight: 1.6,
    baseTurnSpeed: 0.005,
    effectiveTurnSpeed: 0.005,
    selectionRadius: 10.0,
    stealthRange: 20.0,
    distanceNotifyReduction: 0.0,
  },

  riding: {
    dimensions: { height: 2.5, width: 1.0, footprint: 0.5 },
    baseMoveSpeed: 8.0,
    effectiveMoveSpeed: 8.0,
    baseJumpSpeed: 10.0,
    effectiveJumpSpeed: 10.0,
    eyeHeight: 2.0,
    baseTurnSpeed: 0.003,
    effectiveTurnSpeed: 0.003,
    selectionRadius: 6.0,
    stealthRange: 10.0,
    distanceNotifyReduction: 0.0,
  },
};

/**
 * Get state values for a movement state with fallback
 * @param playerInfo Player information
 * @param stateKey Movement state key
 * @returns State values (falls back to default if not found)
 */
export function getStateValues(
  playerInfo: PlayerInfo,
  stateKey: MovementStateKey
): MovementStateValues {
  return playerInfo.stateValues?.[stateKey] || playerInfo.stateValues?.default || DEFAULT_STATE_VALUES.default;
}

/**
 * Map PlayerMovementState to MovementStateKey
 * @param state Player movement state
 * @returns Movement state key for stateValues lookup
 */
export function movementStateToKey(state: string): MovementStateKey {
  // PlayerMovementState enum values map directly to MovementStateKey
  // JUMP and FALL use walk values
  switch (state) {
    case 'WALK': return 'walk';
    case 'SPRINT': return 'sprint';
    case 'CROUCH': return 'crouch';
    case 'SWIM': return 'swim';
    case 'FREE_FLY': return 'free_fly';
    case 'FLY': return 'fly';
    case 'RIDING': return 'riding';
    case 'JUMP': return 'walk';  // Jump uses walk values
    case 'FALL': return 'walk';  // Fall uses walk values
    default: return 'default';
  }
}

/**
 * Map MovementMode to MovementStateKey
 * @param mode Movement mode from PhysicsEntity
 * @returns Movement state key for stateValues lookup
 */
export function movementModeToKey(mode: string): MovementStateKey {
  // MovementMode maps directly to MovementStateKey
  switch (mode) {
    case 'walk': return 'walk';
    case 'sprint': return 'sprint';
    case 'crouch': return 'crouch';
    case 'swim': return 'swim';
    case 'climb': return 'climb';
    case 'free_fly': return 'free_fly';
    case 'fly': return 'fly';
    case 'teleport': return 'teleport';
    default: return 'default';
  }
}
