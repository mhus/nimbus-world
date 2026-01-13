/**
 * User/Player-related messages
 */

import type { BaseMessage } from '../BaseMessage';
import type { Rotation } from '../../types/Rotation';

/**
 * User movement update data
 */
export interface UserMovementData {
  /** Position (optional) */
  p?: {
    x: number;
    y: number;
    z: number;
  };

  /** Rotation (optional) */
  r?: Rotation;
}

/**
 * User movement update (Client -> Server)
 * Client sends current position and rotation to server
 */
export type UserMovementMessage = BaseMessage<UserMovementData>;

/**
 * Player teleport data
 */
export interface PlayerTeleportData {
  /** Target position */
  p: {
    x: number;
    y: number;
    z: number;
  };

  /** Target rotation */
  r: Rotation;
}

/**
 * Player teleport (Server -> Client)
 * Server sends teleport instruction to client
 */
export type PlayerTeleportMessage = BaseMessage<PlayerTeleportData>;
