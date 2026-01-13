/**
 * CommandResultHandler - Handles command result messages from server
 *
 * Message type: "cmd.rs"
 * Direction: Server -> Client
 *
 * Handles final result of command execution (success or failure).
 * Routes results to CommandService for processing.
 */

import {
  ResponseMessage,
  CommandResultData,
  MessageType,
  getLogger,
  ExceptionHandler,
} from '@nimbus/shared';
import type { MessageHandler } from '../MessageHandler';
import type { CommandService } from '../../services/CommandService';

const logger = getLogger('CommandResultHandler');

/**
 * Handler for command result messages (cmd.rs)
 */
export class CommandResultHandler implements MessageHandler {
  readonly messageType = MessageType.CMD_RESULT;

  constructor(private commandService: CommandService) {
    logger.debug('CommandResultHandler created');
  }

  handle(message: ResponseMessage<CommandResultData>): void {
    try {
      if (!message.r) {
        logger.error('Received cmd.rs without request ID (r)');
        return;
      }

      if (!message.d) {
        logger.error('Received cmd.rs without data');
        return;
      }

      const result = message.d;

      logger.debug('Received command result', {
        requestId: message.r,
        rc: result.rc,
        message: result.message,
      });

      // Route to CommandService
      this.commandService.handleCommandResult(message.r, result);
    } catch (error) {
      ExceptionHandler.handle(error, 'CommandResultHandler.handle', { message });
    }
  }
}
