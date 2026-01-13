/**
 * WorldTimeStartCommand - Start World Time
 */

import { CommandHandler } from './CommandHandler';
import type { AppContext } from '../AppContext';
import { getLogger, toNumber } from '@nimbus/shared';

const logger = getLogger('WorldTimeStartCommand');

/**
 * WorldTimeStart command - Start World Time
 * Usage: worldTimeStart <worldMinute>
 */
export class WorldTimeStartCommand extends CommandHandler {
  private appContext: AppContext;

  constructor(appContext: AppContext) {
    super();
    this.appContext = appContext;
  }

  name(): string {
    return 'worldTimeStart';
  }

  description(): string {
    return 'Start World Time from specified world minute (@Minutes since @0.1.1.0000 00:00:00)';
  }

  execute(parameters: any[]): any {
    const environmentService = this.appContext.services.environment;
    if (!environmentService) {
      logger.error('EnvironmentService not available');
      return { error: 'EnvironmentService not available' };
    }

    // Parse parameter
    if (parameters.length !== 1) {
      logger.error('Usage: worldTimeStart <worldMinute>');
      return {
        error: 'Invalid parameters',
        usage: 'worldTimeStart <worldMinute>',
      };
    }

    const worldMinute = toNumber(parameters[0]);

    if (isNaN(worldMinute)) {
      logger.error('worldMinute must be a valid number');
      return { error: 'worldMinute must be a valid number' };
    }

    // Start world time
    environmentService.startWorldTime(worldMinute);

    const currentTime = environmentService.getWorldTimeCurrentAsString();
    const daySection = environmentService.getWorldDayTimeSection();

    logger.debug('=== World Time Started ===');
    logger.debug(`  Start Time      : ${currentTime}`);
    logger.debug(`  Day Section     : ${daySection}`);
    logger.debug(`  World Minute    : ${worldMinute.toFixed(2)}`);
    logger.debug('==========================');

    return {
      success: true,
      worldTime: currentTime,
      daySection,
      worldMinute,
    };
  }
}
