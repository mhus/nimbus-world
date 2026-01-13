/**
 * UnderwaterCommand - Toggle underwater camera mode
 */

import { CommandHandler } from './CommandHandler';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('UnderwaterCommand');
import type { AppContext } from '../AppContext';

/**
 * Underwater command - Toggle underwater camera effect
 *
 * Usage:
 *   underwater true   - Enable underwater mode
 *   underwater false  - Disable underwater mode
 *   underwater        - Toggle underwater mode
 */
export class UnderwaterCommand extends CommandHandler {
  private appContext: AppContext;
  private underwaterEnabled: boolean = false;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'underwater';
  }

  description(): string {
    return 'Toggle underwater camera mode (true/false or no parameter to toggle)';
  }

  execute(parameters: any[]): any {
    const playerService = this.appContext.services.player;

    if (!playerService) {
      logger.error('PlayerService not available');
      return { error: 'PlayerService not available' };
    }

    // Parse parameter
    let enabled: boolean;

    if (parameters.length === 0) {
      // No parameter: toggle current state
      enabled = !this.underwaterEnabled;
    } else {
      // Parse first parameter as boolean
      const param = parameters[0];

      if (typeof param === 'boolean') {
        enabled = param;
      } else if (typeof param === 'string') {
        const lower = param.toLowerCase();
        if (lower === 'true' || lower === '1' || lower === 'on' || lower === 'yes') {
          enabled = true;
        } else if (lower === 'false' || lower === '0' || lower === 'off' || lower === 'no') {
          enabled = false;
        } else {
          logger.error(`Invalid parameter: ${param}. Use true/false.`);
          return { error: 'Invalid parameter. Use true/false.' };
        }
      } else {
        logger.error('Invalid parameter type. Use true/false.');
        return { error: 'Invalid parameter type. Use true/false.' };
      }
    }

    // Update state
    this.underwaterEnabled = enabled;

    // Apply underwater mode via PlayerService
    playerService.setUnderwaterViewMode(enabled);

    const message = `Underwater camera mode ${enabled ? 'enabled' : 'disabled'}`;
    logger.debug(message);

    return {
      underwaterEnabled: enabled,
      message,
    };
  }
}
