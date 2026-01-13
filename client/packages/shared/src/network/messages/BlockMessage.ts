/**
 * Block-related messages
 */

import type { BaseMessage } from '../BaseMessage';
import type { Block } from '../../types/Block';
import type { AnimationData } from '../../types/AnimationData';

/**
 * Block update (Server -> Client)
 * Server sends block changes to client
 */
export type BlockUpdateMessage = BaseMessage<Block[]>;

/**
 * Block status update data
 */
export interface BlockStatusUpdate {
  /** Block X position */
  x: number; // javaType: int

  /** Block Y position */
  y: number; // javaType: int

  /** Block Z position */
  z: number; // javaType: int

  /** New status value */
  s: number; // javaType: int

  /** Optional animations before status change */
  aa?: AnimationData[];

  /** Optional animations after status change */
  ab?: AnimationData[];
}

/**
 * Block status update (Server -> Client)
 * Server sends block status changes (e.g., for animations, effects)
 */
export type BlockStatusUpdateMessage = BaseMessage<BlockStatusUpdate[]>;

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
