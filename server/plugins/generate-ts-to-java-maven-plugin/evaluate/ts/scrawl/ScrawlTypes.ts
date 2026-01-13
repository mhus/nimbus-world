import { Vector3 } from '../types/Vector3';

/**
 * Subject in a scrawl execution context.
 * Can represent either an entity or a block position.
 */
export interface ScrawlSubject {
  /** World position of the subject */
  position: Vector3;

  /** Entity ID (if subject is an entity) */
  entityId?: string;

  /** Block ID (if subject is a block) */
  blockId?: string;
}

/**
 * Execution context for a scrawl script.
 * Contains the actor (source) and patients (targets).
 */
export interface ScrawlExecContext {
  /** The actor performing the action (source) */
  actor?: ScrawlSubject;

  /** The patients receiving the action (targets) */
  patients?: ScrawlSubject[];

  /** Variables available in the context */
  vars?: Record<string, any>;

  /** Custom context data passed to effects */
  [key: string]: any;
}

/**
 * Parameter type definitions for scrawl scripts
 */
export type ScrawlParameterType = 'number' | 'string' | 'boolean' | 'color' | 'vector3';

/**
 * LOD (Level of Detail) levels for effects
 */
export type ScrawlLodLevel = 'high' | 'medium' | 'low';
