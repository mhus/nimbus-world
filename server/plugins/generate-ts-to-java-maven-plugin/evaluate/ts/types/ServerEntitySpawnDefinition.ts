/**
 * ServerEntitySpawnDefinition - Server-side entity spawn configuration
 *
 * Defines how entities are spawned and simulated on the server.
 * Stored in data/worlds/{worldId}/entities/ as JSON files.
 */

import type { Vector3 } from './Vector3';
import type { Rotation } from './Rotation';
import type { EntityPathway } from './EntityData';

/**
 * Vector2 for chunk coordinates
 */
export interface Vector2 {
  x: number;
  z: number;
}

/**
 * Behavior configuration for entity movement
 */
export interface BehaviorConfig {
  /** Minimum step distance per waypoint (blocks) */
  minStepDistance?: number;

  /** Maximum step distance per waypoint (blocks) */
  maxStepDistance?: number;

  /** Number of waypoints per pathway */
  waypointsPerPath?: number;

  /** Minimum idle duration (milliseconds) */
  minIdleDuration?: number;

  /** Maximum idle duration (milliseconds) */
  maxIdleDuration?: number;

  /** Interval between pathway generation (milliseconds) */
  pathwayInterval?: number;
}

/**
 * ServerEntitySpawnDefinition - Configuration for spawning and simulating entities
 */
export interface ServerEntitySpawnDefinition {
  /** Unique entity ID */
  entityId: string;

  /** Entity model ID reference */
  entityModelId: string;

  /** Initial spawn position (world coordinates) */
  initialPosition: Vector3;

  /** Initial spawn rotation */
  initialRotation: Rotation;

  /** Middle point for entity movement (center of roaming area) */
  middlePoint: Vector3;

  /** Movement radius around middle point */
  radius: number;

  /** Movement speed (units per second) */
  speed: number;

  /** Behavior model reference (algorithm for simulation) */
  behaviorModel: string;

  /** Behavior configuration (optional, behavior-specific settings) */
  behaviorConfig?: BehaviorConfig;

  /** Current pathway (calculated and updated by simulator) */
  currentPathway?: EntityPathway;

  /** List of chunk positions where this entity is active in the pathway */
  chunks: Vector2[];

  /** Physics state (for entities with physics enabled) */
  physicsState?: {
    /** Current position (world coordinates) */
    position: Vector3;

    /** Current velocity (blocks per second) */
    velocity: Vector3;

    /** Current rotation */
    rotation: Rotation;

    /** Is entity on ground? */
    grounded: boolean;
  };
}

/**
 * Helper: Create default spawn definition
 */
export function createServerEntitySpawnDefinition(
  entityId: string,
  entityModelId: string,
  position: Vector3,
  behaviorModel: string = 'PreyAnimalBehavior'
): ServerEntitySpawnDefinition {
  return {
    entityId,
    entityModelId,
    initialPosition: position,
    initialRotation: { y: 0, p: 0 },
    middlePoint: position,
    radius: 10,
    speed: 2,
    behaviorModel,
    chunks: [],
  };
}

/**
 * Helper: Calculate affected chunks from pathway waypoints
 */
export function calculateAffectedChunks(
  pathway: EntityPathway,
  chunkSize: number = 16
): Vector2[] {
  const chunkSet = new Set<string>();

  for (const waypoint of pathway.waypoints) {
    const chunkX = Math.floor(waypoint.target.x / chunkSize);
    const chunkZ = Math.floor(waypoint.target.z / chunkSize);
    chunkSet.add(`${chunkX},${chunkZ}`);
  }

  return Array.from(chunkSet).map(key => {
    const [x, z] = key.split(',').map(Number);
    return { x, z };
  });
}
