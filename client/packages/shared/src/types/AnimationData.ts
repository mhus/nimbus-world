/**
 * AnimationData - Timeline-based animation system
 *
 * Animations are sequences of effects that can execute in parallel or sequentially.
 * Effects can be position-based, with support for multiple positions (e.g., projectile from A to B).
 *
 * **Use cases:**
 * 1. Server-defined: Server sends complete animation with fixed positions
 * 2. Client-defined: Client has animation template, fills in positions, sends back to server
 *    for broadcast to other players
 *
 * **Example:** Arrow shot from player to NPC:
 * - Effect 1: Projectile flies from A to B (parallel)
 * - Effect 2: Sky darkens (parallel with projectile)
 * - Effect 3: Explosion at B (sequential, after projectile)
 * - Effect 4: Sky brightens (sequential, after explosion)
 */

import type { Vector3 } from './Vector3';

/**
 * Animation effect types
 */
export enum AnimationEffectType {
  // Transform effects
  SCALE = 'scale',
  ROTATE = 'rotate',
  TRANSLATE = 'translate',

  // Visual effects
  COLOR_CHANGE = 'colorChange',
  FADE = 'fade',
  FLASH = 'flash',

  // Particle/Object effects
  PROJECTILE = 'projectile',
  EXPLOSION = 'explosion',
  PARTICLES = 'particles',
  SPAWN_ENTITY = 'spawnEntity',

  // Environment effects
  SKY_CHANGE = 'skyChange',
  LIGHT_CHANGE = 'lightChange',
  CAMERA_SHAKE = 'cameraShake',

  // Sound effects
  PLAY_SOUND = 'playSound',

  // Block effects
  BLOCK_BREAK = 'blockBreak',
  BLOCK_PLACE = 'blockPlace',
  BLOCK_CHANGE = 'blockChange',
}

/**
 * Easing function types
 */
export enum EasingType {
  LINEAR = 'linear',
  EASE_IN = 'easeIn',
  EASE_OUT = 'easeOut',
  EASE_IN_OUT = 'easeInOut',
  ELASTIC = 'elastic',
  BOUNCE = 'bounce',
  STEP = 'step',
}

/**
 * Position reference in animation
 * Can be a fixed position or a named placeholder to be filled in later
 */
export type PositionRef =
  | { type: 'fixed'; position: Vector3 }
  | { type: 'placeholder'; name: string }; // e.g., "player", "target", "impact"

/**
 * Animation effect definition
 */
export interface AnimationEffect {
  /** Unique effect ID within animation (optional) */
  id?: string;

  /** Effect type */
  type: AnimationEffectType;

  /** Position references (can be multiple for effects like projectile A→B) */
  positions?: PositionRef[];

  /** Effect parameters (type-specific) */
  params: {
    // Common parameters
    from?: any;
    to?: any;
    easing?: EasingType;

    // Projectile-specific
    speed?: number;
    projectileModel?: string;
    trajectory?: 'linear' | 'arc' | 'homing';

    // Explosion-specific
    radius?: number;
    explosionIntensity?: number;

    // Sky/Light-specific
    color?: string;
    lightIntensity?: number;

    // Sound-specific
    soundPath?: string;
    volume?: number;

    // Generic extensible params
    [key: string]: any;
  };

  /** Start time relative to animation timeline (ms) */
  startTime: number; // javaType: long

  /** Duration of effect (ms), or end time if endTime is not set */
  duration?: number; // javaType: int

  /** End time relative to animation timeline (ms) (alternative to duration) */
  endTime?: number; // javaType: long

  /** Wait for this effect to complete before starting next sequential effect */
  blocking?: boolean;
}

/**
 * Animation definition
 */
export interface AnimationData {
  /** Animation ID (unique for templates) */
  id?: string;

  /** Animation name/type */
  name: string;

  /** Total duration in milliseconds (calculated from effects if not set) */
  duration?: number; // javaType: int

  /** Timeline of effects */
  effects: AnimationEffect[];

  /** Position placeholders that need to be filled */
  placeholders?: string[];

  /** Whether animation loops */
  loop?: boolean;

  /** Number of times to repeat (if not looping) */
  repeat?: number; // javaType: int

  /** Animation source (who created/filled this animation) */
  source?: {
    type: 'server' | 'client';
    playerId?: string;
  };
}

/**
 * Animation template (predefined animation with placeholders)
 */
export interface AnimationTemplate extends AnimationData {
  /** Template ID */
  id: string;

  /** Required placeholders that must be filled */
  placeholders: string[];

  /** Description for debugging/editor */
  description?: string;
}

/**
 * Animation instance (template with filled positions)
 */
export interface AnimationInstance {
  /** Template ID this instance is based on */
  templateId: string;

  /** Filled animation data */
  animation: AnimationData;

  /** Timestamp when created */
  createdAt: number; // javaType: long

  /** Who triggered this animation */
  triggeredBy?: string; // Player ID
}

/**
 * Animation helper functions
 */
export namespace AnimationHelper {
  /**
   * Create fixed position reference
   * @param position World position
   * @returns Fixed position reference
   */
  export function fixedPosition(position: Vector3): PositionRef {
    return { type: 'fixed', position };
  }

  /**
   * Create placeholder position reference
   * @param name Placeholder name
   * @returns Placeholder position reference
   */
  export function placeholder(name: string): PositionRef {
    return { type: 'placeholder', name };
  }

  /**
   * Fill placeholders in animation with actual positions
   * @param template Animation template
   * @param positions Map of placeholder names to positions
   * @returns Animation with filled positions
   */
  export function fillPlaceholders(
    template: AnimationData,
    positions: Record<string, Vector3>
  ): AnimationData {
    const filledEffects = template.effects.map((effect) => {
      if (!effect.positions) {
        return effect;
      }

      const filledPositions = effect.positions.map((posRef) => {
        if (posRef.type === 'fixed') {
          return posRef;
        }

        const position = positions[posRef.name];
        if (!position) {
          throw new Error(
            `Missing position for placeholder: ${posRef.name}`
          );
        }

        return fixedPosition(position);
      });

      return {
        ...effect,
        positions: filledPositions,
      };
    });

    return {
      ...template,
      effects: filledEffects,
      placeholders: undefined, // Remove placeholders after filling
      source: {
        type: 'client' as const,
      },
    };
  }

  /**
   * Get all placeholder names from animation
   * @param animation Animation data
   * @returns Array of placeholder names
   */
  export function getPlaceholders(animation: AnimationData): string[] {
    const placeholders = new Set<string>();

    animation.effects.forEach((effect) => {
      effect.positions?.forEach((posRef) => {
        if (posRef.type === 'placeholder') {
          placeholders.add(posRef.name);
        }
      });
    });

    return Array.from(placeholders);
  }

  /**
   * Check if animation has unfilled placeholders
   * @param animation Animation data
   * @returns True if has placeholders
   */
  export function hasPlaceholders(animation: AnimationData): boolean {
    return getPlaceholders(animation).length > 0;
  }

  /**
   * Calculate total animation duration from effects
   * @param animation Animation data
   * @returns Duration in milliseconds
   */
  export function calculateDuration(animation: AnimationData): number {
    if (animation.duration) {
      return animation.duration;
    }

    let maxEndTime = 0;
    animation.effects.forEach((effect) => {
      const endTime =
        effect.endTime ?? (effect.startTime + (effect.duration ?? 0));
      maxEndTime = Math.max(maxEndTime, endTime);
    });

    return maxEndTime;
  }

  /**
   * Get effects active at specific time
   * @param animation Animation data
   * @param time Time in milliseconds
   * @returns Array of active effects
   */
  export function getActiveEffects(
    animation: AnimationData,
    time: number
  ): AnimationEffect[] {
    return animation.effects.filter((effect) => {
      const endTime =
        effect.endTime ?? (effect.startTime + (effect.duration ?? 0));
      return time >= effect.startTime && time <= endTime;
    });
  }

  /**
   * Create animation instance from template
   * @param template Animation template
   * @param positions Position values
   * @param triggeredBy Player ID who triggered
   * @returns Animation instance
   */
  export function createInstance(
    template: AnimationTemplate,
    positions: Record<string, Vector3>,
    triggeredBy?: string
  ): AnimationInstance {
    const animation = fillPlaceholders(template, positions);

    return {
      templateId: template.id,
      animation: {
        ...animation,
        source: {
          type: 'client',
          playerId: triggeredBy,
        },
      },
      createdAt: Date.now(),
      triggeredBy,
    };
  }

  /**
   * Validate animation structure
   * @param animation Animation data
   * @returns Validation result
   */
  export function validate(
    animation: AnimationData
  ): { valid: boolean; errors: string[] } {
    const errors: string[] = [];

    if (!animation.name) {
      errors.push('Animation name is required');
    }

    if (!animation.effects || animation.effects.length === 0) {
      errors.push('Animation must have at least one effect');
    }

    animation.effects.forEach((effect, index) => {
      if (effect.startTime < 0) {
        errors.push(`Effect ${index}: startTime cannot be negative`);
      }

      if (effect.endTime && effect.endTime < effect.startTime) {
        errors.push(`Effect ${index}: endTime cannot be before startTime`);
      }

      if (effect.duration && effect.duration < 0) {
        errors.push(`Effect ${index}: duration cannot be negative`);
      }
    });

    // Check for unfilled placeholders
    const placeholders = getPlaceholders(animation);
    if (placeholders.length > 0 && animation.source?.type !== 'server') {
      errors.push(`Unfilled placeholders: ${placeholders.join(', ')}`);
    }

    return {
      valid: errors.length === 0,
      errors,
    };
  }

  /**
   * Clone animation data
   * @param animation Animation to clone
   * @returns Cloned animation
   */
  export function clone(animation: AnimationData): AnimationData {
    return JSON.parse(JSON.stringify(animation));
  }

  /**
   * Merge multiple animations into timeline
   * @param animations Array of animations to merge
   * @param offsetTimes Optional offset for each animation
   * @returns Merged animation
   */
  export function merge(
    animations: AnimationData[],
    offsetTimes?: number[]
  ): AnimationData {
    const mergedEffects: AnimationEffect[] = [];
    let maxDuration = 0;

    animations.forEach((anim, index) => {
      const offset = offsetTimes?.[index] ?? 0;

      anim.effects.forEach((effect) => {
        const offsetEffect = {
          ...effect,
          startTime: effect.startTime + offset,
          endTime: effect.endTime ? effect.endTime + offset : undefined,
        };

        mergedEffects.push(offsetEffect);

        const endTime =
          offsetEffect.endTime ??
          (offsetEffect.startTime + (offsetEffect.duration ?? 0));
        maxDuration = Math.max(maxDuration, endTime);
      });
    });

    return {
      name: 'merged_animation',
      duration: maxDuration,
      effects: mergedEffects,
    };
  }
}
