/**
 * Targeting Types - Type-safe target resolution for visual effects
 *
 * Provides discriminated unions for compile-time safety when resolving
 * targets for visual effects and server interactions.
 */

import type { ClientEntity } from './ClientEntity';

/**
 * Targeting mode determines how targets are resolved
 *
 * - 'ENTITY': Only resolve entity targets
 * - 'BLOCK': Only resolve block targets
 * - 'BOTH': Resolve entity OR block (entity has priority)
 * - 'GROUND': Resolve ground position from camera ray
 * - 'ALL': Try entity, then block, then ground (fallback chain)
 */
export type TargetingMode = 'ENTITY' | 'BLOCK' | 'BOTH' | 'GROUND' | 'ALL';

/**
 * Resolved target - discriminated union for type-safe target handling
 *
 * Each variant includes the specific data needed for that target type.
 * Use TypeScript's type narrowing to safely access variant-specific fields.
 */
export type ResolvedTarget =
  | { type: 'entity'; entity: ClientEntity; position: ClientPosition }
  | { type: 'block'; block: ClientBlock; position: ClientPosition }
  | { type: 'ground'; position: ClientPosition }
  | { type: 'none' };

/**
 * Client-side block representation (minimal fields for targeting)
 */
export interface ClientBlock {
  block: {
    position: { x: number; y: number; z: number };
    metadata?: {
      id?: string;
      groupId?: string; // Deprecated?
      [key: string]: any;
    };
    [key: string]: any;
  };
  blockType: any; // BlockType (full definition in engine package)
  [key: string]: any; // Allow additional fields
}

/**
 * Position type compatible with BabylonJS Vector3
 */
export interface ClientPosition {
  x: number;
  y: number;
  z: number;
}

/**
 * Targeting context for network synchronization
 *
 * Sent via ef.p.u messages to remote clients so they can
 * properly reconstruct the targeting state.
 */
export interface TargetingContext {
  /**
   * Targeting mode used to resolve the target
   */
  mode: TargetingMode;

  /**
   * Resolved target data
   *
   * For network transmission, some fields may be omitted or simplified:
   * - Entity: Only id and position
   * - Block: Only block.position
   * - Ground: Only position
   */
  target: ResolvedTarget;
}

/**
 * Serializable targeting context for network transmission
 *
 * Simplified version with only essential data for ef.p.u messages
 */
export interface SerializableTargetingContext {
  mode: TargetingMode;
  targetType: 'entity' | 'block' | 'ground' | 'none';
  entityId?: string;
  blockPosition?: { x: number; y: number; z: number };
  position: { x: number; y: number; z: number };
}
