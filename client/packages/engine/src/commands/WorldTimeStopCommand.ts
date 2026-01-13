/**
 * WorldTimeStopCommand - Stop World Time
 */

import { CommandHandler } from './CommandHandler';
import type { AppContext } from '../AppContext';
import { getLogger } from '@nimbus/shared';

const logger = getLogger('WorldTimeStopCommand');

/**
 * WorldTimeStop command - Stop World Time
 * Usage: worldTimeStop
 */
export class WorldTimeStopCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'worldTimeStop';
  }

  description(): string {
    return 'Stop World Time';
  }

  execute(parameters: any[]): any {
    const environmentService = this.appContext.services.environment;
    if (!environmentService) {
      logger.error('EnvironmentService not available');
      return { error: 'EnvironmentService not available' };
    }

    const wasRunning = environmentService.isWorldTimeRunning();
    const currentTime = wasRunning ? environmentService.getWorldTimeCurrentAsString() : 'N/A';

    // Stop world time
    environmentService.stopWorldTime();

    if (wasRunning) {
      logger.debug('=== World Time Stopped ===');
      logger.debug(`  Stopped at: ${currentTime}`);
      logger.debug('===========================');
    } else {
      logger.debug('World Time was not running');
    }

    return {
      success: true,
      wasRunning,
      stoppedAt: wasRunning ? currentTime : null,
    };
  }
}
