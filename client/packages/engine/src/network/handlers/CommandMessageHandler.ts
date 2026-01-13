/**
 * CommandMessageHandler - Handles intermediate command messages from server
 *
 * Message type: "cmd.msg"
 * Direction: Server -> Client
 *
 * Handles intermediate progress/informational messages during command execution.
 * Routes messages to CommandService for processing.
 */

import {
  ResponseMessage,
  CommandMessageData,
  MessageType,
  getLogger,
  ExceptionHandler,
} from '@nimbus/shared';
import type { MessageHandler } from '../MessageHandler';
import type { CommandService } from '../../services/CommandService';

const logger = getLogger('CommandMessageHandler');

/**
 * Handler for command intermediate messages (cmd.msg)
 */
export class CommandMessageHandler implements MessageHandler {
  readonly messageType = MessageType.CMD_MESSAGE;

  constructor(private commandService: CommandService) {
    logger.debug('CommandMessageHandler created');
  }

  handle(message: ResponseMessage<CommandMessageData>): void {
    try {
      if (!message.r) {
        logger.error('Received cmd.msg without request ID (r)');
        return;
      }

      if (!message.d) {
        logger.error('Received cmd.msg without data');
        return;
      }

      const { message: messageText } = message.d;

      logger.debug('Received command message', {
        requestId: message.r,
        message: messageText,
      });

      // Route to CommandService
      this.commandService.handleCommandMessage(message.r, messageText);
    } catch (error) {
      ExceptionHandler.handle(error, 'CommandMessageHandler.handle', { message });
    }
  }
}
