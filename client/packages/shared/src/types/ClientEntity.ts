/**
 * ClientEntity - Client-side entity representation with cache and rendering data
 *
 * Used by EntityService to cache entities with their models and rendering state.
 */

import type { Entity, EntityModel, Waypoint } from './EntityData';
import type { Vector3 } from './Vector3';
import type { Rotation } from './Rotation';

/**
 * ClientEntity - Cached entity with rendering data
 *
 * Combines Entity + EntityModel with client-specific rendering state:
 * - Mesh references for rendering
 * - Current interpolated position/rotation
 * - Waypoint tracking
 * - Cache management (lastAccess timestamp)
 */
export interface ClientEntity {
  /** Entity ID */
  id: string;

  /** Entity model (resolved from entity.model ID) */
  model: EntityModel;

  /** Entity data */
  entity: Entity;

  /** Visibility flag (used for rendering) */
  visible: boolean;

  /** Babylon.js mesh references (managed by renderer) */
  meshes: any[]; // Mesh[] - using any to avoid Babylon.js dependency in shared

  /** Current interpolated position (world coordinates) */
  currentPosition: Vector3;

  /** Current interpolated rotation */
  currentRotation: Rotation;

  /** Current waypoint index in pathway */
  currentWaypointIndex: number; // javaType: int

  /** Current pose ID */
  currentPose: number; // javaType: int

  /** Current waypoints (from EntityPathway) */
  currentWaypoints: Waypoint[];

  /** Last access timestamp (for cache eviction) */
  lastAccess: number; // javaType: long

  /** Last step sound time for throttling (timestamp in ms) */
  lastStepTime?: number; // javaType: long
}

/**
 * Helper: Create a ClientEntity from Entity and EntityModel
 */
export function createClientEntity(
  entity: Entity,
  model: EntityModel,
  initialPosition?: Vector3,
  initialRotation?: Rotation
): ClientEntity {
  return {
    id: entity.id,
    model,
    entity,
    visible: true,
    meshes: [],
    currentPosition: initialPosition || { x: 0, y: 0, z: 0 },
    currentRotation: initialRotation || { y: 0, p: 0 },
    currentWaypointIndex: 0,
    currentPose: 0,
    currentWaypoints: [],
    lastAccess: Date.now(),
  };
}
