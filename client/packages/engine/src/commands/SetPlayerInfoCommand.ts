/**
 * SetPlayerInfoCommand - Set player info properties dynamically
 *
 * Usage: setPlayerInfo {"property": value, ...}
 * - Accepts JSON object with PlayerInfo properties to update
 * - Triggers 'playerInfo:updated' event automatically
 * - Useful for testing, debugging, and cheats
 *
 * Examples:
 * - setPlayerInfo {"effectiveWalkSpeed": 10}
 * - setPlayerInfo {"effectiveJumpSpeed": 15, "effectiveTurnSpeed": 0.01}
 * - setPlayerInfo {"eyeHeight": 2.5}
 */

import { CommandHandler } from './CommandHandler';
import type { AppContext } from '../AppContext';
import { getLogger, ExceptionHandler } from '@nimbus/shared';

const logger = getLogger('SetPlayerInfoCommand');

export class SetPlayerInfoCommand extends CommandHandler {
  constructor(private appContext: AppContext) {
    super();
  }

  name(): string {
    return 'setPlayerInfo';
  }

  description(): string {
    return 'Set player info properties (JSON object)';
  }

  async execute(parameters: any[]): Promise<string> {
    try {
      // Get PlayerService
      const playerService = this.appContext.services.player;
      if (!playerService) {
        return 'PlayerService not available';
      }

      // Show current PlayerInfo if no parameters
      if (parameters.length === 0) {
        const playerEntity = playerService.getPlayerEntity();
        const info = playerEntity.playerInfo;
        return `Current PlayerInfo:\n${JSON.stringify(info, null, 2)}`;
      }

      // Parse JSON
      let updates: any;
      if (parameters[0] == null) {
        return 'Error: Missing JSON parameter';
      }
      if (typeof parameters[0] === 'string') {
          try {
            updates = JSON.parse(parameters[0]);
          } catch (parseError) {
            if (parseError instanceof SyntaxError) {
              return `Invalid JSON: ${parseError.message}\nUsage: setPlayerInfo {"property": value}`;
            }
            throw parseError;
          }
      } else
      if (typeof parameters[0] === 'object') {
        updates = parameters[0];
      } else {
        return 'Error: Parameter must be a JSON object string or object';
      }

      // Validate that updates is an object
      if (typeof updates !== 'object' || updates === null || Array.isArray(updates)) {
        return 'Error: Parameter must be a JSON object, not array or primitive';
      }

      // Update PlayerInfo (this will emit 'playerInfo:updated' event)
      playerService.updatePlayerInfo(updates);

      logger.debug('PlayerInfo updated via command', { updates });

      return `PlayerInfo updated successfully:\n${JSON.stringify(updates, null, 2)}`;

    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : String(error);
      ExceptionHandler.handle(error, 'SetPlayerInfoCommand.execute', { parameters });
      return `Error updating PlayerInfo: ${errorMessage}`;
    }
  }
}
