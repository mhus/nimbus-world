/**
 * Interaction-related messages
 */

import type { BaseMessage, RequestMessage, ResponseMessage } from '../BaseMessage';

/**
 * Interaction request data
 */
export interface InteractionRequestData {
  /** Block X position */
  x: number; // javaType: int

  /** Block Y position */
  y: number; // javaType: int

  /** Block Z position */
  z: number; // javaType: int

  /** Group ID of block (optional) */
  g?: string;
}

/**
 * Interaction response data (success)
 */
export interface InteractionSuccessData {
  success: true;
  // Additional response data can be added
}

/**
 * Interaction response data (failure)
 */
export interface InteractionErrorData {
  success: false;
  errorCode: number; // javaType: int
  errorMessage: string;
}

/**
 * Interaction request (Client -> Server)
 * Client sends interaction request (e.g., player interacts with block)
 */
export type InteractionRequestMessage = RequestMessage<InteractionRequestData>;

/**
 * Interaction response (Server -> Client)
 * Server responds to interaction request
 */
export type InteractionResponseMessage = ResponseMessage<
  InteractionSuccessData | InteractionErrorData
>;

/**
 * Simple interaction data (no block/entity target)
 */
export interface SimpleInteractionData {
  /** Action type (e.g., 'click') */
  ac: string;
  /** Shortcut key (e.g., 'key1', 'click1') */
  sc?: string;
  /** Additional data (optional) */
  d?: any;
}

/**
 * Simple interaction message (Client -> Server)
 * Sent when a shortcut is triggered without a block/entity target
 */
export type SimpleInteractionMessage = BaseMessage<SimpleInteractionData>;
