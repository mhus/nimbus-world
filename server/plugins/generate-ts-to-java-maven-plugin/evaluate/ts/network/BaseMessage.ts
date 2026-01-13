/**
 * Base message structure for all network messages
 */

import type { MessageType } from './MessageTypes';

/**
 * Base message interface
 * All network messages follow this structure
 */
export interface BaseMessage<T = any> {
  /**
   * Message ID (for request/response correlation)
   * Optional for server-initiated messages
   */
  i?: string;

  /**
   * Response ID (references request message ID)
   * Used in response messages
   */
  r?: string;

  /**
   * Message type
   */
  t: MessageType;

  /**
   * Message data payload
   */
  d?: T;
}

/**
 * Request message (has message ID)
 */
export interface RequestMessage<T = any> extends BaseMessage<T> {
  i: string;
}

/**
 * Response message (has response ID referencing request)
 */
export interface ResponseMessage<T = any> extends BaseMessage<T> {
  r: string;
}
