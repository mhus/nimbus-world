/**
 * Entity-related messages
 */

import type { BaseMessage } from '../BaseMessage';
import type { EntityPathway } from '../../types/EntityData';
import type { Vector3 } from '../../types/Vector3';
import type { Rotation } from '../../types/Rotation';

/**
 * Entity Chunk Pathway (Server -> Client)
 * Server sends entity pathway data based on currently registered chunks
 * Message type: "e.p"
 */
export type EntityChunkPathwayMessage = BaseMessage<EntityPathway[]>;

/**
 * Entity Position Update Data (Client -> Server)
 * Client sends current entity position and rotation to server
 */
export interface EntityPositionUpdateData {
  /** Local entity ID (not unique ID, only local for client, can also be another simulated entity) */
  pl: string;

  /** Position (optional) */
  p?: Vector3;

  /** Rotation: yaw, pitch (optional) */
  r?: Rotation;

  /** Velocity (optional) */
  v?: Vector3;

  /** Pose ID (optional) */
  po?: number;

  /** Server timestamp */
  ts: number;

  /** Target arrival (optional) - interpolated target position
   * Client already calculates for server where player will move in next 200ms
   * Takes lag from server, update is every 100ms
   */
  ta?: {
    x: number;
    y: number;
    z: number;
    /** Target arrival timestamp */
    ts: number;
  };
}

/**
 * Entity Position Update (Client -> Server)
 * Message type: "e.p.u"
 */
export type EntityPositionUpdateMessage = BaseMessage<EntityPositionUpdateData[]>;

/**
 * Entity Interaction Data (Client -> Server)
 * Client sends interaction information with an entity to server
 */
export interface EntityInteractionData {
  /** Entity ID to interact with */
  entityId: string;

  /** Timestamp of interaction */
  ts: number;

  /** Action (e.g., 'use', 'talk', 'attack', 'touch') */
  ac: string;

  /** Optional parameters for interaction */
  pa?: Record<string, any>;
}

/**
 * Entity Interaction Request (Client -> Server)
 * Message type: "e.int.r"
 *
 * Example: Player interacts with an entity
 */
export type EntityInteractionMessage = BaseMessage<EntityInteractionData>;
