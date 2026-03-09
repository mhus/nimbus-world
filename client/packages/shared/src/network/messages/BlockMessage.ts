/**
 * Block-related messages
 */

import type { BaseMessage } from '../BaseMessage';
import type { Block } from '../../types/Block';
/**
 * Block update (Server -> Client)
 * Server sends block changes to client
 */
export type BlockUpdateMessage = BaseMessage<Block[]>;

/**
 * Block progress status update (Server -> Client).
 * Notifies clients about block status changes (e.g. door open/closed).
 * Key: world coordinates "x,y,z", Value: status string or null for removal.
 */
export interface BlockProgressStatusData {
  /** Chunk X coordinate */
  cx: number; // javaType: int

  /** Chunk Z coordinate */
  cz: number; // javaType: int

  /** Block status map: "x,y,z" -> status (null = removed) */
  s: Record<string, string | null>; // javaType: java.util.Map<String,String>
}

/**
 * Block progress status update (Server -> Client)
 * Server sends block status changes from world progress data.
 */
export type BlockProgressStatusMessage = BaseMessage<BlockProgressStatusData>;

/**
 * Block interaction data (Client -> Server)
 */
export interface BlockInteractionData {
  /** Block X position */
  x: number; // javaType: int

  /** Block Y position */
  y: number; // javaType: int

  /** Block Z position */
  z: number; // javaType: int

  /** Block ID from metadata (optional) */
  id?: string;

  /** Block group ID (optional) */
  gId?: string;

  /** Action type */
  ac: string;

  /** Action parameters */
  pa?: Record<string, any>;
}

/**
 * Block interaction (Client -> Server)
 * Client sends interaction with a block (e.g., click, use)
 */
export type BlockInteractionMessage = BaseMessage<BlockInteractionData>;
