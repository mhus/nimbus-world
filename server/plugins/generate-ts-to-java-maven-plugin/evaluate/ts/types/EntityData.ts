/**
 * Entity System - Dynamic objects in the game world
 *
 * Entities sind dynamische Objekte, die in der Spielwelt existieren.
 * Sie wechseln oft ihre Position, Ausrichtung oder ihren Zustand.
 * Beispiele: Spieler, NPCs, Fahrzeuge, bewegliche Gegenstände.
 */

import type { Vector3 } from './Vector3';
import type { Rotation } from './Rotation';
import type { AudioDefinition } from './BlockModifier';

/**
 * PoseType - Art der Bewegungsanimation
 */
export type PoseType =
  | '2-Legs'    // Zweibeinig (Menschen, Vögel)
  | '4-Legs'    // Vierbeinig (Hunde, Pferde)
  | '6-Legs'    // Sechsbeinig (Insekten)
  | 'Wings'     // Fliegend (Vögel, Drachen)
  | 'Fish'      // Schwimmend (Fische)
  | 'Snake'     // Kriechend (Schlangen)
  | 'Humanoid'  // Menschenähnlich
  | 'Slime';    // Gleitend/formlos

/**
 * PoseAnimation - Animation configuration for a pose
 */
export interface PoseAnimation {
  /** Animation name in the 3D model */
  animationName: string;

  /** Speed multiplier for this animation (1.0 = normal speed) */
  speedMultiplier: number;

  /** Should the animation loop? */
  loop: boolean;
}

/**
 * ENTITY_POSES - Standard pose IDs for entity animations
 */
export enum ENTITY_POSES {
  IDLE = 0,
  WALK = 1,
  RUN = 2,
  SPRINT = 3,
  CROUCH = 4,
  JUMP = 5,
  SWIM = 6,
  FLY = 7,
  DEATH = 8,
  WALK_SLOW = 9,
  CLAPPING = 10,
  ROLL = 11,
  ATTACK = 12,
  OUT_OF_WATER = 13,
  SWIMMING_FAST = 14,
  SWIMMING_IMPULSIVE = 15,
  SWIMMING = 16,
  HIT_RECEIVED = 17,
  HIT_RECEIVED_STRONG = 18,
  KICK_LEFT = 19,
  KICK_RIGHT = 20,
  PUNCH_LEFT = 21,
  PUNCH_RIGHT = 22,
  RUN_BACKWARD = 23,
  RUN_LEFT = 24,
  RUN_RIGHT = 25,
  WAVE = 26,
}

/**
 * Dimensions for different movement states
 * Collision box dimensions for each pose/movement type
 */
export interface EntityDimensions {
  walk?: { height: number; width: number; footprint: number };
  sprint?: { height: number; width: number; footprint: number };
  crouch?: { height: number; width: number; footprint: number };
  swim?: { height: number; width: number; footprint: number };
  climb?: { height: number; width: number; footprint: number };
  fly?: { height: number; width: number; footprint: number };
  teleport?: { height: number; width: number; footprint: number };
}

/**
 * Physics properties for entities
 */
export interface EntityPhysicsProperties {
  /** Mass of the entity (affects gravity and collisions) */
  mass: number;

  /** Ground friction coefficient (0 = no friction, 1 = high friction) */
  friction: number;

  /** Bounciness when colliding with surfaces (0 = no bounce, 1 = perfect bounce) */
  restitution?: number;

  /** Air drag coefficient (slows down movement over time) */
  drag?: number;
}

/**
 * EntityModifier - Modifiers for entity instances
 * Similar to BlockModifier but for entities
 */
export interface EntityModifier {
  /** Audio definitions for this entity (type is custom string, e.g., 'attack', 'idle', 'hurt') */
  audio?: AudioDefinition[];
}

/**
 * EntityModel - Template/Definition für Entity-Typen
 *
 * Analog zu BlockType: Definiert die Eigenschaften eines Entity-Typs.
 * Wird im Registry gespeichert und von Entity-Instanzen referenziert.
 */
export interface EntityModel {
  /** Unique identifier for this entity model */
  id: string;

  /** Type/category of entity */
  type: string;

  /** Path to 3D model file */
  modelPath: string;

  /** Position offset from entity position */
  positionOffset: Vector3;

  /** Rotation offset from entity rotation */
  rotationOffset: Vector3;

  /** Scale of the model */
  scale: Vector3;

  /** Maximum pitch rotation in degrees (0 = no vertical rotation, only Y-axis) */
  maxPitch?: number;

  /** Mapping of pose IDs to animation configuration */
  poseMapping: Map<ENTITY_POSES, PoseAnimation>;

  /** Type of movement/animation system */
  poseType: PoseType;

  /** Mapping of modifier keys to visual modifications (e.g., skin colors, equipment) */
  modelModifierMapping: Map<string, string>;

  /** Collision dimensions for different movement states */
  dimensions: EntityDimensions;

  /** Physics properties (optional, for entities with physics enabled) */
  physicsProperties?: EntityPhysicsProperties;

  /** Audio definitions for this entity model (default for all instances) */
  audio?: AudioDefinition[];
}

/**
 * MovementType - Defines how dynamic an entity's movement is
 */
export type MovementType =
  | 'static'   // Statisch (bewegt sich nicht)
  | 'passive'  // Passiv (langsame, vorhersagbare Bewegung)
  | 'slow'     // Langsam (moderate Bewegung)
  | 'dynamic'; // Dynamisch (schnelle, unvorhersagbare Bewegung)

/**
 * Entity - Konkrete Entity-Instanz in der Welt
 *
 * Analog zu BlockInstance: Eine konkrete Entity an einer bestimmten Position.
 * Referenziert ein EntityModel über die ID.
 *
 * Entities koennen sehr individuell sein, deshalb werden viele Eigenschaften
 * direkt in der Instanz definiert.
 */
export interface Entity {
  /** Unique identifier for this entity instance */
  id: string;

  /** Display name of the entity */
  name: string;

  /** Reference to EntityModel (by ID) - das Modell definiert nur die Darstellung und auch das visuelle Verhalten, z.b. beim Laufen */
  model: string; // EntityModel ID

  /** Custom modifiers for this instance (overrides/extends model defaults) */
  modelModifier: Record<string, any>;

  /** Entity modifiers (audio, etc.) */
  modifier?: EntityModifier;

  /** Movement behavior type */
  movementType: MovementType;

  /** Who controls this entity: 'player', 'server', 'ai', 'client' */
  controlledBy: string;

  /** Is this entity solid (blocking)? */
  solid?: boolean;

  /** Is this entity interactive (can be clicked/used)? */
  interactive?: boolean;

  /** Enable server-side physics simulation for this entity */
  physics?: boolean;

  /** Enable client-side physics simulation (independent of server physics) */
  clientPhysics?: boolean;

  /** Send collision event to server when player collides with this entity */
  notifyOnCollision?: boolean;

  /** Distance threshold for proximity notifications in blocks (0 = disabled) */
  notifyOnAttentionRange?: number;
}

/**
 * Waypoint - Single point in an entity's path
 */
export interface Waypoint {
  /** Target timestamp when entity should reach this waypoint */
  timestamp: number;

  /** Target position (world coordinates) */
  target: Vector3;

  /** Target rotation (direction, pitch) */
  rotation: Rotation;

  /** Pose/animation ID at this waypoint */
  pose: ENTITY_POSES;
}

/**
 * EntityPathway - Movement path for an entity
 *
 * Defines a sequence of waypoints that the entity follows.
 * Can be used for NPCs, moving platforms, or other scripted movements.
 */
export interface EntityPathway {
  /** Entity ID this pathway belongs to */
  entityId: string;

  /** Start timestamp for the pathway */
  startAt: number;

  /** Sequence of waypoints */
  waypoints: Waypoint[];

  /** Should the pathway loop? If true, recalculate waypoints with startAt */
  isLooping?: boolean;

  /** Timestamp for querying current position (interpolation) */
  queryAt?: number;

  /** Idle pose when not moving */
  idlePose?: ENTITY_POSES;

  /** Is this pathway generated by physics? (used for client prediction) */
  physicsEnabled?: boolean;

  /** Current velocity (for physics-based pathways) */
  velocity?: Vector3;

  /** Is entity on ground? (for physics-based pathways) */
  grounded?: boolean;
}

/**
 * Helper: Create a basic EntityModel
 */
export function createEntityModel(
  id: string,
  type: string,
  modelPath: string,
  poseType: PoseType,
  dimensions: EntityDimensions
): EntityModel {
  return {
    id,
    type,
    modelPath,
    positionOffset: { x: 0, y: 0, z: 0 },
    rotationOffset: { x: 0, y: 0, z: 0 },
    scale: { x: 1.0, y: 1.0, z: 1.0 },
    poseMapping: new Map(),
    poseType,
    modelModifierMapping: new Map(),
    dimensions,
  };
}

/**
 * Helper: Create a basic Entity instance
 */
export function createEntity(
  id: string,
  name: string,
  modelId: string,
  movementType: MovementType = 'static',
  controlledBy: string = 'server'
): Entity {
  return {
    id,
    name,
    model: modelId,
    modelModifier: {},
    movementType,
    controlledBy,
    solid: true,
    interactive: false,
  };
}

/**
 * Helper: Create a pathway for an entity
 */
export function createEntityPathway(
  entityId: string,
  startAt: number,
  waypoints: Waypoint[],
  isLooping: boolean = false
): EntityPathway {
  return {
    entityId,
    startAt,
    waypoints,
    isLooping,
  };
}

/**
 * Helper: Interpolate entity position at a given timestamp
 *
 * Calculates the current position/rotation/pose based on waypoints and timestamp.
 */
export function interpolateEntityPosition(
  pathway: EntityPathway,
  queryTimestamp: number
): { position: Vector3; rotation: Rotation; pose: number } | null {
  if (pathway.waypoints.length === 0) {
    return null;
  }

  // Find the two waypoints to interpolate between
  let prevWaypoint: Waypoint | null = null;
  let nextWaypoint: Waypoint | null = null;

  for (let i = 0; i < pathway.waypoints.length; i++) {
    const wp = pathway.waypoints[i];
    if (wp.timestamp <= queryTimestamp) {
      prevWaypoint = wp;
      nextWaypoint = pathway.waypoints[i + 1] || null;
    } else {
      break;
    }
  }

  // If before first waypoint
  if (!prevWaypoint) {
    const first = pathway.waypoints[0];
    return {
      position: first.target,
      rotation: first.rotation,
      pose: first.pose,
    };
  }

  // If after last waypoint
  if (!nextWaypoint) {
    return {
      position: prevWaypoint.target,
      rotation: prevWaypoint.rotation,
      pose: pathway.idlePose ?? prevWaypoint.pose,
    };
  }

  // Interpolate between waypoints
  const t = (queryTimestamp - prevWaypoint.timestamp) / (nextWaypoint.timestamp - prevWaypoint.timestamp);
  const clampedT = Math.max(0, Math.min(1, t));

  return {
    position: {
      x: prevWaypoint.target.x + (nextWaypoint.target.x - prevWaypoint.target.x) * clampedT,
      y: prevWaypoint.target.y + (nextWaypoint.target.y - prevWaypoint.target.y) * clampedT,
      z: prevWaypoint.target.z + (nextWaypoint.target.z - prevWaypoint.target.z) * clampedT,
    },
    rotation: {
      y: prevWaypoint.rotation.y + (nextWaypoint.rotation.y - prevWaypoint.rotation.y) * clampedT,
      p: prevWaypoint.rotation.p + (nextWaypoint.rotation.p - prevWaypoint.rotation.p) * clampedT,
    },
    pose: clampedT < 0.5 ? prevWaypoint.pose : nextWaypoint.pose,
  };
}
