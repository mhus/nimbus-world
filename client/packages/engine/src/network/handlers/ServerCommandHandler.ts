/**
 * ServerCommandHandler - Handles server command requests
 *
 * Message type: "scmd"
 * Direction: Server -> Client
 *
 * Handles server commands (scmd) by executing them via CommandService
 * and sending back the result (scmd.rs).
 */

import {
  ServerCommandMessage,
  SingleServerCommandData,
  MessageType,
  getLogger,
  ExceptionHandler,
} from '@nimbus/shared';
import type { MessageHandler } from '../MessageHandler';
import type { CommandService } from '../../services/CommandService';

const logger = getLogger('ServerCommandHandler');

/**
 * Handler for server command messages (scmd)
 */
export class ServerCommandHandler implements MessageHandler {
  readonly messageType = MessageType.SCMD;

  constructor(private commandService: CommandService) {
    logger.debug('ServerCommandHandler created');
  }

  handle(message: ServerCommandMessage): void {
    try {
      if (!message.d) {
        logger.error('Received scmd without data');
        return;
      }

      const { cmd, args, oneway, cmds, parallel } = message.d;

      // Check if this is a multiple commands message
      if (cmds && Array.isArray(cmds)) {
        // Multiple commands mode
        logger.debug('Received multiple server commands', {
          count: cmds.length,
          parallel: parallel || false,
        });

        // Route to CommandService for batch execution
        this.commandService.handleMultipleServerCommands(cmds, parallel || false);
        return;
      }

      // Single command mode (backward compatible)
      if (!cmd) {
        logger.error('Received scmd without cmd field');
        return;
      }

      // For oneway commands, no request ID is required
      if (!oneway && !message.i) {
        message.i = '?';
// TODO warum !!!!
//        logger.error('Received scmd without request ID (i) for non-oneway command');
//        return;
      }

      logger.debug('Received server command', {
        requestId: message.i,
        cmd,
        args,
        oneway,
      });

      // Route to CommandService for execution
      this.commandService.handleServerCommand(message.i || '', cmd, args || [], oneway || false);
    } catch (error) {
      ExceptionHandler.handle(error, 'ServerCommandHandler.handle', { message });
    }
  }
}
