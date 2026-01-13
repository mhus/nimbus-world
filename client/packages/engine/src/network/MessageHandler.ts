/**
 * MessageHandler - Base class for handling network messages
 *
 * Each concrete handler handles a specific message type and
 * processes incoming messages from the server.
 */

import type { BaseMessage, MessageType } from '@nimbus/shared';

/**
 * Abstract base class for message handlers
 *
 * Implementations must specify which message type they handle
 * and provide the handling logic.
 */
export abstract class MessageHandler<T = any> {
  /**
   * The message type this handler processes
   */
  abstract readonly messageType: MessageType;

  /**
   * Handle an incoming message
   *
   * @param message - The message to handle
   */
  abstract handle(message: BaseMessage<T>): void | Promise<void>;
}
